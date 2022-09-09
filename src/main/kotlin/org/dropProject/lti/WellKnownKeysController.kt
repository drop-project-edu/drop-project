package org.dropProject.lti

import edu.uoc.elc.lti.tool.Key
import edu.uoc.elc.spring.lti.jkws.JwksKey
import edu.uoc.elc.spring.lti.jkws.JwksKeyFactory
import edu.uoc.elc.spring.lti.tool.registration.RegistrationService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

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