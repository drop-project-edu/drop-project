/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2021 Pedro Alves
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

import org.dropProject.data.AuthorDetails
import org.dropProject.repository.ProjectGroupRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner


@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
class TestProjectGroupService {

    @Autowired
    private lateinit var projectGroupService: ProjectGroupService

    @Autowired
    private lateinit var projectGroupRepository: ProjectGroupRepository

    @Test
    @DirtiesContext
    fun testCreateGroupWithOneStudentTwice() {

        val authors = arrayListOf(AuthorDetails("username1", "user1"))
        val projectGroup1 = projectGroupService.getOrCreateProjectGroup(authors)
        assertEquals(1, projectGroup1.id)
        assertEquals(1, projectGroupRepository.count())

        // try to create the same group
        val projectGroup2 = projectGroupService.getOrCreateProjectGroup(authors)
        assertEquals(1, projectGroup2.id)
        assertEquals(1, projectGroupRepository.count())
    }

    @Test
    @DirtiesContext
    fun testCreateGroupWithTwoStudentsTwice() {

        val authors = arrayListOf(AuthorDetails("username1", "user1"), AuthorDetails("username2", "user2"))
        val projectGroup1 = projectGroupService.getOrCreateProjectGroup(authors)
        assertEquals(1, projectGroup1.id)
        assertEquals(1, projectGroupRepository.count())

        // try to create the same group
        val projectGroup2 = projectGroupService.getOrCreateProjectGroup(authors)
        assertEquals(1, projectGroup2.id)
        assertEquals(1, projectGroupRepository.count())
    }
}
