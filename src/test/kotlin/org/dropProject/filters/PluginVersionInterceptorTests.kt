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

import org.dropproject.DropProjectIntegrationTest
import org.dropproject.basicAuthHeader
import org.dropproject.controllers.ApiTestSupport
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The minimum version that these tests run against is the one in drop-project-test.properties (0.9.15).
 */
@DropProjectIntegrationTest
@Tag("integration")
class PluginVersionInterceptorTests : ApiTestSupport {

    @Autowired
    lateinit var mvc: MockMvc

    private lateinit var token: String

    @BeforeEach
    fun setup() {
        token = generateToken("student1", listOf(SimpleGrantedAuthority("ROLE_STUDENT")), mvc)
    }

    private fun callApiAs(userAgent: String?) =
        mvc.perform(
            get("/api/student/assignments/current")
                .header("authorization", basicAuthHeader("student1", token))
                .apply { if (userAgent != null) header(HttpHeaders.USER_AGENT, userAgent) })

    @Test
    fun `a plugin that is recent enough is served`() {
        callApiAs("DropProjectPlugin/0.9.15 (IntelliJ IDEA 2024.3)").andExpect(status().isOk)
        callApiAs("DropProjectPlugin/0.9.16 (IntelliJ IDEA 2024.3)").andExpect(status().isOk)
        callApiAs("DropProjectPlugin/1.0.0 (IntelliJ IDEA 2024.3)").andExpect(status().isOk)
    }

    @Test
    fun `an outdated plugin is refused with an upgrade required`() {
        callApiAs("DropProjectPlugin/0.9.14 (IntelliJ IDEA 2024.3)")
            .andExpect(status().isUpgradeRequired)
            .andExpect(jsonPath("$.error").value("Plugin version not supported"))
            // the version it has to update to is reported as data, so that the plugin writes its own message
            .andExpect(jsonPath("$.minimumVersion").value("0.9.15"))
            .andExpect(jsonPath("$.message", containsString("0.9.14")))
            .andExpect(jsonPath("$.message", containsString("0.9.15")))
    }

    @Test
    fun `a beta is older than the version it leads to`() {
        callApiAs("DropProjectPlugin/0.9.15-beta (IntelliJ IDEA 2024.3)")
            .andExpect(status().isUpgradeRequired)
    }

    @Test
    fun `a plugin too old to report its version is refused with the status it knows how to report`() {
        // up to 0.9.14 the plugin set no user agent, so okhttp filled in its own. Those versions only show
        // the message of a 401, so that is what they get, even though 426 is what this case deserves
        callApiAs("okhttp/4.12.0")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Plugin version not supported"))
            .andExpect(jsonPath("$.minimumVersion").value("0.9.15"))
            .andExpect(jsonPath("$.message", containsString("too old")))
            .andExpect(jsonPath("$.message", containsString("0.9.15")))
    }

    @Test
    fun `clients that are not the plugin are served whatever the minimum version is`() {
        callApiAs(null).andExpect(status().isOk)
        callApiAs("curl/8.7.1").andExpect(status().isOk)
        callApiAs("PostmanRuntime/7.39.0").andExpect(status().isOk)
    }

    @Test
    fun `the version is only checked after the credentials`() {
        // a student whose token is wrong is told about the token, which is the problem they can act on
        mvc.perform(
            get("/api/student/assignments/current")
                .header("authorization", basicAuthHeader("student1", "invalid"))
                .header(HttpHeaders.USER_AGENT, "okhttp/4.12.0"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Token Authentication failed"))
    }
}
