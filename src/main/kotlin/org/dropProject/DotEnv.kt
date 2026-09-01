/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 Pedro Alves
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
package org.dropproject

import java.io.File

/**
 * Resolves configuration values that cannot be committed to the repository, such as the ssh keys
 * for the sample github repositories: first from an environment variable, then from the git-ignored
 * .env file at the project root (see .env.example).
 */
object DotEnv {

    fun resolve(variable: String): String? {
        System.getenv(variable)?.takeIf { it.isNotBlank() }?.let { return it }
        return dotEnvEntries[variable]?.takeIf { it.isNotBlank() }
    }

    private val dotEnvEntries: Map<String, String> by lazy { parse(File(".env")) }

    /**
     * Minimal .env parser: NAME=value entries, blank lines and # comments ignored. A value
     * that starts with a double quote extends until the line that ends with a double quote,
     * which is how multi-line values such as private keys are stored (see .env.example).
     */
    private fun parse(file: File): Map<String, String> {
        if (!file.exists()) {
            return emptyMap()
        }

        val entries = mutableMapOf<String, String>()
        val lines = file.readLines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            i++
            if (line.isEmpty() || line.startsWith("#") || !line.contains('=')) {
                continue
            }
            val name = line.substringBefore('=').trim()
            var value = line.substringAfter('=').trim()
            if (value.startsWith("\"")) {
                value = value.substring(1)
                while (!value.endsWith("\"") && i < lines.size) {
                    value += "\n" + lines[i]
                    i++
                }
                value = value.removeSuffix("\"")
            }
            entries[name] = value
        }
        return entries
    }
}