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

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter
import java.nio.charset.StandardCharsets
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

const val AUTH_HEADER_PARAMETER_AUTHORIZATION = "authorization"

class PersonalTokenAuthenticationFilter(
    defaultFilterProcessesUrl: String,
    authenticationManager: AuthenticationManager
) :
    AbstractAuthenticationProcessingFilter(defaultFilterProcessesUrl, authenticationManager) {

    init {
        setContinueChainBeforeSuccessfulAuthentication(true)
    }

    override fun attemptAuthentication(request: HttpServletRequest, response: HttpServletResponse): Authentication {

        // Grab basic header value from request header object.
        val basicAuthHeaderValue = request.getHeader(AUTH_HEADER_PARAMETER_AUTHORIZATION)

        if (basicAuthHeaderValue == null || !basicAuthHeaderValue.lowercase().startsWith("basic")) {
            throw AuthenticationCredentialsNotFoundException("No credentials in the request")
        }

        val base64Credentials = basicAuthHeaderValue.substring("basic".length).trim()
        val credDecoded = Base64.getDecoder().decode(base64Credentials)
        val credentials = String(credDecoded, StandardCharsets.UTF_8)

        val token = PersonalToken(credentials, authenticationDetailsSource.buildDetails(request))
        val authentication = authenticationManager.authenticate(token)
        if (!authentication.isAuthenticated) {
            throw BadCredentialsException("Invalid personal token")
        }

        SecurityContextHolder.getContext().authentication = authentication
        return authentication
    }
}
