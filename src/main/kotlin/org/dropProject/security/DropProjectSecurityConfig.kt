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

import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.logout.LogoutFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * Implementation of the AccessDeniedHandler that either calls the default access denied impl
 * (which forwards the request to an error page) or simply returns a 403 error code (in case of
 * an API request)
 */
class APIAccessDeniedHandler(private val errorPage: String) : AccessDeniedHandler {

    override fun handle(request: HttpServletRequest, response: HttpServletResponse, accessDeniedException: AccessDeniedException) {
        if (request.contentType == "application/json") {
            response.status = HttpStatus.FORBIDDEN.value()
            response.flushBuffer()  // to commit immediately the response
        } else {
            request.getRequestDispatcher(errorPage).forward(request, response)
        }
    }
}

/**
 * Definitions and configurations related with Security and Role Based Access Control.
 *
 */
open class DropProjectSecurityConfig(val apiAuthenticationManager: PersonalTokenAuthenticationManager? = null) {

    /**
     * Returns an array of ant matcher expressions which will be allowed without authentication
     */
    open fun getPublicUrls() = listOf("/upload/**/public/**", "/login", "/loginFromDEISI", "/access-denied.html", "/error", "/h2-console/**",
        "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs",
        "/css/**", "/js/**", "/img/**", "/favicon.ico")

    protected fun configure(http: HttpSecurity): HttpSecurity {
        http
            // disable csrf in case someone needs to access "/" by POST (e.g. Moodle lti)
            // and for all API calls
            .csrf { 
                it.ignoringRequestMatchers("/", "/api/**") 
            }
            .authorizeHttpRequests { authz ->
                authz
                    .requestMatchers(*getPublicUrls().toTypedArray()).permitAll()
                    .requestMatchers(
                        "/", "/upload", "/upload/**", "/buildReport/**", "/student/**",
                        "/git-submission/refresh-git/*", "/git-submission/generate-report/*", "/mySubmissions",
                        "/leaderboard/*",
                        "/personalToken", "/api/student/**"
                    )
                    .hasAnyRole("STUDENT", "TEACHER", "DROP_PROJECT_ADMIN")
                    .requestMatchers("/admin/**").hasRole("DROP_PROJECT_ADMIN")
                    .anyRequest().hasAnyRole("TEACHER", "DROP_PROJECT_ADMIN")
            }
            .exceptionHandling { 
                it.accessDeniedHandler(APIAccessDeniedHandler("/access-denied.html"))
            }

        if (apiAuthenticationManager != null) {
            http.addFilterBefore(PersonalTokenAuthenticationFilter("/api/**", apiAuthenticationManager),
                LogoutFilter::class.java)
        }

        http.headers { 
            it.frameOptions { frameOptions -> frameOptions.sameOrigin() }  // this is needed for h2-console
        }

        return http
    }
}