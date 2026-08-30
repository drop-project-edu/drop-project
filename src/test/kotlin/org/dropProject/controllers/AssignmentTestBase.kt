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
import org.dropproject.dao.*
import org.dropproject.repository.*
import org.dropproject.services.AssignmentService
import org.dropproject.services.ScheduledTasks
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.dropproject.config.DropProjectProperties
import org.springframework.cache.CacheManager
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.*

const val sampleJavaAssignmentRepo = "git@github.com:drop-project-edu/sampleJavaAssignment.git"
const val sampleKotlinAssignmentRepo = "git@github.com:drop-project-edu/sampleKotlinAssignment.git"
const val sampleJavaAssignmentWithJUnit5Repo = "git@github.com:drop-project-edu/sampleJavaAssignmentWithJunit5.git"

/**
 * Shared plumbing for the Assignment*Tests classes (split from the former giant
 * AssignmentControllerTests).
 */
abstract class AssignmentTestBase {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var cacheManager: CacheManager

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var assigneeRepository: AssigneeRepository

    @Autowired
    lateinit var submissionRepository: SubmissionRepository

    @Autowired
    lateinit var gitSubmissionRepository: GitSubmissionRepository

    @Autowired
    lateinit var assignmentTagRepository: AssignmentTagRepository

    @Autowired
    lateinit var assignmentReportRepository: AssignmentReportRepository

    @Autowired
    lateinit var projectGroupRepository: ProjectGroupRepository

    @Autowired
    lateinit var projectGroupRestrictionsRepository: ProjectGroupRestrictionsRepository

    @Autowired
    lateinit var assignmentService: AssignmentService

    @Autowired
    lateinit var assignmentFixtures: AssignmentFixtures
    @Autowired
    lateinit var submissionFixtures: SubmissionFixtures
    @Autowired
    lateinit var gitFixtures: GitFixtures

    @Autowired
    lateinit var scheduledTasks: ScheduledTasks

    @Autowired
    lateinit var dropProjectProperties: DropProjectProperties
}
