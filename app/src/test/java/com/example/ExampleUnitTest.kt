package com.example

import com.example.model.ClockFace
import com.example.ui.clockfaces.get5x7CharMatrix
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testClockFaceEnumValues() {
        val faces = ClockFace.values()
        assertTrue(faces.contains(ClockFace.DIGITAL_STATION_BLUE))
        assertTrue(faces.contains(ClockFace.DIGITAL_STATION_MATRIX))
        assertTrue(faces.contains(ClockFace.RETRO_LCD_GOLD))
        assertEquals(12, faces.size)
    }

    @Test
    fun test5x7MatrixGeneration() {
        val matrixT = get5x7CharMatrix('T')
        assertEquals(7, matrixT.size)
        assertEquals(5, matrixT[0].size)
        // 'T' top row should be all true
        assertTrue(matrixT[0].all { it })
        // 'T' second row should only have center lit
        assertTrue(matrixT[1][2])
        assertFalse(matrixT[1][0])

        val matrix4 = get5x7CharMatrix('4')
        assertEquals(7, matrix4.size)
        assertEquals(5, matrix4[0].size)
    }
}
