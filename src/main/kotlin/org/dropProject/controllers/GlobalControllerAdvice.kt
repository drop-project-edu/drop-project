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
package org.dropProject.controllers

import com.zaxxer.hikari.HikariDataSource
import org.dropProject.extensions.realName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.info.BuildProperties
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.provisioning.UserDetailsManager
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute
import java.security.Principal
import javax.servlet.http.HttpServletRequest

@ControllerAdvice
class GlobalControllerAdvice {

    @Value("\${dropProject.admin.email}")
    val adminEmailProperty: String = ""

    @Autowired
    lateinit var buildProperties: BuildProperties

    @Autowired
    private val dataSource: HikariDataSource? = null

    @Autowired
    private val userDetailsManager: UserDetailsManager? = null

    @ModelAttribute("username")
    fun getUsername(principal: Principal?) : String {
        if (principal != null) {
            return principal.realName()
        } else {
            return ""
        }
    }

    @ModelAttribute("isTeacher")
    fun isTeacher(request: HttpServletRequest) : Boolean {
        return request.isUserInRole("TEACHER")
    }

    @ModelAttribute("buildInfo")
    fun getBuildInfo() : BuildProperties {
        return buildProperties
    }

    @ModelAttribute("adminEmail")
    fun getAdminEmail() : String {
        return adminEmailProperty
    }

    @ModelAttribute("embeddedDB")
    fun isUsingEmbeddedDB() : Boolean {
        return dataSource!!.driverClassName == "org.h2.Driver"
    }

    @ModelAttribute("demoMode")
    fun isDemoMode() : Boolean {
        if (userDetailsManager != null && userDetailsManager is InMemoryUserDetailsManager) {
            return userDetailsManager.userExists("student1") ||
                    userDetailsManager.userExists("teacher1") ||
                    userDetailsManager.userExists("admin")
        } else {
            return false
        }
    }

}
