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
package org.dropProject.security

import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.web.access.AccessDeniedHandlerImpl
import org.springframework.security.web.authentication.logout.LogoutFilter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Implementation of the AccessDeniedHandler that either calls the default access denied impl
 * (which forwards the request to an error page) or simply returns a 403 error code (in case of
 * an API request)
 */
class APIAccessDeniedHandler(private val errorPage: String) : AccessDeniedHandlerImpl() {

    init {
        setErrorPage(errorPage)
    }

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: org.springframework.security.access.AccessDeniedException) {

        if (request.contentType == "application/json") {
            response.status = HttpStatus.FORBIDDEN.value()
            response.flushBuffer()  // to commit immediately the response
        } else {
            super.handle(request, response, accessDeniedException)
        }
    }
}

/**
 * Definitions and configurations related with Security and Role Based Access Control.
 *
 */
open class DropProjectSecurityConfig(val apiAuthenticationManager: PersonalTokenAuthenticationManager? = null) :
    WebSecurityConfigurerAdapter() {

    /**
     * Returns an array of ant matcher expressions which will be allowed without authentication
     */
    open fun getPublicUrls() = listOf("/public", "/login", "/loginFromDEISI", "/access-denied", "/error", "/h2-console/**",
        "/api-docs", "/swagger-ui.html", "/swagger-ui/**", "/swagger-resources/**", "/v2/api-docs")

    override fun configure(http: HttpSecurity) {
        http
            .csrf().ignoringAntMatchers("/").and() // disable csrf in case someone needs to access "/" by POST
            .authorizeRequests()
            .antMatchers(
                *getPublicUrls().toTypedArray()
            ).permitAll()
            .antMatchers(
                "/", "/upload", "/upload/*", "/buildReport/*", "/student/**",
                "/git-submission/refresh-git/*", "/git-submission/generate-report/*", "/mySubmissions",
                "/leaderboard/*",
                "/personalToken", "/api/student/**"
            )
            .hasAnyRole("STUDENT", "TEACHER", "DROP_PROJECT_ADMIN")
            .antMatchers("/cleanup/*", "/admin/**").hasRole("DROP_PROJECT_ADMIN")
            .anyRequest().hasAnyRole("TEACHER", "DROP_PROJECT_ADMIN")
            .and()
            .exceptionHandling()
            .accessDeniedHandler(APIAccessDeniedHandler("/access-denied.html"))

        if (apiAuthenticationManager != null) {
            http.addFilterBefore(PersonalTokenAuthenticationFilter("/api/**", apiAuthenticationManager),
                    LogoutFilter::class.java)
        }

        http.headers().frameOptions().sameOrigin()  // this is needed for h2-console

    }

    override fun configure(web: WebSecurity) {
        web.ignoring()
                .antMatchers("/resources/**", "/static/**", "/css/**", "/js/**", "/img/**", "/favicon.ico", "/webjars/**")
    }
}
