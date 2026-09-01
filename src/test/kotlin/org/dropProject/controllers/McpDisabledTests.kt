/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2025 Pedro Alves
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
package org.dropproject.controllers

import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertFalse

/**
 * Checks that drop-project.mcp.enabled=false really turns the MCP server off, instead of just being an inert property.
 */
@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"],
                    properties = ["drop-project.mcp.enabled=false"])
@ActiveProfiles("test")
@DirtiesContext
class McpDisabledTests {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var context: ApplicationContext

    @Test
    fun `the mcp beans are not registered`() {
        assertFalse(context.containsBean("mcpController"), "McpController must not be registered")
        assertFalse(context.containsBean("mcpSecurityFilterChain"), "the mcp filter chain must not be registered")
    }

    @Test
    fun `the mcp endpoint does not answer`() {
        mvc.perform(
            post("/mcp/")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"jsonrpc": "2.0", "id": "init-1", "method": "initialize"}""")
        )
            .andExpect(status().is4xxClientError)
    }
}
