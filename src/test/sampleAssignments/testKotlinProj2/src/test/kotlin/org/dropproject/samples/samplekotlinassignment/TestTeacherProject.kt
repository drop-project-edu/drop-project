/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2024 Pedro Alves
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
