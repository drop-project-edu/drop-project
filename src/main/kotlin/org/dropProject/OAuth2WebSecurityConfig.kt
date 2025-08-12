/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2020 Pedro Alves
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
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.core.io.ResourceLoader
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority
import org.springframework.security.web.SecurityFilterChain
import java.util.logging.Logger

@Profile("oauth2")
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
class OAuth2WebSecurityConfig(val resourceLoader: ResourceLoader,
                              val manager: PersonalTokenAuthenticationManager) : DropProjectSecurityConfig(manager) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    @Value("\${dp.config.location:}")
    val configLocationFolder: String = ""

    var idKey: String? = null
    val idValues = mutableMapOf<String, Array<String>>()  // idValue to roles list. e.g. "palves" -> ["ROLE_TEACHER,ROLE_ADMIN"]

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        configure(http)
        http.oauth2Login()
                .userInfoEndpoint()
                .userAuthoritiesMapper(userAuthoritiesMapper())  // assign roles to users

        return http.build()
    }

    /**
     * This function relies on the existence of a classpath accessible oauth-roles.csv with the following format:
     *
     * login;role
     * user1;ROLE_TEACHER
     * user2;ROLE_DROP_PROJECT_ADMIN,ROLE_TEACHER
     *
     * Where "login" is the name of the attribute associated with the user through the user info endpoint
     * (see drop-project.properties)
     */
    @Bean
    fun userAuthoritiesMapper(): GrantedAuthoritiesMapper {

        val loadFromConfig = configLocationFolder.isNotEmpty() && resourceLoader.getResource("file:${configLocationFolder}/oauth-roles.csv").exists()
        val loadFromRoot = resourceLoader.getResource("classpath:oauth-roles.csv").exists()

        if (loadFromConfig || loadFromRoot) {
            val filenameAsResource = if (loadFromConfig) "file:${configLocationFolder}/oauth-roles.csv" else "classpath:oauth-roles.csv"
            LOG.info("Found ${filenameAsResource}. Will load user roles from there.")

            val rolesFile = resourceLoader.getResource(filenameAsResource).inputStream.bufferedReader()
            rolesFile.readLines().forEachIndexed { index, line ->
                if (index == 0) {
                    idKey = line.split(";")[0]
                } else {
                    val (idValue, rolesStr) = line.split(";")
                    val roles = rolesStr.split(",").toTypedArray()
                    idValues[idValue] = roles
                }
            }

            LOG.info("Loaded ${idValues.size} roles")

        } else {
            LOG.info("Didn't find oauth-roles.csv file. All users will have the STUDENT role.")
        }

        return GrantedAuthoritiesMapper { authorities: Collection<GrantedAuthority> ->

            val mappedAuthorities = mutableSetOf<GrantedAuthority>()

            authorities.forEach {
                if (it is OAuth2UserAuthority) {
                    val userAttributes = it.attributes
                    if (idKey != null) {
                        val idValue = userAttributes[idKey]
                        val roles = idValues[idValue]
                        roles?.forEach { mappedAuthorities.add(SimpleGrantedAuthority(it)) }
                                ?: mappedAuthorities.add(SimpleGrantedAuthority("ROLE_STUDENT"))
                    } else {
                        mappedAuthorities.add(SimpleGrantedAuthority("ROLE_STUDENT"))
                    }
                }
            }

            mappedAuthorities
        }
    }
}
