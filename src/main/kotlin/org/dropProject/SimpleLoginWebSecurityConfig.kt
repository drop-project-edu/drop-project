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
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ResourceLoader
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import java.util.*
import java.util.logging.Logger


@Profile("!deisi & !oauth2")
@Configuration
@EnableWebSecurity
class SimpleLoginWebSecurityConfig : DropProjectSecurityConfig() {

    val LOG = Logger.getLogger(this.javaClass.name)

    @Autowired
    lateinit var resourceLoader: ResourceLoader

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
        auth.userDetailsService(inMemoryUserDetailsManager())
    }

    @Bean
    fun inMemoryUserDetailsManager(): InMemoryUserDetailsManager {

        LOG.info("Using inMemoryAuthentication")

        val usersList = mutableListOf<UserDetails>()

        if (resourceLoader.getResource("classpath:users.csv").exists()) {

            LOG.info("Found users.csv file. Will load user details from there.")

            val usersFile = resourceLoader.getResource("classpath:users.csv").file
            usersFile.readLines().forEach {
                if (it != "username;password;roles") {  // skip header
                    val (username, password, rolesStr) = it.split(";")
                    val roles = rolesStr.split(",").toTypedArray()
                    usersList.add(User.withUsername(username).password("{noop}$password").roles(*roles).build())
                }
            }

            LOG.info("Loaded ${usersList.size} users")

        } else {

            LOG.info("Didn't find users.csv file. Will create default users.")

            usersList.add(User.withUsername("student1").password("{noop}123").roles("STUDENT").build())
            usersList.add(User.withUsername("teacher1").password("{noop}123").roles("TEACHER").build())
            usersList.add(User.withUsername("admin").password("{noop}123").roles("TEACHER", "DROP_PROJECT_ADMIN").build())
        }

        return InMemoryUserDetailsManager(usersList)
    }

}
