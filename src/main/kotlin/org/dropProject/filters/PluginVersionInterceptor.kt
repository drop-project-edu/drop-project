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
package org.dropproject.filters

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.dropproject.config.DropProjectProperties
import org.dropproject.data.ApiClient
import org.dropproject.data.PluginVersion
import org.dropproject.security.writeApiError
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.util.Locale

/**
 * Refuses the student API to the versions of the IntelliJ plugin that this server no longer supports, so that
 * the student is told to update the plugin instead of watching it fail in ways they cannot explain.
 *
 * The check is off unless drop-project.plugin.minimum-version is configured, and it only ever looks at the
 * plugin: every other client of the API goes through untouched (see [ApiClient]).
 *
 * It runs on every student API call rather than only on the calls that need a recent plugin, so that an
 * outdated plugin is told so when the student logs in, and not only when they try to submit.
 *
 * The answer carries the version it demands, as a "minimumVersion" field, so that a plugin that knows about
 * this error writes its own message, in the language of the IDE and offering whatever it can to fix it. The
 * sentence that comes with the answer is for the clients that can only print what they are given: an outdated
 * plugin, which was written before any of this existed, and whatever a student or a teacher writes against
 * the API themselves.
 */
@Component
class PluginVersionInterceptor(
    val properties: DropProjectProperties,
    val i18n: MessageSource
) : HandlerInterceptor {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    @Value("\${spring.web.locale}")
    val currentLocale: Locale = Locale.getDefault()

    /** null when the check is disabled, which is the case whenever no minimum version is configured */
    private val minimumVersion: PluginVersion? by lazy { PluginVersion.parse(properties.plugin.minimumVersion) }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {

        val minimum = minimumVersion ?: return true

        val client = ApiClient.of(request)
        if (client.meets(minimum)) {
            return true
        }

        when (client) {

            is ApiClient.Plugin -> {
                LOG.warn("Refused ${request.requestURI} to plugin ${client.version} (minimum is $minimum)")
                // a plugin that reports its version is recent enough to know this status
                reject(response, HttpStatus.UPGRADE_REQUIRED, minimum,
                    i18n.getMessage("error.plugin.outdated.version",
                        arrayOf(client.version.toString(), minimum.toString()), currentLocale))
            }

            ApiClient.UnidentifiedPlugin -> {
                LOG.warn("Refused ${request.requestURI} to a plugin too old to report its version " +
                        "(minimum is $minimum)")
                // an outdated plugin only shows the message of a 401: on any other status it fails silently,
                // leaving the student with a login that does not work and no hint that an update is needed.
                // 426, which is what this case deserves, is therefore kept for the plugins that can report a
                // version, all of which are recent enough to display it.
                reject(response, HttpStatus.UNAUTHORIZED, minimum,
                    i18n.getMessage("error.plugin.outdated", arrayOf(minimum.toString()), currentLocale))
            }

            // a client that is not the plugin is never refused, so it never reaches this point
            ApiClient.Other -> return true
        }

        return false
    }

    private fun reject(response: HttpServletResponse, status: HttpStatus, minimum: PluginVersion,
                       message: String) {
        response.writeApiError(status.value(), "Plugin version not supported", message,
            mapOf("minimumVersion" to minimum.toString()))
    }
}
