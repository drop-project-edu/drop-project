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
package org.dropproject.extensions

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import java.security.Principal

/**
 * This file contains extensions to the default [Principal] class.
 */

fun Principal.realName(): String {
    if (this is OAuth2AuthenticationToken) {
        return this.principal.attributes["login"].toString()  // this only works for github
    }
    return this.name
}
    
    
