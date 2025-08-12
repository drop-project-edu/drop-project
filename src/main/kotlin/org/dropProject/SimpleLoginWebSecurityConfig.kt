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
import org.dropProject.security.PersonalTokenAuthenticationManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.core.io.ResourceLoader
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.stereotype.Component
import java.util.*
import java.util.logging.Logger

@Component
class InMemoryUserDetailsManagerFactory(
    val resourceLoader: ResourceLoader
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    @Value("\${dp.config.location:}")
    val configLocationFolder: String = ""

    @Bean
    fun inMemoryUserDetailsManager(): InMemoryUserDetailsManager {

        LOG.info("Using inMemoryAuthentication")

        val usersList = mutableListOf<UserDetails>()

        val loadFromConfig =
            configLocationFolder.isNotEmpty() && resourceLoader.getResource("file:${configLocationFolder}/users.csv")
                .exists()
        val loadFromRoot = resourceLoader.getResource("classpath:users.csv").exists()

        if (loadFromConfig || loadFromRoot) {
            val filenameAsResource =
                if (loadFromConfig) "file:${configLocationFolder}/users.csv" else "classpath:users.csv"
            LOG.info("Found ${filenameAsResource}. Will load user details from there.")

            val usersFile = resourceLoader.getResource(filenameAsResource).inputStream.bufferedReader()
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
            usersList.add(
                User.withUsername("admin").password("{noop}123").roles("TEACHER", "DROP_PROJECT_ADMIN").build()
            )
        }

        return InMemoryUserDetailsManager(usersList)

    }
}

@Profile("!deisi & !oauth2 & !lti")
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
class SimpleLoginWebSecurityConfig(val manager: PersonalTokenAuthenticationManager) :
    DropProjectSecurityConfig(manager) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        configure(http)
        http
            .csrf { it.disable() }
            .httpBasic { }
            .formLogin { 
                it.loginPage("/login").permitAll() 
            }
            .logout { it.permitAll() }

        return http.build()
    }


}
