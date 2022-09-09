package org.dropProject.lti

import edu.uoc.elc.lti.tool.Key
import edu.uoc.elc.lti.tool.Registration
import edu.uoc.elc.spring.lti.tool.registration.DeploymentBean
import edu.uoc.elc.spring.lti.tool.registration.KeyBean
import edu.uoc.elc.spring.lti.tool.registration.KeySetBean
import edu.uoc.elc.spring.lti.tool.registration.RegistrationBean
import edu.uoc.elc.spring.lti.tool.registration.RegistrationFactory
import edu.uoc.elc.spring.lti.tool.registration.RegistrationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service("dpRegistrationService")
class RegistrationServiceImpl: RegistrationService {

    @Value("\${lti.clientId}")
    val clientId: String = ""

    @Value("\${lti.platform}")
    val platform: String = ""

    @Value("\${lti.keySetUrl}")
    val keySetUrl: String = ""

    @Value("\${lti.accessTokenUrl}")
    val accessTokenUrl: String = ""

    @Value("\${lti.oidcAuthUrl}")
    val oidcAuthUrl: String = ""

    @Value("\${lti.deploymentId}")
    val deploymentId: String = ""

    @Value("\${lti.algorithm}")
    val algorithm: String = ""

    @Value("\${lti.privateKey}")
    val privateKey: String = ""

    @Value("\${lti.publicKey}")
    val publicKey: String = ""

    val registrationFactory = RegistrationFactory()

    val registrationBean: RegistrationBean by lazy {
        val bean = RegistrationBean()
        bean.clientId = clientId
        bean.platform = platform
        bean.keySetUrl = keySetUrl
        bean.accessTokenUrl = accessTokenUrl
        bean.oidcAuthUrl = oidcAuthUrl

        val deploymentBean = DeploymentBean()
        deploymentBean.deploymentId = deploymentId
        bean.deployments = mutableListOf(deploymentBean)

        val keysetBean = KeySetBean()
        val keyBean = KeyBean()
        keyBean.privateKey = privateKey
        keyBean.publicKey = publicKey
        keyBean.algorithm = algorithm
        keysetBean.keys = mutableListOf(keyBean)
        bean.keySet = keysetBean

        bean
    }

    override fun getRegistration(ignoredId: String): Registration {
        return registrationFactory.from(registrationBean)
    }

    override fun getAllKeys(): MutableList<Key> {
        val registration = registrationFactory.from(registrationBean)
        return registration.keySet.keys
    }
}