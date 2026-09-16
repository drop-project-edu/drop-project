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

/**
 * A version of the IntelliJ plugin, as it is published to the JetBrains marketplace: three numbers and,
 * for the releases that go to the beta channel, a suffix (e.g. "0.9.15" or "0.9.15-beta").
 *
 * Versions are ordered the way semantic versioning defines it: numerically, component by component, and a
 * version that carries a suffix comes *before* the same version without one, so 0.9.15-beta is older than
 * 0.9.15. A minimum version of "0.9.15" therefore rejects its betas; to accept them, require "0.9.15-beta".
 *
 * @property major is the first component
 * @property minor is the second component
 * @property patch is the third component, which is 0 when the version has only two
 * @property preRelease is the suffix after the dash, or null for a regular release
 */
data class PluginVersion(
    val major: Int,
    val minor: Int,
    val patch: Int = 0,
    val preRelease: String? = null
) : Comparable<PluginVersion> {

    companion object {

        private val FORMAT = Regex("""(\d{1,9})\.(\d{1,9})(?:\.(\d{1,9}))?(?:-(.+))?""")

        /**
         * Parses [version], returning null when it is blank or is not written in a format that can be
         * compared with another version. Callers decide what an unparseable version means to them - it is
         * not necessarily an error, since the version is reported by the client and cannot be trusted.
         */
        fun parse(version: String?): PluginVersion? {
            val match = FORMAT.matchEntire(version.orEmpty().trim()) ?: return null
            val (major, minor, patch, preRelease) = match.destructured
            return PluginVersion(
                major = major.toInt(),
                minor = minor.toInt(),
                patch = if (patch.isEmpty()) 0 else patch.toInt(),
                preRelease = preRelease.ifEmpty { null }
            )
        }
    }

    override fun compareTo(other: PluginVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        return when {
            preRelease == other.preRelease -> 0
            // a beta (0.9.15-beta) is published before the release it leads to (0.9.15)
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> preRelease.compareTo(other.preRelease)
        }
    }

    override fun toString() = "$major.$minor.$patch" + (preRelease?.let { "-$it" } ?: "")
}
