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

import edu.uoc.elc.lti.tool.Key
import edu.uoc.elc.spring.lti.jkws.JwksKey
import edu.uoc.elc.spring.lti.jkws.JwksKeyFactory
import edu.uoc.elc.spring.lti.tool.registration.RegistrationService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@Profile("lti")
@RestController
class WellKnownKeysController(@Qualifier("dpRegistrationService")
                              val registrationService: RegistrationService) {

    @GetMapping("/.well-known/jwks.json")
    fun getKeys(): List<JwksKey>  {
        val keys = registrationService.allKeys
        val keyFactory = JwksKeyFactory()
        return keys.map { key: Key? -> keyFactory.from(key) }
    }
}
