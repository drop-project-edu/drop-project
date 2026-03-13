/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2026 Pedro Alves
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
package org.dropproject

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import org.htmlunit.WebClient
import org.htmlunit.html.HtmlElement
import org.htmlunit.html.HtmlPage
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
class GeneralWebClientTests {

    @Autowired
    lateinit var webClient: WebClient

    @Test
    @WithMockUser("student1", roles = ["STUDENT"])
    fun `student navbar shows correct items`() {
        val page: HtmlPage = webClient.getPage("/")

        // check username is displayed
        val usernameElement = page.querySelector<HtmlElement>("#currentUsername")
        assertEquals("student1", usernameElement.asNormalizedText())

        // check student-visible menu items are present
        assertNotNull(page.querySelector<HtmlElement>("#menuAccessTokens"))
        assertNotNull(page.querySelector<HtmlElement>("#menuMyHistory"))

        // check teacher/admin menu items are not present
        assertNull(page.querySelector<HtmlElement>("#navbarDropdownAssignments"))
        assertNull(page.querySelector<HtmlElement>("#navbarDropdownStudents"))
        assertNull(page.querySelector<HtmlElement>("#navbarDropdownAdmin"))
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `teacher navbar shows correct items`() {
        val page: HtmlPage = webClient.getPage("/")

        // check username is displayed
        val usernameElement = page.querySelector<HtmlElement>("#currentUsername")
        assertEquals("teacher1", usernameElement.asNormalizedText())

        // check teacher menu items are present
        assertNotNull(page.querySelector<HtmlElement>("#navbarDropdownAssignments"))
        assertNotNull(page.querySelector<HtmlElement>("#navbarDropdownStudents"))

        // check common menu items are present
        assertNotNull(page.querySelector<HtmlElement>("#menuAccessTokens"))
        assertNotNull(page.querySelector<HtmlElement>("#menuMyHistory"))

        // check admin menu items are not present
        assertNull(page.querySelector<HtmlElement>("#navbarDropdownAdmin"))
    }

    @Test
    @WithMockUser("admin1", roles = ["TEACHER", "DROP_PROJECT_ADMIN"])
    fun `admin navbar shows correct items`() {
        val page: HtmlPage = webClient.getPage("/")

        // check username is displayed
        val usernameElement = page.querySelector<HtmlElement>("#currentUsername")
        assertEquals("admin1", usernameElement.asNormalizedText())

        // check teacher menu items are present
        assertNotNull(page.querySelector<HtmlElement>("#navbarDropdownAssignments"))
        assertNotNull(page.querySelector<HtmlElement>("#navbarDropdownStudents"))

        // check admin menu items are present
        assertNotNull(page.querySelector<HtmlElement>("#navbarDropdownAdmin"))

        // check common menu items are present
        assertNotNull(page.querySelector<HtmlElement>("#menuAccessTokens"))
        assertNotNull(page.querySelector<HtmlElement>("#menuMyHistory"))
    }
}
