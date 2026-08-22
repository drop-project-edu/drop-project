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
package org.dropproject.security

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.CredentialsExpiredException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.util.*
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse


const val AUTH_HEADER_PARAMETER_AUTHORIZATION = "authorization"

class PersonalTokenAuthenticationFailureHandler : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(request: HttpServletRequest?, response: HttpServletResponse, exception: AuthenticationException) {
        // Set the response status and write custom error message
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        response.writer.write(("{\"error\": \"Token Authentication failed\", \"message\": \"" + exception.message) + "\"}")
    }
}

/**
 * Authenticates every request of the API chain with the personal token that is sent in the `authorization` header.
 *
 * This is a plain OncePerRequestFilter, and not an AbstractAuthenticationProcessingFilter, because the API is
 * stateless: there is no login endpoint to submit the credentials to and no target url to redirect to after a
 * successful authentication. The requests that are rejected here are answered with a 401 by
 * [PersonalTokenAuthenticationFailureHandler] and never reach the controllers.
 *
 * The filter is not restricted to a url of its own, because the chain that it belongs to
 * (see [org.dropproject.config.ApiSecurityConfig]) is already restricted to the API.
 */
class PersonalTokenAuthenticationFilter(
    private val authenticationManager: AuthenticationManager
) : OncePerRequestFilter() {

    private val detailsSource = WebAuthenticationDetailsSource()
    private val failureHandler = PersonalTokenAuthenticationFailureHandler()

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {

        // Grab basic header value from request header object.
        val basicAuthHeaderValue = request.getHeader(AUTH_HEADER_PARAMETER_AUTHORIZATION)

        if (basicAuthHeaderValue == null || !basicAuthHeaderValue.lowercase().startsWith("basic")) {
            fail(request, response, AuthenticationCredentialsNotFoundException("No credentials in the request"))
            return
        }

        val authentication = try {
            val base64Credentials = basicAuthHeaderValue.substring("basic".length).trim()
            val credDecoded = Base64.getDecoder().decode(base64Credentials)
            val credentials = String(credDecoded, StandardCharsets.UTF_8)

            val token = PersonalToken(credentials, detailsSource.buildDetails(request))
            authenticationManager.authenticate(token).also {
                if (!it.isAuthenticated) {
                    throw BadCredentialsException("Invalid personal token")
                }
            }
        } catch (e: CredentialsExpiredException) {
            fail(request, response, BadCredentialsException(e.message))
            return
        } catch (e: IllegalArgumentException) {
            // the header is not valid base64, which is a malformed credential and not a server error
            fail(request, response, BadCredentialsException("Invalid personal token"))
            return
        } catch (e: AuthenticationException) {
            fail(request, response, e)
            return
        }

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)

        filterChain.doFilter(request, response)
    }

    private fun fail(request: HttpServletRequest, response: HttpServletResponse, exception: AuthenticationException) {
        SecurityContextHolder.clearContext()
        failureHandler.onAuthenticationFailure(request, response, exception)
    }
}
