package org.dropProject.extensions

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import java.security.Principal

fun Principal.realName(): String {
    if (this is OAuth2AuthenticationToken) {
        return this.principal.attributes["login"].toString()  // this only works for github
    }
    return this.name
}
    
    