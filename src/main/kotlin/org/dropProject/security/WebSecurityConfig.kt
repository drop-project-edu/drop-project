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
package org.dropproject.security

import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.access.AccessDeniedHandlerImpl

/**
 * Definitions and configurations related with Security and Role Based Access Control of the web interface.
 *
 * The REST API is not configured here - it has its own chain, which is always authenticated with personal tokens,
 * regardless of the way the users log in the web interface (see [org.dropproject.config.ApiSecurityConfig]).
 */
open class WebSecurityConfig {

    /**
     * Returns an array of ant matcher expressions which will be allowed without authentication
     */
    open fun getPublicUrls() = listOf("/upload/**/public/**", "/login", "/loginFromDEISI", "/access-denied.html", "/error", "/h2-console/**",
        "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs",
        "/css/**", "/js/**", "/img/**", "/favicon.ico", "/vendor/*")

    protected fun configure(http: HttpSecurity): HttpSecurity {
        http
            // disable csrf in case someone needs to access "/" by POST (e.g. Moodle lti)
            .csrf {
                it.ignoringRequestMatchers("/")
            }
            .authorizeHttpRequests { authz ->
                authz
                    .requestMatchers(*getPublicUrls().toTypedArray()).permitAll()
                    .requestMatchers(
                        "/", "/upload", "/upload/**", "/buildReport/**", "/student/**",
                        "/git-submission/refresh-git/*", "/git-submission/generate-report/*", "/mySubmissions",
                        "/leaderboard/*",
                        "/personalToken"
                    )
                    .hasAnyRole("STUDENT", "TEACHER", "DROP_PROJECT_ADMIN")
                    .requestMatchers("/admin/**").hasRole("DROP_PROJECT_ADMIN")
                    .anyRequest().hasAnyRole("TEACHER", "DROP_PROJECT_ADMIN")
            }
            .exceptionHandling {
                it.accessDeniedHandler(AccessDeniedHandlerImpl().apply { setErrorPage("/access-denied.html") })
            }

        http.headers {
            it.frameOptions { frameOptions -> frameOptions.sameOrigin() }  // this is needed for h2-console
        }

        return http
    }
}