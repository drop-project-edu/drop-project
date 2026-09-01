/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2025 Pedro Alves
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
package org.dropproject.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.dropproject.mcp.services.McpService
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Filter that validates Bearer tokens for MCP endpoints.
 */
class McpBearerTokenFilter(
    private val mcpService: McpService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        
        if (authHeader?.startsWith("Bearer ") == true) {
            val token = authHeader.substring(7)
            val personalToken = mcpService.validateBearerToken(token)

            if (personalToken != null) {
                // The token carries the roles that its owner had when it was generated, so it must never grant more
                // than those. The tools are responsible for checking whether the role is enough for what they do.
                val authorities = personalToken.profiles
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { SimpleGrantedAuthority(it) }

                val user = User.builder()
                    .username(personalToken.userId)
                    .password("[PROTECTED]")
                    .authorities(authorities)
                    .build()
                
                val authentication = UsernamePasswordAuthenticationToken(
                    user, null, user.authorities
                )
                SecurityContextHolder.getContext().authentication = authentication
            }
        }
        
        filterChain.doFilter(request, response)
    }
}