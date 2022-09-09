package org.dropProject.lti

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
        return DeepLinkingTokenBuilderService { registration: Registration, kid: String? ->
            val key = registration.keySet.keys[0]
            JWSTokenBuilder(key.publicKey, key.privateKey, key.algorithm)
        }
    }

    @Bean
    fun clientCredentialsTokenBuilderService(): ClientCredentialsTokenBuilderService {
        return ClientCredentialsTokenBuilderService { registration: Registration, kid: String? ->
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