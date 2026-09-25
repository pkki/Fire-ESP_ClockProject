package com.example.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.core.content.ContextCompat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class CameraStreamManager(
    private val context: Context,
    private val server: IpCameraServer
) {
    companion object {
        private const val TAG = "CameraStreamManager"
    }

    private var cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val isStreaming = AtomicBoolean(false)

    private val cameraLock = Any()
    private var retryCount = 0
    private val maxRetries = 4
    private var isPaused = false

    // Current camera parameters
    private var useFrontCamera = true
    private var targetFps = 10
    private var targetWidth = 640
    private var targetHeight = 480
    private var jpegQuality = 75
    private var sensorOrientation = 0
    private var lastOnErrorCallback: ((String) -> Unit)? = null

    // Latest Bitmap for in-app mini preview
    private val _latestPreviewBitmap = MutableStateFlow<Bitmap?>(null)
    val latestPreviewBitmap: StateFlow<Bitmap?> = _latestPreviewBitmap.asStateFlow()

    // FPS tracking
    private var frameCount = 0
    private var lastFpsCalculationTime = 0L
    private val _currentFps = MutableStateFlow(0f)
    val currentFps: StateFlow<Float> = _currentFps.asStateFlow()

    private var lastFrameTime = 0L

    fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraBackground").apply { start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    fun pauseCamera() {
        synchronized(cameraLock) {
            if (isStreaming.get()) {
                isPaused = true
                stopCameraInternal()
            }
        }
    }

    fun resumeCamera(onError: (String) -> Unit = {}) {
        synchronized(cameraLock) {
            if (isPaused) {
                isPaused = false
                startCamera(useFrontCamera, targetFps, targetWidth, targetHeight, jpegQuality, onError)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startCamera(
        useFront: Boolean = true,
        fps: Int = 10,
        width: Int = 640,
        height: Int = 480,
        quality: Int = 75,
        onError: (String) -> Unit = {}
    ) {
        lastOnErrorCallback = onError
        this.useFrontCamera = useFront
        this.targetFps = fps
        this.targetWidth = width
        this.targetHeight = height
        this.jpegQuality = quality

        // Check camera permission explicitly
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onError("カメラ権限が許可されていません。設定画面で許可してください。")
            return
        }

        startBackgroundThread()

        synchronized(cameraLock) {
            stopCameraInternal()
        }

        // Run camera opening on background handler with delay to let previous HAL close cleanly
        backgroundHandler?.postDelayed({
            openCameraInternal(onError)
        }, 150L)
    }

    @SuppressLint("MissingPermission")
    private fun openCameraInternal(onError: (String) -> Unit) {
        val cameraId = findCameraId(useFrontCamera)
        if (cameraId == null) {
            onError(if (useFrontCamera) "内カメラ（フロント）が見つかりません" else "外カメラ（リア）が見つかりません")
            return
        }

        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            // Choose appropriate resolution
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888) ?: arrayOf(Size(targetWidth, targetHeight))
            val chosenSize = chooseOptimalSize(sizes, targetWidth, targetHeight)

            synchronized(cameraLock) {
                imageReader = ImageReader.newInstance(
                    chosenSize.width,
                    chosenSize.height,
                    ImageFormat.YUV_420_888,
                    2
                ).apply {
                    setOnImageAvailableListener({ reader ->
                        handleIncomingFrame(reader)
                    }, backgroundHandler)
                }
            }

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    synchronized(cameraLock) {
                        cameraDevice = camera
                        isStreaming.set(true)
                        retryCount = 0
                    }
                    createCaptureSession(camera, onError)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera disconnected")
                    synchronized(cameraLock) {
                        try { camera.close() } catch (_: Exception) {}
                        cameraDevice = null
                        isStreaming.set(false)
                    }
                    // Auto-reconnect if unexpectedly disconnected
                    scheduleRetry(onError)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error occurred: code $error")
                    synchronized(cameraLock) {
                        try { camera.close() } catch (_: Exception) {}
                        cameraDevice = null
                        isStreaming.set(false)
                    }
                    val errorMsg = when (error) {
                        CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> "カメラ使用中 (自動再試行中...)"
                        CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "カメラ上限超過 (自動再試行中...)"
                        CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "カメラデバイス無効化"
                        CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> "カメラ一時障害 (自動復旧試行中...)"
                        CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> "カメラサービス障害 (自動復旧試行中...)"
                        else -> "カメラエラー (code $error)"
                    }
                    onError(errorMsg)
                    scheduleRetry(onError)
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
            onError("カメラの起動に失敗しました: ${e.localizedMessage}")
            scheduleRetry(onError)
        }
    }

    private fun scheduleRetry(onError: (String) -> Unit) {
        if (retryCount >= maxRetries) {
            Log.w(TAG, "Max camera retries reached.")
            onError("カメラに接続できませんでした。設定画面からカメラを再起動してください。")
            retryCount = 0
            return
        }
        retryCount++
        val delay = 600L * retryCount
        Log.d(TAG, "Scheduling camera retry #$retryCount in ${delay}ms...")
        backgroundHandler?.postDelayed({
            openCameraInternal(onError)
        }, delay)
    }

    private fun createCaptureSession(camera: CameraDevice, onError: (String) -> Unit) {
        try {
            val readerSurface = imageReader?.surface ?: return
            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(readerSurface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }

            @Suppress("DEPRECATION")
            camera.createCaptureSession(
                listOf(readerSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to set repeating request", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onError("キャプチャセッションの構成に失敗しました")
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating capture session", e)
            onError("セッション初期化失敗: ${e.localizedMessage}")
        }
    }

    private fun handleIncomingFrame(reader: ImageReader) {
        var image: Image? = null
        try {
            image = reader.acquireLatestImage() ?: return

            val now = System.currentTimeMillis()
            val minFrameIntervalMs = 1000L / targetFps.coerceAtLeast(1)
            if (now - lastFrameTime < minFrameIntervalMs) {
                // Throttle frame rate for CPU & battery efficiency
                return
            }
            lastFrameTime = now

            val jpegBytes = yuv420ToJpeg(image, sensorOrientation, useFrontCamera, jpegQuality)
            if (jpegBytes != null) {
                // Push to MJPEG HTTP server
                server.pushFrame(jpegBytes)

                // Track FPS
                frameCount++
                if (now - lastFpsCalculationTime >= 1000L) {
                    _currentFps.value = (frameCount * 1000f) / (now - lastFpsCalculationTime)
                    frameCount = 0
                    lastFpsCalculationTime = now
                }

                // Decode lightweight bitmap for in-app mini preview occasionally
                if (frameCount % 2 == 0) {
                    val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                    _latestPreviewBitmap.value = bmp
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing camera frame", e)
        } finally {
            image?.close()
        }
    }

    fun stopCamera() {
        synchronized(cameraLock) {
            stopCameraInternal()
        }
    }

    private fun stopCameraInternal() {
        isStreaming.set(false)
        try {
            imageReader?.setOnImageAvailableListener(null, null)
        } catch (_: Exception) {}

        try {
            captureSession?.stopRepeating()
            captureSession?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing capture session", e)
        }
        captureSession = null

        try {
            cameraDevice?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera device", e)
        }
        cameraDevice = null

        try {
            imageReader?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing image reader", e)
        }
        imageReader = null

        _latestPreviewBitmap.value = null
        _currentFps.value = 0f
    }

    private fun findCameraId(useFront: Boolean): String? {
        try {
            val list = cameraManager.cameraIdList
            for (id in list) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (useFront && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    return id
                } else if (!useFront && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id
                }
            }
            // Fallback to first available camera if preferred lens not found
            return list.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enumerate cameras", e)
            return null
        }
    }

    private fun chooseOptimalSize(choices: Array<Size>, reqWidth: Int, reqHeight: Int): Size {
        return choices.filter { it.width <= 1280 && it.height <= 720 }
            .minByOrNull { kotlin.math.abs(it.width - reqWidth) + kotlin.math.abs(it.height - reqHeight) }
            ?: choices.firstOrNull() ?: Size(reqWidth, reqHeight)
    }

    private fun yuv420ToJpeg(image: Image, sensorOrientation: Int, isFront: Boolean, quality: Int): ByteArray? {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)

        val uPixelStride = image.planes[1].pixelStride
        val vPixelStride = image.planes[2].pixelStride

        if (uPixelStride == 1 && vPixelStride == 1) {
            // YUV420P
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
        } else {
            // Interleaved NV21
            val rowStride = image.planes[1].rowStride
            val height = image.height
            val width = image.width
            var pos = ySize

            for (row in 0 until height / 2) {
                for (col in 0 until width / 2) {
                    val vIndex = row * rowStride + col * vPixelStride
                    val uIndex = row * rowStride + col * uPixelStride
                    if (vIndex < vBuffer.capacity() && uIndex < uBuffer.capacity()) {
                        nv21[pos++] = vBuffer.get(vIndex)
                        nv21[pos++] = uBuffer.get(uIndex)
                    }
                }
            }
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), quality, out)
        var jpegBytes = out.toByteArray()

        // Handle rotation for front/back camera in landscape tablet
        if (sensorOrientation != 0) {
            try {
                val origBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                if (origBitmap != null) {
                    val matrix = Matrix()
                    matrix.postRotate(sensorOrientation.toFloat())
                    if (isFront) {
                        matrix.postScale(-1f, 1f) // Mirror front camera
                    }
                    val rotated = Bitmap.createBitmap(
                        origBitmap,
                        0,
                        0,
                        origBitmap.width,
                        origBitmap.height,
                        matrix,
                        true
                    )
                    val rotOut = ByteArrayOutputStream()
                    rotated.compress(Bitmap.CompressFormat.JPEG, quality, rotOut)
                    jpegBytes = rotOut.toByteArray()
                    origBitmap.recycle()
                    rotated.recycle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Rotation error", e)
            }
        }

        return jpegBytes
    }
}
