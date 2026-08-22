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
package org.dropproject.config

import org.dropproject.security.PersonalTokenAuthenticationFilter
import org.dropproject.security.PersonalTokenAuthenticationManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.logout.LogoutFilter

/**
 * Security chain for the REST API.
 *
 * Unlike the web interface, whose authentication depends on the deployment (basic login, oauth2, lti, ...), the API
 * is always authenticated with a personal token that is managed by Drop Project itself
 * (see [PersonalTokenAuthenticationFilter]). Therefore, it has its own chain, defined before all the others, so that
 * the way the users log in the web interface never interferes with the API.
 *
 * Authentication failures are reported by [org.dropproject.security.PersonalTokenAuthenticationFailureHandler]
 * (401 with a json body). Authorization failures that are detected by this chain are reported the same way (403 with a
 * json body), whereas the ones that are thrown by the controllers (e.g. by
 * [org.dropproject.security.RequiresAssignmentOwnerOrACL]) keep being handled by
 * [org.dropproject.controllers.GlobalExceptionHandler].
 */
@Configuration
@Order(0)
class ApiSecurityConfig(val apiAuthenticationManager: PersonalTokenAuthenticationManager) {

    @Bean
    fun apiFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher("/api/**")
            .authorizeHttpRequests { authz ->
                authz
                    .requestMatchers("/api/student/**").hasAnyRole("STUDENT", "TEACHER", "DROP_PROJECT_ADMIN")
                    .requestMatchers("/api/teacher/**").hasAnyRole("TEACHER", "DROP_PROJECT_ADMIN")
                    // any endpoint that is added to the API without an explicit rule is admin only, on purpose
                    .anyRequest().hasRole("DROP_PROJECT_ADMIN")
            }
            .addFilterBefore(
                PersonalTokenAuthenticationFilter(apiAuthenticationManager),
                LogoutFilter::class.java
            )
            // the personal token is sent on every request, so there is no need to keep a session
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // the API is not authenticated with a cookie, so it is not vulnerable to csrf
            .csrf { it.disable() }
            .exceptionHandling { exceptions ->
                // these are not requests made by a browser, so there is no error page to forward to
                exceptions.accessDeniedHandler { _, response, exception ->
                    response.status = HttpStatus.FORBIDDEN.value()
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.writer.write(
                        """{"error": "Access denied", "message": "${exception.message}"}"""
                    )
                }
                // in practice, PersonalTokenAuthenticationFilter rejects the unauthenticated requests before they
                // reach this point, but the default entry point would answer them with an empty 403, so it is
                // replaced here to guarantee a 401 if the filter ever stops covering the whole chain
                exceptions.authenticationEntryPoint { _, response, exception ->
                    response.status = HttpStatus.UNAUTHORIZED.value()
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.writer.write(
                        """{"error": "Token Authentication failed", "message": "${exception.message}"}"""
                    )
                }
            }
            .build()
    }
}