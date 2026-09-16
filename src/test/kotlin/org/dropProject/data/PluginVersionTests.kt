/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2025 Pedro Alves
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
package org.dropproject.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PluginVersionTests {

    @Test
    fun `parse a released version`() {
        assertEquals(PluginVersion(0, 9, 15), PluginVersion.parse("0.9.15"))
        assertEquals(PluginVersion(1, 0, 0), PluginVersion.parse("1.0.0"))
        assertEquals(PluginVersion(12, 345, 6789), PluginVersion.parse("12.345.6789"))
    }

    @Test
    fun `parse a version with only two components`() {
        assertEquals(PluginVersion(1, 0, 0), PluginVersion.parse("1.0"))
    }

    @Test
    fun `parse a beta version`() {
        assertEquals(PluginVersion(0, 9, 15, "beta"), PluginVersion.parse("0.9.15-beta"))
        assertEquals(PluginVersion(0, 9, 15, "beta.2"), PluginVersion.parse("0.9.15-beta.2"))
    }

    @Test
    fun `parse a version that is surrounded by spaces`() {
        assertEquals(PluginVersion(0, 9, 15), PluginVersion.parse("  0.9.15  "))
    }

    @Test
    fun `refuse to parse what is not a version`() {
        assertNull(PluginVersion.parse(null))
        assertNull(PluginVersion.parse(""))
        assertNull(PluginVersion.parse("   "))
        assertNull(PluginVersion.parse("0"))
        assertNull(PluginVersion.parse("0.9.15.1"))
        assertNull(PluginVersion.parse("v0.9.15"))
        assertNull(PluginVersion.parse("0.9.x"))
        assertNull(PluginVersion.parse("latest"))
        // a number that would not fit in an Int is refused instead of overflowing into another version
        assertNull(PluginVersion.parse("0.9.99999999999999"))
    }

    @Test
    fun `order versions by each component`() {
        assertTrue(PluginVersion.parse("0.9.14")!! < PluginVersion.parse("0.9.15")!!)
        assertTrue(PluginVersion.parse("0.9.15")!! < PluginVersion.parse("0.10.0")!!)
        assertTrue(PluginVersion.parse("0.10.0")!! < PluginVersion.parse("1.0.0")!!)
        assertTrue(PluginVersion.parse("0.9.15")!! >= PluginVersion.parse("0.9.15")!!)
        // 9 is not compared as the text "9", which would place it after "10"
        assertTrue(PluginVersion.parse("0.9.9")!! < PluginVersion.parse("0.9.10")!!)
    }

    @Test
    fun `a beta comes before the version it leads to`() {
        assertTrue(PluginVersion.parse("0.9.15-beta")!! < PluginVersion.parse("0.9.15")!!)
        assertTrue(PluginVersion.parse("0.9.14")!! < PluginVersion.parse("0.9.15-beta")!!)
        assertTrue(PluginVersion.parse("0.9.15-beta")!! < PluginVersion.parse("0.9.15-beta.2")!!)
    }

    @Test
    fun `print a version the way it was written`() {
        assertEquals("0.9.15", PluginVersion.parse("0.9.15").toString())
        assertEquals("0.9.15-beta", PluginVersion.parse("0.9.15-beta").toString())
        // the patch that was left out is the 0 that it means
        assertEquals("1.0.0", PluginVersion.parse("1.0").toString())
    }
}
