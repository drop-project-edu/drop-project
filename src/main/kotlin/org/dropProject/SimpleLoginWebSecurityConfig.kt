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
package org.dropProject

import org.dropProject.security.DropProjectSecurityConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity

@Profile("!deisi")
@Configuration
@EnableWebSecurity
class SimpleLoginWebSecurityConfig : DropProjectSecurityConfig() {

    override fun configure(http: HttpSecurity) {

        super.configure(http)

        http
                .csrf().disable().httpBasic()
                .and().formLogin()
                    .loginPage("/login")
                    .permitAll()
                .and().logout()
                    .permitAll()
    }



    @Autowired
    fun configureGlobal(auth: AuthenticationManagerBuilder) {
        auth.inMemoryAuthentication()
                .withUser("student1").password("123").roles("STUDENT")
                .and()
                .withUser("teacher1").password("123").roles("TEACHER")
                .and()
                .withUser("admin").password("123").roles("TEACHER", "DROP_PROJECT_ADMIN")
    }

}
