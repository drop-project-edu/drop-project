package org.dropproject.samples.samplekotlinassignment

import org.junit.Assert.assertEquals
import org.junit.Test

// in Drop Project, all test classes must begin with "Test"
class TestTeacherProject {

    @Test
    fun testFindMax() {
        assertEquals(7, findMax(arrayOf(1, 2, 7, 4)))
    }

    @Test
    fun testFindMaxAllNegative() {
        assertEquals(-1, findMax(arrayOf(-7, -5, -3, -1)))
        assertEquals(-3, findMax(arrayOf(-7, -5, -3, -99)))
    }

    @Test
    fun testFindMaxNegativeAndPositive() {
        assertEquals(3, findMax(arrayOf(-7, -5, 3, -1)))
        assertEquals(5, findMax(arrayOf(-7, 5, -3, -99)))
    }

}