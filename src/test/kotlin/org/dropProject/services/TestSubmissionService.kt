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
package org.dropProject.services

import org.dropProject.controllers.InvalidProjectStructureException
import org.dropProject.dao.Assignment
import org.dropProject.dao.TestVisibility
import org.dropProject.forms.SubmissionMethod
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ResourceLoader
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import java.io.File


@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
class TestSubmissionService {

    @Autowired
    private lateinit var submissionService: SubmissionService

    @Test
    fun `check authors_txt without author id`() {

        try {
            submissionService.getProjectAuthors(File("src/test/sampleAUTHORS_TXT/without_id.txt"))
            fail("Should have thrown an exception")
        } catch (e: InvalidProjectStructureException) {
            assertEquals("O número de aluno tem que estar preenchido para todos os elementos do grupo", e.message)
        }
    }

    @Test
    fun `check authors_txt with blank spaces`() {

        val authors = submissionService.getProjectAuthors(File("src/test/sampleAUTHORS_TXT/with_spaces.txt"))
        assertEquals("a21700000", authors[0].number)
        assertEquals("John Doe", authors[0].name)
    }

    @Test
    fun `check authors_txt with extra empty lines`() {

        val authors = submissionService.getProjectAuthors(File("src/test/sampleAUTHORS_TXT/with_extra_empty_line.txt"))
        assertEquals("a21700000", authors[0].number)
        assertEquals("John Doe", authors[0].name)
    }
}
