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
package org.dropProject.controllers

import org.dropProject.dao.PersonalToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers


interface APIControllerTests {

    fun generateToken(user: String, roles: List<SimpleGrantedAuthority>, mvc: MockMvc): String {
        mvc.perform(
            MockMvcRequestBuilders.post("/personalToken")
                .with(SecurityMockMvcRequestPostProcessors.user(User(user, "", roles))))
            .andExpect(MockMvcResultMatchers.status().isFound)  // redirect

        val mvcResult = mvc.perform(
            MockMvcRequestBuilders.get("/personalToken")
                .with(SecurityMockMvcRequestPostProcessors.user(User(user, "", roles))))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()

        return (mvcResult.modelAndView!!.modelMap["token"] as PersonalToken).personalToken
    }
}
