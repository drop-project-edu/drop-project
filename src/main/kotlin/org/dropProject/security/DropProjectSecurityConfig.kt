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
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter

/**
 * Definitions and configurations related with Security and Role Based Access Control.
 *
 */
open class DropProjectSecurityConfig : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        http
                .authorizeRequests()
                    .antMatchers("/public", "/login", "/loginFromDEISI", "/access-denied", "/error", "/h2-console/**").permitAll()
                    .antMatchers("/", "/upload", "/upload/*", "/buildReport/*", "/student/**",
                                 "/git-submission/generate-report/*", "/mySubmissions")
                        .hasAnyRole("STUDENT", "TEACHER", "DROP_PROJECT_ADMIN")
                    .antMatchers("/cleanup/*", "/admin/**").hasRole("DROP_PROJECT_ADMIN")
                    .anyRequest().hasRole("TEACHER")
                .and()
                    .exceptionHandling()
                    .accessDeniedPage("/access-denied.html")

        http.headers().frameOptions().sameOrigin()  // this is needed for h2-console

    }

    override fun configure(web: WebSecurity) {
        web.ignoring()
                .antMatchers("/resources/**", "/static/**", "/css/**", "/js/**", "/img/**")
    }
}
