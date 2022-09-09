/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2022 Pedro Alves
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
package org.dropProject.lti

import edu.uoc.elc.lti.tool.Tool
import edu.uoc.elc.spring.lti.security.User
import edu.uoc.lti.claims.ClaimsEnum
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ResourceLoader
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.authentication.preauth.PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

/**
 * This is based on the class LTIAuthenticationUserDetailsService in the spring-boot-lti-advantage-for-dp library
 * and adds some custom behaviour
 * - The userid is retrieved from the custom claim EXT. When the request comes from moodle, it is here that we can find the userid.
 * Otherwise, we'll get a gigantic base64 encoded String as the userid, not very user-friendly.
 * - Mapping of the LTI roles to the Drop Project roles
 * - Possibility of addicional custom roles, such as ADMIN
 */
@Profile("lti")
@Service
class LTIAuthenticationUserDetailsService<T: Authentication>: AuthenticationUserDetailsService<T> {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    /**
     * The custom roles relies on the existence of a classpath accessible custom-roles.csv with the following format:
     *
     * user1;ROLE_XXX
     * user2;ROLE_DROP_PROJECT_ADMIN,ROLE_YYY
     *
     * Notice that ROLE_STUDENT and ROLE_TEACHER are already handled automtically, no need to add these.
     */
    val roles : Map<String, Array<String>> by lazy {  // username to roles list. e.g. "teacher1" -> ["ROLE_XXX,ROLE_ADMIN"]
        val internalRoles = mutableMapOf<String, Array<String>>()

        if (resourceLoader.getResource("classpath:custom-roles.csv").exists()) {

            LOG.info("Found custom-roles.csv file. Will load (additional) user roles from there.")

            val rolesFile = resourceLoader.getResource("classpath:custom-roles.csv").file
            rolesFile.readLines().forEach { line ->
                val (userid, rolesStr) = line.split(";")
                val roles = rolesStr.split(",").toTypedArray()
                internalRoles[userid] = roles
            }

            LOG.info("Loaded ${internalRoles.size} custom roles")

        } else {
            LOG.warn("Didn't find custom-roles.csv file. This is probably not intended. For example, there won't be no one with the admin role.")
        }


        internalRoles
    }
    override fun loadUserDetails(authentication: T): UserDetails? {

        if (authentication.credentials is Tool) {
            val tool = authentication.credentials as Tool
            if (tool.isValid) {
                @Suppress("UNCHECKED_CAST")
                var authorities = authentication.authorities as MutableList<SimpleGrantedAuthority>
                if (authentication.details is PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails) {
                    val details = authentication.details as PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails
                    authorities = details.grantedAuthorities.map {
                        when (it.authority) {
                            "ROLE_USER" -> SimpleGrantedAuthority("ROLE_STUDENT")
                            "ROLE_LEARNER" -> SimpleGrantedAuthority("ROLE_STUDENT")
                            "ROLE_INSTRUCTOR" -> SimpleGrantedAuthority("ROLE_TEACHER")
                            else -> throw IllegalArgumentException("Invalid role: ${it.authority}")
                        }
                    } as MutableList<SimpleGrantedAuthority>
                }
                val userid = tool.getCustomClaim(ClaimsEnum.EXT, Map::class.java)["user_username"] as String?
                if (userid != null) {
                    // add custom roles such as ROLE_DROP_PROJECT_ADMIN
                    roles[userid]?.forEach {
                        authorities.add(SimpleGrantedAuthority(it))
                    }

                    return User(userid, "N. A.", tool, authorities)
                } else {
                    return User(tool.user!!.id, "N. A.", tool, authorities)
                }
            }
        }
        return null
    }


}
