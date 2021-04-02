/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2021 Pedro Alves
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
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

        mvnResult2.expiredByTimeout = true
        assertTrue(mvnResult2.expiredByTimeout)
    }
}
