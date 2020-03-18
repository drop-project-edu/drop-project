package org.dropProject

import org.dropProject.security.DropProjectSecurityConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ResourceLoader
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority
import java.util.logging.Logger

@Profile("oauth2")
@Configuration
class OAuth2WebSecurityConfig : DropProjectSecurityConfig() {

    val LOG = Logger.getLogger(this.javaClass.name)

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    var idKey: String? = null
    val idValues = mutableMapOf<String, Array<String>>()  // idValue to roles list. e.g. "palves" -> ["ROLE_TEACHER,ROLE_ADMIN"]

    override fun configure(http: HttpSecurity) {
        super.configure(http)
        http.oauth2Login()
                .userInfoEndpoint()
                .userAuthoritiesMapper(userAuthoritiesMapper())  // assign roles to users
    }

    /**
     * This function relies on the existence of a classpath accessible oauth-roles.csv with the following format:
     *
     * login;role
     * user1;ROLE_TEACHER
     * user2;ROLE_DROP_PROJECT_ADMIN,ROLE_TEACHER
     *
     * Where "login" is the name of the attribute associated with the user through the user info endpoint
     * (see drop-project.properties)
     */
    fun userAuthoritiesMapper(): GrantedAuthoritiesMapper {

        if (resourceLoader.getResource("classpath:oauth-roles.csv").exists()) {

            LOG.info("Found oauth-roles.csv file. Will load user roles from there.")

            val rolesFile = resourceLoader.getResource("classpath:oauth-roles.csv").file
            rolesFile.readLines().forEachIndexed { index, line ->

                if (index == 0) {
                    val idKey = line.split(";")[0]
                } else {
                    val (idValue, rolesStr) = line.split(";")
                    val roles = rolesStr.split(",").toTypedArray()
                    idValues[idValue] = roles
                }
            }

            LOG.info("Loaded ${idValues.size} roles")

        } else {
            LOG.info("Didn't find oauth-roles.csv file. All users will have the STUDENT role.")
        }

        return GrantedAuthoritiesMapper { authorities: Collection<GrantedAuthority> ->

            val mappedAuthorities = mutableSetOf<GrantedAuthority>()

            authorities.forEach {
                if (it is OAuth2UserAuthority) {
                    val userAttributes = it.attributes
                    if (idKey != null) {
                        val idValue = userAttributes[idKey]
                        val roles = idValues[idValue]
                        roles?.forEach { mappedAuthorities.add(SimpleGrantedAuthority(it)) }
                                ?: mappedAuthorities.add(SimpleGrantedAuthority("ROLE_STUDENT"))
                    } else {
                        mappedAuthorities.add(SimpleGrantedAuthority("ROLE_STUDENT"))
                    }
                }
            }

            mappedAuthorities
        }
    }
}