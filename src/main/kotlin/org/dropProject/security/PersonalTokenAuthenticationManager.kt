/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2021 Pedro Alves
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

import org.dropProject.dao.TokenStatus
import org.dropProject.repository.PersonalTokenRepository
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.CredentialsExpiredException
import org.springframework.security.authentication.InternalAuthenticationServiceException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
import java.util.*

class InexistentPersonalTokenException: AuthenticationException("Inexistent or expired personal token")

@Component
class PersonalTokenAuthenticationManager(val personalTokenRepository: PersonalTokenRepository): AuthenticationManager {

    override fun authenticate(authentication: Authentication): Authentication {

        val credentials = (authentication as PersonalToken).token

        val (username,personalToken) = credentials.split(":")

        val personalTokenDB = personalTokenRepository.getFirstByUserIdAndStatusOrderByStatusDateDesc(username, TokenStatus.ACTIVE)
        if (personalTokenDB != null) {
            if (personalTokenDB.expirationDate.before(Date())) {
                throw CredentialsExpiredException("Personal token is expired")
            }

            if (personalTokenDB.personalToken != personalToken) {
                throw InternalAuthenticationServiceException("Invalid personal token")
            }

            val grantedAuths = mutableListOf<GrantedAuthority>()
            personalTokenDB.profiles.split(",").forEach {
                grantedAuths.add(SimpleGrantedAuthority(it))
            }

            return UsernamePasswordAuthenticationToken(username, personalToken, grantedAuths)
        }

        throw InexistentPersonalTokenException()
    }
}
