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
package org.dropproject.controllers

import org.dropproject.AssignmentFixtures
import org.dropproject.SubmissionFixtures
import org.dropproject.GitFixtures
import org.junit.jupiter.api.Assertions.*
import org.dropproject.dao.*
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
import org.dropproject.services.CooloffOverrideService
import org.dropproject.services.ZipService
import org.dropproject.storage.StorageService
import org.hamcrest.CoreMatchers.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.dropproject.config.DropProjectProperties
import org.springframework.core.io.ResourceLoader
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File
import java.util.*

/**
 * Shared plumbing for the Upload*Tests classes (split from the former giant
 * UploadControllerTests): common repositories, users and the default assignment setup.
 */
abstract class UploadTestBase {

    @Autowired
    lateinit var dropProjectProperties: DropProjectProperties

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    @Autowired
    lateinit var authorRepository: AuthorRepository

    @Autowired
    lateinit var submissionRepository: SubmissionRepository

    @Autowired
    lateinit var rebuildStatusRepository: RebuildStatusRepository

    @Autowired
    lateinit var jUnitReportRepository: JUnitReportRepository

    @Autowired
    lateinit var assignmentACLRepository: AssignmentACLRepository

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var assigneeRepository: AssigneeRepository

    @Autowired
    lateinit var assignmentTestMethodRepository: AssignmentTestMethodRepository

    @Autowired
    lateinit var projectGroupRestrictionsRepository: ProjectGroupRestrictionsRepository

    @Autowired
    lateinit var zipService: ZipService

    @Autowired
    lateinit var storageService: StorageService

    @Autowired
    lateinit var assignmentFixtures: AssignmentFixtures
    @Autowired
    lateinit var submissionFixtures: SubmissionFixtures
    @Autowired
    lateinit var gitFixtures: GitFixtures

    @Autowired
    lateinit var cooloffOverrideService: CooloffOverrideService

    @BeforeEach
    fun setup() {
        var folder = File(dropProjectProperties.mavenizedProjects.rootLocation)
        if (folder.exists()) {
            folder.deleteRecursively()
        }
        folder.mkdirs()

        // create initial assignment
        assignmentFixtures.createDefaultAssignment(gitCurrentHash = "somehash", withTestMethods = true)
    }

    @AfterEach
    fun cleanup() {
        val folder = File(dropProjectProperties.mavenizedProjects.rootLocation)
        if (folder.exists()) {
            folder.deleteRecursively()
        }

        val submissionsFolder = File(dropProjectProperties.storage.rootLocation)
        if (submissionsFolder.exists()) {
            submissionsFolder.deleteRecursively()
        }
    }
}
