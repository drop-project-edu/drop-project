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
package edu.uoc.elc.lti.tool

import edu.uoc.elc.lti.exception.BadToolProviderConfigurationException
import edu.uoc.elc.lti.platform.accesstoken.AccessTokenRequestHandler
import edu.uoc.elc.lti.platform.accesstoken.AccessTokenResponse
import edu.uoc.elc.lti.platform.ags.AgsClientFactory
import edu.uoc.elc.lti.platform.deeplinking.DeepLinkingClient
import edu.uoc.elc.lti.tool.deeplinking.Settings
import edu.uoc.elc.lti.tool.oidc.AuthRequestUrlBuilder
import edu.uoc.elc.lti.tool.oidc.LoginRequest
import edu.uoc.elc.lti.tool.oidc.LoginResponse
import edu.uoc.lti.MessageTypesEnum
import edu.uoc.lti.claims.ClaimAccessor
import edu.uoc.lti.claims.ClaimsEnum
import edu.uoc.lti.oidc.OIDCLaunchSession
import java.io.IOException
import java.math.BigInteger
import java.net.URI
import java.net.URISyntaxException
import java.security.SecureRandom
import java.util.*

/**
 * This shadows the class Tool in the spring-boot-lti-advantage-for-dp library (based on lti-13-1.0.0)
 * to add the method getCustomClaim(), already discussed with Xavier (owner of the library)
 *
 * TODO: Remove this class after lti-13-1.0.1 is released
 */
class Tool {
    var issuer: String? = null
    var audiences: List<String>? = null
    var kid: String? = null
    var issuedAt: Date? = null
    var expiresAt: Date? = null
    var user: User? = null
    var locale: String? = null
    var isValid: Boolean = false
    private var reason: String? = null

    private var accessTokenResponse: AccessTokenResponse? = null
    private var registration: Registration? = null
    private var claimAccessor: ClaimAccessor? = null
    private var oidcLaunchSession: OIDCLaunchSession? = null
    private var toolBuilders: ToolBuilders? = null

    constructor(registration: Registration, claimAccessor: ClaimAccessor, oidcLaunchSession: OIDCLaunchSession, toolBuilders: ToolBuilders) {
        this.registration = registration
        this.claimAccessor = claimAccessor
        this.oidcLaunchSession = oidcLaunchSession
        this.toolBuilders = toolBuilders
    }



    fun validate(token: String?, state: String?): Boolean {
        val launchValidator: LaunchValidator = LaunchValidator(registration, claimAccessor, oidcLaunchSession)
        isValid = launchValidator.validate(token, state)
        if (!isValid) {
            reason = launchValidator.reason
            return false
        }

        // get the standard JWT payload claims
        issuer = claimAccessor!!.issuer
        audiences = claimAccessor!!.audiences
        issuedAt = claimAccessor!!.issuedAt
        expiresAt = claimAccessor!!.expiration

        // create the user attribute
        createUser(claimAccessor!!.subject)

        // update locale attribute
        locale = claimAccessor!!.get(ClaimsEnum.LOCALE)
        return isValid
    }

    @get:Throws(IOException::class, BadToolProviderConfigurationException::class)
    val accessToken: AccessTokenResponse?
        get() {
            if (!this.isValid) {
                return null
            }
            if (accessTokenResponse == null) {
                val accessTokenRequestHandler: AccessTokenRequestHandler = AccessTokenRequestHandler(
                    kid,
                    registration,
                    toolBuilders!!.clientCredentialsTokenBuilder,
                    toolBuilders!!.accessTokenRequestBuilder
                )
                accessTokenResponse = accessTokenRequestHandler.accessToken
            }
            return accessTokenResponse
        }

    private fun createUser(subject: String) {
        user = User.builder()
            .id(subject)
            .givenName(claimAccessor!!.get(ClaimsEnum.GIVEN_NAME))
            .familyName(claimAccessor!!.get(ClaimsEnum.FAMILY_NAME))
            .middleName(claimAccessor!!.get(ClaimsEnum.MIDDLE_NAME))
            .picture(claimAccessor!!.get(ClaimsEnum.PICTURE))
            .email(claimAccessor!!.get(ClaimsEnum.EMAIL))
            .name(claimAccessor!!.get(ClaimsEnum.NAME))
            .build()
    }

    // general claims getters
    val platform: Platform
        get() = claimAccessor!!.get(ClaimsEnum.TOOL_PLATFORM, Platform::class.java)

    val context: Context
        get() = claimAccessor!!.get(ClaimsEnum.CONTEXT, Context::class.java)
    val resourceLink: ResourceLink
        get() = claimAccessor!!.get(ClaimsEnum.RESOURCE_LINK, ResourceLink::class.java)
    val nameRoleService: NamesRoleService
        get() = claimAccessor!!.get(ClaimsEnum.NAMES_ROLE_SERVICE, NamesRoleService::class.java)
    val assignmentGradeService: AssignmentGradeService
        get() = claimAccessor!!.get(ClaimsEnum.ASSIGNMENT_GRADE_SERVICE, AssignmentGradeService::class.java)
    val deploymentId: String?
        get() {
            if (!isDeepLinkingRequest) {
                return null
            }
            return claimAccessor!!.get(ClaimsEnum.DEPLOYMENT_ID)
        }
    val deepLinkingSettings: Settings?
        get() {
            if (!isDeepLinkingRequest) {
                return null
            }
            return claimAccessor!!.get(ClaimsEnum.DEEP_LINKING_SETTINGS, Settings::class.java)
        }
    val roles: List<String>?
        get() {
            @Suppress("UNCHECKED_CAST")
            return claimAccessor!!.get(ClaimsEnum.ROLES, List::class.java as Class<List<String>>)
        }

    fun getCustomParameter(name: String): Any? {
        val claim: Map<String, Any>? = customParameters
        if (claim != null) {
            return claim.get(name)
        }
        return null
    }

    val customParameters: Map<String, Any>
        get() {
            @Suppress("UNCHECKED_CAST")
            val claim: Map<String, Any> = claimAccessor!!.get(
                ClaimsEnum.CUSTOM,
                Map::class.java as Class<Map<String,Any>>
            )
            return claim
        }

    fun <T> getCustomClaim(claim: ClaimsEnum?, returnClass: Class<T>?): T {
        return claimAccessor!!.get(claim, returnClass)
    }

    val messageType: MessageTypesEnum?
        get() {
            try {
                return MessageTypesEnum.valueOf(claimAccessor!!.get(ClaimsEnum.MESSAGE_TYPE))
            } catch (ignored: IllegalArgumentException) {
                return null
            }
        }
    val isDeepLinkingRequest: Boolean
        get() = MessageTypesEnum.LtiDeepLinkingRequest == messageType
    val isResourceLinkLaunch: Boolean
        get() = MessageTypesEnum.LtiResourceLinkRequest == messageType
    val deepLinkingClient: DeepLinkingClient?
        get() {
            if (!isDeepLinkingRequest) {
                return null
            }
            return DeepLinkingClient(
                toolBuilders!!.deepLinkingTokenBuilder,
                issuer,
                registration!!.clientId,
                claimAccessor!!.azp,
                deploymentId,
                claimAccessor!!.get(ClaimsEnum.NONCE),
                deepLinkingSettings
            )
        }
    val assignmentGradeServiceClientFactory: AgsClientFactory
        get() = AgsClientFactory(
            assignmentGradeService,
            resourceLink
        )

    // roles commodity methods
    val isLearner: Boolean
        get() {
            return roles != null && roles!!.contains(RolesEnum.LEARNER.getName())
        }

    val isInstructor: Boolean
        get() {
            return roles != null && roles!!.contains(RolesEnum.INSTRUCTOR.getName())
        }

    // openid methods
    @Throws(URISyntaxException::class)
    fun getOidcAuthUrl(loginRequest: LoginRequest): String {
        val loginResponse: LoginResponse = LoginResponse.builder()
            .client_id(if (loginRequest.client_id != null) loginRequest.client_id else registration!!.clientId)
            .redirect_uri(loginRequest.target_link_uri)
            .login_hint(loginRequest.login_hint)
            .state(BigInteger(50, SecureRandom()).toString(16))
            .nonce(BigInteger(50, SecureRandom()).toString(16))
            .lti_message_hint(loginRequest.lti_message_hint)
            .build()

        // save in session
        oidcLaunchSession!!.state = loginResponse.state
        oidcLaunchSession!!.nonce = loginResponse.nonce
        oidcLaunchSession!!.targetLinkUri = loginResponse.redirect_uri
        oidcLaunchSession!!.clientId = loginResponse.client_id
        oidcLaunchSession!!.deploymentId = loginRequest.lti_deployment_id

        // return url
        return AuthRequestUrlBuilder.build(registration!!.oidcAuthUrl, loginResponse)
    }
}
