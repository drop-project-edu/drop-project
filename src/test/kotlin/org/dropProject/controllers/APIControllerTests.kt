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