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

import edu.uoc.elc.lti.tool.Registration
import edu.uoc.elc.spring.lti.ags.RestTemplateFactory
import edu.uoc.elc.spring.lti.security.openid.CachedOIDCLaunchSession
import edu.uoc.elc.spring.lti.security.openid.HttpSessionOIDCLaunchSession
import edu.uoc.elc.spring.lti.security.openid.RequestAwareOIDCLaunchSession
import edu.uoc.elc.spring.lti.tool.builders.ClaimAccessorService
import edu.uoc.elc.spring.lti.tool.builders.ClientCredentialsTokenBuilderService
import edu.uoc.elc.spring.lti.tool.builders.DeepLinkingTokenBuilderService
import edu.uoc.elc.spring.lti.tool.registration.RegistrationBean
import edu.uoc.lti.accesstoken.AccessTokenRequestBuilder
import edu.uoc.lti.accesstoken.JSONAccessTokenRequestBuilderImpl
import edu.uoc.lti.claims.ClaimAccessor
import edu.uoc.lti.jwt.claims.JWSClaimAccessor
import edu.uoc.lti.jwt.client.JWSClientCredentialsTokenBuilder
import edu.uoc.lti.jwt.deeplink.JWSTokenBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("lti")
@Configuration
@ComponentScan(value = ["edu.uoc.elc.spring.lti.security.mvc", "edu.uoc.elc.spring.lti.tool"])
class LTIConfig {

    @Bean
    fun claimAccessor(registrationBean: RegistrationBean): ClaimAccessor {
        return JWSClaimAccessor(registrationBean.keySetUrl)
    }

    @Bean
    fun claimAccessorService(): ClaimAccessorService {
        return ClaimAccessorService { registration: Registration -> JWSClaimAccessor(registration.keySetUrl) }
    }

    @Bean
    fun requestAwareOIDCLaunchSession(): RequestAwareOIDCLaunchSession {
        return CachedOIDCLaunchSession()
    }

    @Bean
    fun deepLinkingTokenBuilderService(): DeepLinkingTokenBuilderService {
        return DeepLinkingTokenBuilderService { registration: Registration, _: String? ->
            val key = registration.keySet.keys[0]
            JWSTokenBuilder(key.publicKey, key.privateKey, key.algorithm)
        }
    }

    @Bean
    fun clientCredentialsTokenBuilderService(): ClientCredentialsTokenBuilderService {
        return ClientCredentialsTokenBuilderService { registration: Registration, _: String? ->
            val key = registration.keySet.keys[0]
            JWSClientCredentialsTokenBuilder(key.publicKey, key.privateKey, key.algorithm)
        }
    }

    @Bean
    fun accessTokenRequestBuilder(): AccessTokenRequestBuilder {
        return JSONAccessTokenRequestBuilderImpl()
    }

    @Bean
    fun restTemplateFactory(): RestTemplateFactory {
        return RestTemplateFactory()
    }


}
