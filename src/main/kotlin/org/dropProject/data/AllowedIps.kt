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

/**
 * The IPv4 addresses that students may use an assignment from, written as a comma separated list of addresses and
 * wildcard prefixes, e.g. "10.12.33.*, 172.18.*, 193.136.10.5".
 *
 * A wildcard prefix is one to three octets followed by `.*` and matches every address that starts with those octets.
 * A complete address only matches itself. IPv6 addresses never match.
 */
class AllowedIps private constructor(
    // the octets of each entry: four for a complete address, fewer for a wildcard prefix
    private val prefixes: List<List<String>>
) {

    fun allows(clientIp: String?): Boolean {
        val octets = clientIp?.let { parseOctets(it) }?.takeIf { it.size == 4 } ?: return false
        return prefixes.any { it == octets.take(it.size) }
    }

    /**
     * The normalized form of the list, e.g. "10.12.33.*, 172.18.*" for " 10.12.033.* ,172.18.*"
     */
    override fun toString() = prefixes.joinToString(", ") { it.joinToString(".") + if (it.size == 4) "" else ".*" }

    companion object {

        /**
         * @throws IllegalArgumentException naming the first entry of [text] that is neither an IPv4 address nor a
         * wildcard prefix
         */
        fun parse(text: String): AllowedIps {
            val entries = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            require(entries.isNotEmpty()) { "Allowed IPs must have at least one address or prefix" }

            return AllowedIps(entries.map { entry ->
                val wildcard = entry.endsWith(".*")
                val octets = parseOctets(entry.removeSuffix(".*"))
                require(octets != null && if (wildcard) octets.size in 1..3 else octets.size == 4) {
                    "'$entry' is not an IPv4 address (e.g. 10.12.33.105) nor a prefix ending in .* (e.g. 10.12.33.*)"
                }
                octets
            })
        }

        // the octets of a dotted IPv4 address (or of the start of one), normalized, or null if they aren't valid
        private fun parseOctets(text: String): List<String>? {
            val octets = text.split(".")
            if (octets.size > 4 || octets.any { it.isEmpty() || it.length > 3 || !it.all(Char::isDigit) }) {
                return null
            }
            val numbers = octets.map { it.toInt() }
            return if (numbers.all { it in 0..255 }) numbers.map { it.toString() } else null
        }
    }
}
