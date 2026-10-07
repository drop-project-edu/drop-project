/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2026 Pedro Alves
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AllowedIpsTests {

    @Test
    fun `wildcard prefixes match whole octets`() {
        val allowedIps = AllowedIps.parse("10.12.33.*, 172.18.*")

        assertTrue(allowedIps.allows("10.12.33.105"))
        assertTrue(allowedIps.allows("172.18.101.89"))

        // 10.12.33.* is not a textual prefix: 10.12.3 doesn't match 10.12.33
        assertFalse(allowedIps.allows("10.12.3.105"))
        assertFalse(AllowedIps.parse("10.1.*").allows("10.12.33.105"))

        assertFalse(allowedIps.allows("87.196.72.232"))
    }

    @Test
    fun `a complete address only matches itself`() {
        val allowedIps = AllowedIps.parse("193.136.10.5")

        assertTrue(allowedIps.allows("193.136.10.5"))
        assertFalse(allowedIps.allows("193.136.10.50"))
    }

    @Test
    fun `addresses that are not ipv4 never match`() {
        val allowedIps = AllowedIps.parse("10.*")

        assertFalse(allowedIps.allows("0:0:0:0:0:0:0:1"))
        assertFalse(allowedIps.allows(""))
        assertFalse(allowedIps.allows(null))
    }

    @Test
    fun `the list is normalized`() {
        assertEquals("10.12.33.*, 172.18.*, 193.136.10.5", AllowedIps.parse(" 10.12.033.* ,172.18.*,, 193.136.10.5 ").toString())
        assertTrue(AllowedIps.parse("010.12.*").allows("10.12.0.1"))
    }

    @Test
    fun `invalid entries are rejected naming the entry`() {
        for (invalid in listOf("10.12.33", "10.12.33.105.*", "*", "10.*.33.*", "256.*", "10.12.33.x", "10..33.*")) {
            val exception = assertThrows<IllegalArgumentException>(invalid) { AllowedIps.parse("10.*, $invalid") }
            assertTrue(exception.message!!.contains("'$invalid'"), exception.message)
        }

        assertThrows<IllegalArgumentException> { AllowedIps.parse(" , ") }
    }
}
