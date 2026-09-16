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

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders

/**
 * Who is calling the API, as far as the User-Agent header says.
 *
 * The API is used both by the IntelliJ plugin and by whatever a teacher or a student writes against it (the
 * documentation of /api/student/submissions/new has a curl example), and Drop Project only makes demands of
 * the plugin: a feature that needs the plugin to send a submission in a new shape can require a version that
 * knows how to do it, while a script, which is written against the API and not against the plugin, is left
 * alone. Distinguishing them is what this class is for.
 */
sealed class ApiClient {

    /** The plugin, from [PLUGIN_PRODUCT_TOKEN] onwards, which reports its own version. */
    data class Plugin(val version: PluginVersion) : ApiClient()

    /**
     * A plugin that cannot say which version it is, and is therefore older than any version that can.
     *
     * Up to 0.9.14 the plugin never set a User-Agent, so OkHttp, the http library it is built on, filled in
     * its own ("okhttp/4.12.0"). Any other OkHttp-based client would be indistinguishable from it, but the
     * clients that call the student API in practice are the plugins, so an OkHttp user agent with no version
     * of ours in it is taken to be an outdated plugin and told to update.
     */
    data object UnidentifiedPlugin : ApiClient()

    /** Anything else: curl, a script, a browser, ... Drop Project makes no version demands of those. */
    data object Other : ApiClient()

    companion object {

        /**
         * The product token that the plugin puts at the front of its User-Agent, from the first version that
         * identifies itself: "DropProjectPlugin/0.9.15 (IntelliJ IDEA 2024.3)".
         */
        const val PLUGIN_PRODUCT_TOKEN = "DropProjectPlugin"

        private val PLUGIN_USER_AGENT = Regex("""^$PLUGIN_PRODUCT_TOKEN/(\S+)""", RegexOption.IGNORE_CASE)

        private const val OKHTTP_USER_AGENT_PREFIX = "okhttp/"

        /**
         * Identifies the client that made [request].
         */
        fun of(request: HttpServletRequest) = fromUserAgent(request.getHeader(HttpHeaders.USER_AGENT))

        /**
         * Identifies a client from its [userAgent] header, which may be absent.
         *
         * A user agent of ours whose version cannot be parsed is reported as an [UnidentifiedPlugin], and
         * therefore treated as outdated: the server cannot tell whether such a client is recent enough, and
         * a plugin that reports a version it did not write is not one to trust with the benefit of the doubt.
         */
        fun fromUserAgent(userAgent: String?): ApiClient {
            val header = userAgent.orEmpty().trim()
            val pluginMatch = PLUGIN_USER_AGENT.find(header)
            return when {
                pluginMatch != null ->
                    PluginVersion.parse(pluginMatch.groupValues[1])?.let { Plugin(it) } ?: UnidentifiedPlugin
                header.startsWith(OKHTTP_USER_AGENT_PREFIX, ignoreCase = true) -> UnidentifiedPlugin
                else -> Other
            }
        }
    }
}
