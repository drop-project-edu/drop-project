/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 Pedro Alves
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
package org.dropProject.controllers

import org.dropProject.dao.PersonalToken
import org.dropProject.dao.TokenStatus
import org.dropProject.extensions.realName
import org.dropProject.repository.PersonalTokenRepository
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Controller
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import java.security.Principal
import java.time.LocalDate
import java.time.ZoneId
import java.util.*


const val TOKEN_LENGTH = 20

/**
 * LoginController contains MVC controller functions that handle the login related requests.
 */
@Controller
class LoginController(val personalTokenRepository: PersonalTokenRepository) {

    private val charPool : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9') // for token generation

    @RequestMapping(value = ["/login"], method = [RequestMethod.GET])
    fun login(): String {
        return "login"
    }

    // lacking a better place for this redirection...
    @RequestMapping(value = ["/api-docs"], method = [RequestMethod.GET])
    fun api(): String {
        return "redirect:/swagger-ui/"
    }

    @RequestMapping(value = ["/personalToken"], method = [RequestMethod.GET])
    fun getPersonalToken(principal: Principal, model: ModelMap): String {
        val token = personalTokenRepository.
            getFirstByUserIdAndStatusOrderByStatusDateDesc(principal.realName(), TokenStatus.ACTIVE)

        model["token"] = token
        return "personal-tokens-list"
    }

    @RequestMapping(value = ["/personalToken"], method = [RequestMethod.POST])
    fun generatePersonalToken(principal: Principal, model: ModelMap): String {
        val previousToken = personalTokenRepository
            .getFirstByUserIdAndStatusOrderByStatusDateDesc(principal.realName(), TokenStatus.ACTIVE)
        if (previousToken != null) {
            previousToken.status = TokenStatus.DELETED
            previousToken.statusDate = Date()
            personalTokenRepository.save(previousToken)
        }

        val currentDate = LocalDate.now()
        val expirationLocalDate = if (currentDate.month.value > 8) LocalDate.of(currentDate.year+1,8,31) else LocalDate.of(currentDate.year,8,31)
        val expirationDate = Date.from(expirationLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        // make sure we generate a unique token
        var randomToken: String
        do {
            randomToken = (1..TOKEN_LENGTH)
                .map { _ -> kotlin.random.Random.nextInt(0, charPool.size) }
                .map(charPool::get)
                .joinToString("");
        } while (personalTokenRepository.getByPersonalToken(randomToken) != null)

        val authorities = SecurityContextHolder.getContext().authentication.authorities as Collection<GrantedAuthority>
        val rolesStr = authorities.map { it.authority }.joinToString(",")

        val newToken = PersonalToken(userId = principal.realName(), personalToken = randomToken,
                                     expirationDate = expirationDate,
                                     status = TokenStatus.ACTIVE, statusDate = Date(), profiles = rolesStr)
        personalTokenRepository.save(newToken)

        return "redirect:/personalToken"
    }
}
