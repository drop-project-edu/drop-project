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

open class DropProjectSecurityConfig : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        http
                .authorizeRequests()
                    .antMatchers("/public", "/login", "/loginFromDEISI", "/access-denied", "/error").permitAll()
                    .antMatchers(HttpMethod.GET, "/assignment/**").hasRole("TEACHER")
                    .antMatchers(HttpMethod.POST, "/assignment/**").hasRole("TEACHER")
                    .antMatchers(
                            "/report",
                            "/submission-report",
                            "/submissions",
                            "/rebuild/*",
                            "/rebuildFull/*",
                            "/markAsFinal/*",
                            "/downloadMavenProject/*",
                            "/downloadOriginalAll/*",
                            "/downloadMavenizedAll/*",
                            "/cleanup/*"
                    ).hasRole("TEACHER")  // TODO: review
                .antMatchers(
                        "/cleanup/*"
                    ).hasRole("DROP_PROJECT_ADMIN")
                    .anyRequest().authenticated()
                .and()
                    .exceptionHandling()
                    .accessDeniedPage("/access-denied.html")

    }

    override fun configure(web: WebSecurity) {
        web.ignoring()
                .antMatchers("/resources/**", "/static/**", "/css/**", "/js/**", "/img/**")
    }
}
