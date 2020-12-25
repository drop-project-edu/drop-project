package org.dropProject.controllers

import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations=["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AdminControllerTests {

    @Autowired
    lateinit var mvc : MockMvc

    @Test
    @WithMockUser("admin",roles=["DROP_PROJECT_ADMIN"])
    @DirtiesContext
    fun test_00_getDashboard() {
        this.mvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk)
    }

    @Test
    @WithMockUser("admin",roles=["DROP_PROJECT_ADMIN"])
    @DirtiesContext
    fun test_01_changeMavenOutput() {
        this.mvc.perform(post("/admin/dashboard")
                .param("showMavenOutput", "true"))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/admin/dashboard"))
    }
}