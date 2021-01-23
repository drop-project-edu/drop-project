package org.dropProject.data

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class TestMavenResult {
    @Test
    fun testMavenResult() {
        val outputLines = mutableListOf<String>()
        var mvnResult = MavenResult(100, outputLines, true)
        assertTrue(mvnResult.expiredByTimeout)
        assertEquals(100, mvnResult.resultCode)
        
        var mvnResult2 = MavenResult(200, outputLines, false)
        assertFalse(mvnResult2.expiredByTimeout)
        assertEquals(200, mvnResult2.resultCode)
    }
}