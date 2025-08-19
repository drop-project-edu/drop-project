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
package org.dropproject.lti

import edu.uoc.elc.spring.lti.security.LTIProcessingFilter
import edu.uoc.elc.spring.lti.security.openid.OIDCFilter
import edu.uoc.elc.spring.lti.tool.ToolDefinitionBean
import edu.uoc.elc.spring.lti.tool.registration.RegistrationService
import org.dropproject.security.DropProjectSecurityConfig
import org.dropproject.security.PersonalTokenAuthenticationManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher

@Profile("lti")
@Configuration
@ComponentScan(value = ["edu.uoc.elc.spring.lti.security.mvc", "edu.uoc.elc.spring.lti.tool"])
class LTIWebSecurityConfig(
    @Qualifier("dpRegistrationService") val registrationService: RegistrationService,
    val toolDefinitionBean: ToolDefinitionBean,
    val manager: PersonalTokenAuthenticationManager,
    val ltiAuthenticationUserDetailsService: LTIAuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken>
) : DropProjectSecurityConfig(manager) {

    @Bean
    @Order(1)
    fun ltiFilterChain(http: HttpSecurity, @Qualifier("ltiAuthenticationManager") authManager: AuthenticationManager): SecurityFilterChain {
        configure(http)
        val preAuthFilter = LTIProcessingFilter(registrationService, toolDefinitionBean)
        preAuthFilter.setCheckForPrincipalChanges(true)
        preAuthFilter.setAuthenticationManager(authManager)

        http.addFilterBefore(preAuthFilter, CsrfFilter::class.java)
            .addFilterAfter(oidcFilter(), preAuthFilter.javaClass)

        return http.build()
    }

    private val OIDC_LAUNCH_URL = "/oidclaunch"
    private val OIDC_LAUNCH_URL_WITH_REGISTRATION = "/oidclaunch/{registrationId:.*}"
    private val OIDC_LAUNCH_PREFIX = "/oidclaunch"
    private val JWKS_KEYSET_URL = "/.well-known/jwks.json"

    override fun getPublicUrls(): List<String> {
        val result = ArrayList(super.getPublicUrls())
        result.add(OIDC_LAUNCH_URL)
        result.add(OIDC_LAUNCH_URL_WITH_REGISTRATION)
        result.add(JWKS_KEYSET_URL)
        return result
    }

    @Bean
    fun ltiAuthenticationManager(ltiAuthenticationUserDetailsService: LTIAuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken>): AuthenticationManager {
        val authenticationProvider = PreAuthenticatedAuthenticationProvider()
        authenticationProvider.setPreAuthenticatedUserDetailsService(ltiAuthenticationUserDetailsService)
        return ProviderManager(authenticationProvider)
    }

    private fun oidcFilter(): OIDCFilter {
        val oidcFilter = OIDCFilter(OIDC_LAUNCH_URL, OIDC_LAUNCH_PREFIX, registrationService, toolDefinitionBean)
        oidcFilter.setRequiresAuthenticationRequestMatcher(
            OrRequestMatcher(
                AntPathRequestMatcher(OIDC_LAUNCH_URL), AntPathRequestMatcher(OIDC_LAUNCH_URL_WITH_REGISTRATION)
            )
        )
        return oidcFilter
    }
}