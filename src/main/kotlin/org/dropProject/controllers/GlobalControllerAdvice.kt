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

import org.apache.tomcat.jdbc.pool.DataSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute
import java.security.Principal

@ControllerAdvice
class GlobalControllerAdvice {

    @Autowired
    lateinit var buildProperties: BuildProperties

    @Autowired
    private val dataSource: DataSource? = null

    @ModelAttribute("username")
    fun getUsername(principal: Principal?) : String {
        if (principal != null) {
            return principal.name
        } else {
            return ""
        }
    }

    @ModelAttribute("buildInfo")
    fun getBuildInfo() : BuildProperties {
        return buildProperties
    }

    @ModelAttribute("embeddedDB")
    fun isUsingEmbeddedDB() : Boolean {
        return dataSource!!.driverClassName == "org.h2.Driver"
    }

}
