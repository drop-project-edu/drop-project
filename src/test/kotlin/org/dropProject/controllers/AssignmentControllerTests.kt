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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import org.apache.commons.io.FileUtils
import org.dropProject.TestsHelper
import org.dropProject.TestsHelper.Companion.sampleJavaAssignmentPrivateKey
import org.dropProject.TestsHelper.Companion.sampleJavaAssignmentPublicKey
import org.dropProject.dao.*
import org.dropProject.data.SubmissionInfo
import org.dropProject.extensions.formatJustDate
import org.dropProject.forms.AssignmentForm
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.*
import org.dropProject.services.AssignmentService
import org.dropProject.services.GitClient
import org.dropProject.services.ScheduledTasks
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.*
import org.hamcrest.collection.IsCollectionWithSize.hasSize
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.validation.BindingResult
import java.io.File
import java.util.*

const val sampleJavaAssignmentRepo = "git@github.com:drop-project-edu/sampleJavaAssignment.git"
const val sampleKotlinAssignmentRepo = "git@github.com:drop-project-edu/sampleKotlinAssignment.git"
const val sampleJavaAssignmentWithJUnit5Repo = "git@github.com:drop-project-edu/sampleJavaAssignmentWithJunit5.git"

@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AssignmentControllerTests {

    @Autowired
    lateinit var mvc: MockMvc

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
    lateinit var projectGroupRestrictionsRepository: ProjectGroupRestrictionsRepository

    @Autowired
    lateinit var assignmentService: AssignmentService

    @Autowired
    lateinit var testsHelper: TestsHelper

    @Autowired
    lateinit var scheduledTasks: ScheduledTasks

    @Value("\${assignments.rootLocation}")
    val assignmentsRootLocation: String = ""

    @Value("\${storage.rootLocation}/upload")
    val uploadSubmissionsRootLocation: String = "submissions/upload"

    val TEACHER_1 = User("teacher1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))
    val STUDENT_1 = User("student1", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_00_getNewAssignmentForm() {
        this.mvc.perform(get("/assignment/new"))
            .andExpect(status().isOk)
    }


    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_01_createInvalidAssignment() {

        mvc.perform(post("/assignment/new"))
            .andExpect(status().isOk)
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "assignmentId"))


        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", "git://dummy")
                .param("acceptsStudentTests", "true")  // <<<<<
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "acceptsStudentTests"))

        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", "git://dummy")
                .param("minStudentTests", "1")  // <<<<<
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "acceptsStudentTests"))

        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", "git://dummy")
                .param("calculateStudentTestsCoverage", "true") // <<<<<
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "acceptsStudentTests"))

        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", "git://dummy")
                .param("acceptsStudentTests", "true")
                .param("calculateStudentTestsCoverage", "true")
                .param("minStudentTests", "1")
                .param("acl", "teacher1,teacher2")  // <<<<< acl should not include the session owner (teacher1)
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "acl"))

        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", "git://dummy")
                .param("minGroupSize", "-1")
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "minGroupSize"))

        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", "git://dummy")
                .param("maxGroupSize", "2")  // <<< minGroupSize is missing
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "minGroupSize"))

        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", "git://dummy")
                .param("exceptions", "user1,user2")  // <<< minGroupSize is missing
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "exceptions"))


        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
                .param("acceptsStudentTests", "true")    // <<<<
                .param("calculateStudentTestsCoverage", "true")  // <<<<
                .param("minStudentTests", "1")   // <<<<
        )
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "/assignment/setup-git/assignmentId"))

        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
                .param("visibility", "PRIVATE")    // <<<< assignees is missing
        )
            .andExpect(status().isOk())
            .andExpect(view().name("assignment-form"))
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "assignees"))

    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_02_createNewAssignmentAndConnectWithGithub() {

        try {
            val createdAssignment = testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment1", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo
            )
            assertEquals("dummyAssignment1", createdAssignment.id)
            assertEquals("88e3327370242da0c3ae99e6bfdd5ac22148e213", createdAssignment.gitCurrentHash)

            val result = this.mvc.perform(get("/assignment/info/dummyAssignment1"))
                .andExpect(status().isOk)
                .andExpect(view().name("assignment-detail"))
                .andExpect(model().hasNoErrors())
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignment = result.modelAndView!!.modelMap["assignment"] as Assignment
            assertEquals("dummyAssignment1", assignment.id)
            @Suppress("UNCHECKED_CAST")
            val report = result.modelAndView!!.modelMap["report"] as List<AssignmentReport>
            assertEquals(6, report.size)
            assertEquals("Assignment has a pom.xml", report[0].message)
            assertEquals("Doesn't use the 'dropProject.currentUserId' system property", report[1].message)
            assertEquals("POM file is prepared to prevent stacktrace trimming on junit errors", report[2].message)
            assertEquals("Found 1 test classes", report[3].message)
            assertEquals("You have defined 4 test methods with timeout.", report[4].message)
            assertEquals("You are using a recent version of checkstyle.", report[5].message)

            @Suppress("UNCHECKED_CAST")
            val tests = result.modelAndView!!.modelMap["tests"] as List<AssignmentTestMethod>
            assertEquals(4, tests.size)
            assertEquals("test_001_FindMax", tests[0].testMethod)
            assertEquals("test_002_FindMaxAllNegative", tests[1].testMethod)

            // change the assignment to have a mandatory tests suffix
            assignment.mandatoryTestsSuffix = "_MANDATORY"
            assignmentRepository.save(assignment)

            // refresh the assignment to kick the validation process
            this.mvc.perform(post("/assignment/refresh-git/dummyAssignment1")).andExpect(status().isOk)

            // get information again
            val result2 = this.mvc.perform(get("/assignment/info/dummyAssignment1"))
                .andExpect(status().isOk)
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val report2 = result2.modelAndView!!.modelMap["report"] as List<AssignmentReport>
            assertEquals(7, report2.size)
            assertEquals("You haven't defined mandatory tests", report2[5].message)

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment1").exists()) {
                File(assignmentsRootLocation, "dummyAssignment1").deleteRecursively()
            }
        }
    }

    @Ignore("THIS TEST IS FAILING BECAUSE BITBUCKET DOESNT RECOGNIZE THE PUBLIC KEY")
    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_03_createNewAssignmentAndConnectWithBitbucket() {

        try {
            // post form
            this.mvc.perform(
                post("/assignment/new")
                    .param("assignmentId", "dummyAssignment2")
                    .param("assignmentName", "Dummy Assignment")
                    .param("assignmentPackage", "org.dummy")
                    .param("submissionMethod", "UPLOAD")
                    .param("language", "JAVA")
                    .param("gitRepositoryUrl", "git@bitbucket.org:palves-ulht/projecto-modelo-aed-2017-18.git")
//                    .param("gitRepositoryUrl", "git@bitbucket.org:pedrohalves/projecto-modelo-aed-2017-18.git")
            )
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/assignment/setup-git/dummyAssignment2"))


            // get assignment detail
            this.mvc.perform(get("/assignment/setup-git/dummyAssignment2"))
                .andExpect(status().isOk)

            // inject private and public key to continue
            val assignment = assignmentRepository.getById("dummyAssignment2")
            assignment.gitRepositoryPrivKey = "-----BEGIN RSA PRIVATE KEY-----\n" +
                    "MIIEowIBAAKCAQEAgbzH8iu5BsdX8fZhsiqQRgG/ICbJ2gy4guNltnBeRchInAmP\n" +
                    "UdjAbLUBOwCAaixz4F5rtOvmuNy2kjpqmvdT8Ltoaox+GnSdsTRDVALmrST5MS4w\n" +
                    "PvMz/Gcd9wMjGLYIK6SKlGK3NxnLN5BZ9LekH/vmJwEqDRXVkAara+wg+VFEi2ij\n" +
                    "CknVjgvuqsXb3TUZtEPpSdsm2SSuEePyskajGlUx/464CRMsUwDUW9fmkaO5GvTT\n" +
                    "kBnYkI+2pdTo2bStcbHtI9NDjqmnjpSQIXqFqgbhL45NIp1n9AJMWjztPh4PUOTl\n" +
                    "QS9+tW2E6PpAvai86bs2x0kT3gkj7jmQDG9NQwIDAQABAoIBABv1PbVfXLksPjSD\n" +
                    "XtxRNdQQTkq0cS1PLnfuXx3oqzgoiBUwLjV6G0WR4BkB66p1t+bzEgzkBU1zKtjq\n" +
                    "Q8zvXaR0DnVfn1E+LjlgxN2W3nUTkavag0UdjednpVp0z5xkpfAZvk9p0ofKRDPO\n" +
                    "JMSKypCl7RwcsR4uxV4LQee49AYl/7mS/NMbDzlbMxLR3VmA0Id0U0C7v4qIfMkm\n" +
                    "L8DCtw1P4QfpOY6fMqN0pKke0jPGguMYXx4F1Hcmhi+uz9tmjd02XnRe9cY8QVYE\n" +
                    "rj1H0qFijJ3bSg4DKjXWo1wJZN2PD+Z/Z/yofJBcqh8/+Q1nlZMXP3obsYZPjkD3\n" +
                    "WafwgNkCgYEA47Y3oXZoL70GWRkAMQtqAEtAWk2g4Bc8R9AZN7pHrg23gF33z8Yp\n" +
                    "EjtYxcu1Y+eLvALMbrStg0txGv3BeoApEi/rzgR2c/JUvgo9s/6cbXvGgT6NW3U7\n" +
                    "Tgpe0V8h/ZglExvy9MHKXJWutN5sQImiYfk0uKtR1/uOiJFOl9Dd+L0CgYEAkdq+\n" +
                    "S3D401MSTR4buodKZpseOlVo6SpaGdepeFY6FtGBwTlJAmcLgRfebyUkydq2zPnK\n" +
                    "kUvKQWwAQhurGn4wiMpQ+e6FuEKfkJDBcFa+jlfieswngCn2XyM2Atg3XDVj9Y/4\n" +
                    "eWx7BqpIIUR3jgSO5vjrzyoQp2SERrPzPf8Fvf8CgYBXU3EAJbWM4TPHBXRyWos7\n" +
                    "M6CpQO36Ik8Gx0J0gaatlCsUOnUnpDnp+QJxUE7u0kRfRL97kSSdnlfw3vHM9ctK\n" +
                    "Y0BOEJ4QlxVyj+Db3z/EKNyWghOZyFqG8iksqAwUAb3uFyDURmFBolGOoWHoWiAA\n" +
                    "7J4QV/saFimyK+90/y+xDQKBgHb46RR8mFs4fcst7gxe4w+DJEsM9ECNbWV7Bx/D\n" +
                    "piqKxr6oTaeKClZI9AXRVIravxXAA741BkwLHsLN8unvWQOblCXqrGS645F2onNS\n" +
                    "LqnJglIMSYQ/tlmwTRRQ7gdm/ZyGzXWuSUQMjj2krajIixBYp3EarO7+DO/nRVii\n" +
                    "tzpdAoGBAMTtRmjRdxuEcI0IiQow3ubR1h6WkstpsxBJIhxP4V5+xKiIuIDALF/4\n" +
                    "Oiz4gxG9NO9lS3Dkcx6NUHMAVJvN1GoC681m/zqstTiU7LmYhIF98Od2jbuDGaIC\n" +
                    "+8ekKRwhJB8A4Jr+7NGn9AfY+ZY2Kt16iXktYmTLtW2Oh38IKL81\n" +
                    "-----END RSA PRIVATE KEY-----"

            assignment.gitRepositoryPubKey = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCBvMfyK7kGx1fx9mGyKpBGAb8gJsna" +
                    "DLiC42W2cF5FyEicCY9R2MBstQE7AIBqLHPgXmu06+a43LaSOmqa91Pwu2hqjH4adJ2xNENUAuatJPkxLjA+8zP8Zx33Ay" +
                    "MYtggrpIqUYrc3Gcs3kFn0t6Qf++YnASoNFdWQBqtr7CD5UUSLaKMKSdWOC+6qxdvdNRm0Q+lJ2ybZJK4R4/KyRqMaVTH/" +
                    "jrgJEyxTANRb1+aRo7ka9NOQGdiQj7al1OjZtK1xse0j00OOqaeOlJAheoWqBuEvjk0inWf0AkxaPO0+Hg9Q5OVBL361bY" +
                    "To+kC9qLzpuzbHSRPeCSPuOZAMb01D"

            assignmentRepository.save(assignment)


            // connect to git repository
            this.mvc.perform(post("/assignment/setup-git/dummyAssignment2"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/assignment/info/dummyAssignment2"))
                .andExpect(
                    flash().attribute(
                        "message",
                        "Assignment was successfully created and connected to git repository"
                    )
                )

            val updatedAssignment = assignmentRepository.getById("dummyAssignment2")
            assert(updatedAssignment.active == false)

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment2").exists()) {
                File(assignmentsRootLocation, "dummyAssignment2").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_04_createAssignmentWithInvalidGitRepository() {

        val mvcResult = this.mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "dummyAssignment3")
                .param("assignmentName", "Dummy Assignment")
                .param("assignmentPackage", "org.dummy")
                .param("submissionMethod", "UPLOAD")
                .param("language", "JAVA")
                .param("gitRepositoryUrl", "git@githuu.com:someuser/cs1Assigment1.git")
        )
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("assignmentForm", "gitRepositoryUrl"))
            .andReturn()

        val result =
            mvcResult.modelAndView!!.model.get(BindingResult.MODEL_KEY_PREFIX + "assignmentForm") as BindingResult
        assertEquals(
            "Error cloning git repository. Are you sure the url is right?",
            result.getFieldError("gitRepositoryUrl")?.defaultMessage
        )

        try {
            assignmentRepository.getById("dummyAssignment3")
            fail("dummyAssignment shouldn't exist in the database")
        } catch (e: Exception) {
        }

    }

    @Test
    @DirtiesContext
    fun test_05_listAssignments() {

        val user = User("p1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        try {// list assigments should return empty
            this.mvc.perform(
                get("/assignment/my")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", emptyList<Assignment>()))

            // create assignment
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment4", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                teacherId = "p1", activateRightAfterCloning = false
            )

            // list assignments should return one assignment
            val mvcResult = this.mvc.perform(
                get("/assignment/my")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(1)))
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignments = mvcResult.modelAndView!!.modelMap["assignments"] as List<Assignment>
            val assignment = assignments[0]

            assertEquals("dummyAssignment4", assignment.id)
            assertEquals("Dummy Assignment", assignment.name)
            assertEquals("org.dummy", assignment.packageName)
            assertEquals(SubmissionMethod.UPLOAD, assignment.submissionMethod)
            assertEquals(sampleJavaAssignmentRepo, assignment.gitRepositoryUrl)
            assertEquals("p1", assignment.ownerUserId)
            assertEquals(false, assignment.active)
            assertEquals(0, assignment.numSubmissions)
            assertEquals(0, assignment.numUniqueSubmitters)
            assertNull(assignment.lastSubmissionDate)
            assertEquals(AssignmentVisibility.ONLY_BY_LINK, assignment.visibility)

        } finally {
            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment4").exists()) {
                File(assignmentsRootLocation, "dummyAssignment4").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_06_createNewAssignmentAndForgetToConnectWithGithub() {  // assignment should be marked as inactive

        // post form
        this.mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "dummyAssignment5")
                .param("assignmentName", "Dummy Assignment")
                .param("assignmentPackage", "org.dummy")
                .param("submissionMethod", "UPLOAD")
                .param("language", "JAVA")
                .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
        )
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "/assignment/setup-git/dummyAssignment5"))


        // get assignment detail
        this.mvc.perform(get("/assignment/setup-git/dummyAssignment5"))
            .andExpect(status().isOk)

        val assignment = assignmentRepository.getById("dummyAssignment5")
        assert(assignment.active == false)
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_07_showOnlyActiveAssignments() {

        try {
            // create an assignment with white-list. it will start as inactive
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment6", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                assignees = "21800000"
            )

            // login as 21800000 and get an empty list of assignments
            val student = User("21800000", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
            this.mvc.perform(
                get("/")
                    .with(user(student))
            )
                .andExpect(status().isOk)
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(0)))

            // mark the assignment as active
            this.mvc.perform(get("/assignment/toggle-status/dummyAssignment6"))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/assignment/my"))
                .andExpect(flash().attribute("message", "Assignment was marked active"))

            // login again as 21800000 and get a redirect to the assignment
            this.mvc.perform(
                get("/")
                    .with(user(student))
            )
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/upload/dummyAssignment6"))

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment6").exists()) {
                File(assignmentsRootLocation, "dummyAssignment6").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser(username = "teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_08_createAssignmentWithOtherTeachers() {

        try {

            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment7", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                acl = "p1000, p1001"
            )

            // get assignment detail
            val mvcResult = this.mvc.perform(get("/assignment/info/dummyAssignment7"))
                .andExpect(status().isOk)
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val aclList = mvcResult.modelAndView!!.modelMap["acl"] as List<AssignmentACL>
            assertEquals(2, aclList.size)
            assertEquals("p1000", aclList[0].userId)
            assertEquals("p1001", aclList[1].userId)

            // accessing "/assignments/my" with p1000 should give one assignment
            val user = User("p1000", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))
            this.mvc.perform(
                get("/assignment/my")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk)
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(1)))

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment7").exists()) {
                File(assignmentsRootLocation, "dummyAssignment7").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_09_createNewAssignmentAndEdit() {

        try {
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment8", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo
            )

            // get edit form
            this.mvc.perform(get("/assignment/edit/dummyAssignment8"))
                .andExpect(status().isOk)
                .andExpect(model().hasNoErrors())
                .andExpect(
                    model().attribute(
                        "assignmentForm",
                        AssignmentForm(
                            assignmentId = "dummyAssignment8",
                            assignmentName = "Dummy Assignment",
                            assignmentPackage = "org.dummy",
                            submissionMethod = SubmissionMethod.UPLOAD,
                            language = Language.JAVA,
                            gitRepositoryUrl = sampleJavaAssignmentRepo,
                            hiddenTestsVisibility = TestVisibility.SHOW_PROGRESS,
                            editMode = true,
                            assignmentTags = "",
                            visibility = AssignmentVisibility.ONLY_BY_LINK
                        )
                    )
                )

            // post a change
            mvc.perform(
                post("/assignment/new")
                    .param("assignmentId", "dummyAssignment8")
                    .param("assignmentName", "New Name")
                    .param("editMode", "true")
                    .param("submissionMethod", "UPLOAD")
                    .param("language", "JAVA")
                    .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
                    .param("leaderboardType", "ELLAPSED")
                    .param("visibility", "PUBLIC")
            )
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/assignment/info/dummyAssignment8"))

            // get edit form again
            this.mvc.perform(get("/assignment/edit/dummyAssignment8"))
                .andExpect(status().isOk)
                .andExpect(model().hasNoErrors())
                .andExpect(
                    model().attribute(
                        "assignmentForm",
                        AssignmentForm(
                            assignmentId = "dummyAssignment8",
                            assignmentName = "New Name",
                            submissionMethod = SubmissionMethod.UPLOAD,
                            language = Language.JAVA,
                            gitRepositoryUrl = sampleJavaAssignmentRepo,
                            editMode = true,
                            leaderboardType = LeaderboardType.ELLAPSED,
                            assignmentTags = "",
                            visibility = AssignmentVisibility.PUBLIC
                        )
                    )
                )


        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment8").exists()) {
                File(assignmentsRootLocation, "dummyAssignment8").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_10_checkAssignmentHasNoErrors() {

        // create initial assignment
        val assignment01 = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = false, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProj", hiddenTestsVisibility = TestVisibility.HIDE_EVERYTHING
        )
        assignmentRepository.save(assignment01)

        // toggle status
        this.mvc.perform(get("/assignment/toggle-status/testJavaProj"))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/my"))
            .andExpect(flash().attribute("message", "Assignment was marked active"))

        // confirm it is now active
        val assignment = assignmentRepository.getById("testJavaProj")
        assertTrue("assignment is not active", assignment.active)
    }


    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_11_getAssignmentInfo() {

        // create initial assignment
        val assignment = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = false, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProj", tagsStr = emptyList()
        )
        assignmentRepository.save(assignment)
        assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student1"))

        this.mvc.perform(get("/assignment/info/testJavaProj"))
            .andExpect(status().isOk)
            .andExpect(view().name("assignment-detail"))
            .andExpect(model().hasNoErrors())
            .andExpect(model().attribute("assignment", assignment))
            .andExpect(content().string(containsString(assignment.id)))

    }

    @Test
    @DirtiesContext
    fun test_12_deleteAssignment() {

        val STUDENT_1 = User("student1", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
        val TEACHER_1 = User("teacher1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        // make a copy of the "testJavaProj" assignment files and create an assignment based on the copy
        // so that we can safely delete it, without affecting the original files
        val assignmentFolder = File(assignmentsRootLocation, "testJavaProjForDelete")
        FileUtils.copyDirectory(File(assignmentsRootLocation, "testJavaProj"), assignmentFolder)

        // create initial assignment
        val assignment = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProjForDelete"
        )
        assignmentRepository.save(assignment)
        assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student1"))


        // make a submission
        val submissionId =
            testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1).toLong()

        // try to delete the assignment but DP will issue an error since it has submissions
        this.mvc.perform(
            post("/assignment/delete/testJavaProj")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/my"))
            .andExpect(flash().attribute("error", "Assignment can't be deleted because it has submissions"))

        // remove the submission
        submissionRepository.deleteById(submissionId)

        // try to delete the assignment again, this time with success
        this.mvc.perform(
            post("/assignment/delete/testJavaProj")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/my"))
            .andExpect(flash().attribute("message", "Assignment was successfully deleted"))

        // check if the assignment folder was also deleted
        assertFalse("$assignmentFolder should have been deleted", assignmentFolder.exists())
    }

    @Test
    @DirtiesContext
    fun test_12_1_deleteAssignmentWithOtherAssignments() {

        val TEACHER_1 = User("teacher1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        // make a copy of the "testJavaProj" assignment files and create an assignment based on the copy
        // so that we can safely delete it, without affecting the original files
        val assignmentFolder = File(assignmentsRootLocation, "testJavaProjForDelete")
        FileUtils.copyDirectory(File(assignmentsRootLocation, "testJavaProj"), assignmentFolder)

        // create two initial assignments
        val assignment1 = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProjForDelete"
        )
        assignmentService.addTagToAssignment(assignment1, "teste")
        assignmentRepository.save(assignment1)
        assigneeRepository.save(Assignee(assignmentId = assignment1.id, authorUserId = "student1"))

        // create two initial assignments
        val assignment2 = Assignment(
            id = "testJavaProj2", name = "Test Project (for automatic tests)",
            packageName = "org.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProjForDelete"
        )
        assignmentService.addTagToAssignment(assignment2, "teste")
        assignmentRepository.save(assignment2)
        assigneeRepository.save(Assignee(assignmentId = assignment2.id, authorUserId = "student1"))


        // delete the assignment 1
        this.mvc.perform(
            post("/assignment/delete/testJavaProj")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/my"))
            .andExpect(flash().attribute("message", "Assignment was successfully deleted"))

        // check my assignments
        this.mvc.perform(
            get("/assignment/my")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isOk)
            .andExpect(model().hasNoErrors())
            .andExpect(model().attribute("assignments", hasSize<Assignment>(1)))

    }

    @Test
    @DirtiesContext
    fun test_12_2_deleteAssignmentWithForce() {

        val STUDENT_1 = User("student1", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
        val TEACHER_1 = User("teacher1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        // make a copy of the "testJavaProj" assignment files and create an assignment based on the copy
        // so that we can safely delete it, without affecting the original files
        val assignmentFolder = File(assignmentsRootLocation, "testJavaProjForDelete")
        FileUtils.copyDirectory(File(assignmentsRootLocation, "testJavaProj"), assignmentFolder)

        // create initial assignment
        val assignment = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProjForDelete"
        )
        assignmentRepository.save(assignment)
        assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student1"))


        // make a submission
        val submissionId =
            testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "testJavaProj", STUDENT_1).toLong()
        val submission = submissionRepository.getById(submissionId)

        // try to delete the assignment with force = true using someone who hasn't the admin role
        this.mvc.perform(
            post("/assignment/delete/testJavaProj")
                .param("force", "true")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isForbidden)

        // try to delete the assignment with force = true using someone who has the admin role
        this.mvc.perform(
            post("/assignment/delete/testJavaProj")
                .param("force", "true")
                .with(user(User("admin", "", mutableListOf(SimpleGrantedAuthority("ROLE_DROP_PROJECT_ADMIN")))))
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/my"))
            .andExpect(flash().attribute("message", "Assignment was successfully deleted"))

        // check if the assignment folder was deleted
        assertFalse("$assignmentFolder should have been deleted", assignmentFolder.exists())

        // check if the submission folder was deleted
        val submissionFolder = File(uploadSubmissionsRootLocation, submission.submissionFolder)
        assertFalse("$submissionFolder should have been deleted", submissionFolder.exists())

        // check if the submission was deleted from the database
        assertTrue("$submissionId should have been deleted from the DB", submissionRepository.findById(submissionId).isEmpty)
    }

    @Test
    @DirtiesContext
    fun test_12_3_deleteAssignmentAndCheckRepositories() {
        val STUDENT_1 = User("student1", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
        val TEACHER_1 = User("teacher1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        // make a copy of the "testJavaProj" assignment files and create an assignment based on the copy
        // so that we can safely delete it, without affecting the original files
        val assignmentFolder = File(assignmentsRootLocation, "testJavaProjForDelete")
        FileUtils.copyDirectory(File(assignmentsRootLocation, "testJavaProj"), assignmentFolder)

        // create initial assignment
        val assignment = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProjForDelete"
        )
        assignmentRepository.save(assignment)
        assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student1"))

        //check if assignment is in Assignee Repository
        assertTrue(
            "${assignment.id} should be in assignee repository",
            assigneeRepository.findByAuthorUserId(STUDENT_1.username).isNotEmpty()
        )

        //check if assignment is in assignment Repository
        assertTrue(
            "${assignment.id} should be in assignment repository",
            assignmentRepository.existsById(assignment.id)
        )

        // succeed on deleting the assignment
        this.mvc.perform(
            post("/assignment/delete/testJavaProj")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/assignment/my"))
            .andExpect(flash().attribute("message", "Assignment was successfully deleted"))

        //check if assignment was deleted in Assignee Repository
        assertFalse(
            "${assignment.id} should have been deleted from assignee repository",
            assigneeRepository.existsByAssignmentId(assignment.id)
        )
        assertTrue(
            "${assignment.id} should have been deleted from assignee repository",
            assigneeRepository.findByAuthorUserId(STUDENT_1.username).isEmpty()
        )

        //check if assignment was deleted in assignment Repository
        assertFalse(
            "${assignment.id} should have been deleted from assignment repository",
            assignmentRepository.existsById(assignment.id)
        )
    }

    @Test
    @DirtiesContext
    fun test_13_listArchivedAssignments() {

        val user = User("p1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        try {// list archived assignments should return empty
            this.mvc.perform(
                get("/assignment/archived")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", emptyList<Assignment>()))

            // create assignment
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment4", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                teacherId = "p1", activateRightAfterCloning = false
            )

            // list archived assignments should still return empty
            this.mvc.perform(
                get("/assignment/archived")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", emptyList<Assignment>()))

            // archive assignment
            this.mvc.perform(
                post("/assignment/archive/dummyAssignment4")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/assignment/my"))
                .andExpect(
                    flash().attribute(
                        "message",
                        "Assignment was archived. You can now find it in the Archived assignments page"
                    )
                )

            // list archived assignments should now return 1 assignment
            val mvcResult = this.mvc.perform(
                get("/assignment/archived")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(1)))
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignments = mvcResult.modelAndView!!.modelMap["assignments"] as List<Assignment>
            val assignment = assignments[0]

            assertEquals("dummyAssignment4", assignment.id)


        } finally {
            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment4").exists()) {
                File(assignmentsRootLocation, "dummyAssignment4").deleteRecursively()
            }
        }
    }

    // refreshAssignmentGitRepository
    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_14_refreshAssignmentGitRepository() {

        try {
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment1", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo
            )

            val contentString = this.mvc.perform(post("/assignment/refresh-git/dummyAssignment1"))
                .andExpect(status().isOk)
                .andReturn().response.contentAsString

            val contentJSON = JSONObject(contentString)
            assertEquals(true, contentJSON.getBoolean("success"))

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment1").exists()) {
                File(assignmentsRootLocation, "dummyAssignment1").deleteRecursively()
            }
        }
    }

    @Test
    @DirtiesContext
    fun test_15_markAllAsFinal() {

        val assignmentId = testsHelper.defaultAssignmentId

        // create assignment
        val assignment01 = Assignment(
            id = assignmentId, name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = testsHelper.TEACHER_1.username,
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProj"
        )
        assignmentRepository.save(assignment01)

        // make several submissions for that assignment
        testsHelper.makeSeveralSubmissions(
            listOf(
                "projectInvalidStructure1",
                "projectInvalidStructure1",
                "projectInvalidStructure1",
                "projectInvalidStructure1"
            ), mvc
        )

        // mark all as final
        this.mvc.perform(
            post("/assignment/markAllAsFinal/${assignmentId}")
                .with(user(testsHelper.TEACHER_1))
        )
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "/report/${assignmentId}"))

        // check results
        val reportResult = this.mvc.perform(
            get("/report/testJavaProj")
                .with(user(testsHelper.TEACHER_1))
        )
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val report = reportResult.modelAndView!!.modelMap["submissions"] as List<SubmissionInfo>
        report.forEach {
            assertTrue(it.lastSubmission.markedAsFinal)
        }

    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_16_createNewAssignmentWithTags() {

        // check available tags
        // it shouldn't exist none
        var globalTags = assignmentTagRepository.findAll()
        assertEquals(0, globalTags.size)

        // post form
        this.mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "dummyAssignmentTags")
                .param("assignmentName", "Dummy Assignment")
                .param("assignmentPackage", "org.dummy")
                .param("submissionMethod", "UPLOAD")
                .param("language", "JAVA")
                .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
                .param("assignmentTags", "sample,test,simple")
        )
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "/assignment/setup-git/dummyAssignmentTags"))

        // get assignment detail
        val mvcResult = this.mvc.perform(get("/assignment/info/dummyAssignmentTags"))
            .andExpect(status().isOk)
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val assignment = mvcResult.modelAndView!!.modelMap["assignment"] as Assignment
        val tagNames = assignment.tagsStr
        org.hamcrest.MatcherAssert.assertThat(tagNames, containsInAnyOrder("sample", "test", "simple"))

        // check available tags
        // it should now return 'sample','test','simple'
        globalTags = assignmentTagRepository.findAll()
        assertEquals(3, globalTags.size)
        org.hamcrest.MatcherAssert.assertThat(tagNames, containsInAnyOrder("sample", "test", "simple"))
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_17_updateAssignmentWithTags() {

        try {
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignmentTags", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                tags = "sample,test,simple"  // <<<<
            )

            // add one tag and remove 2 tags
            mvc.perform(
                post("/assignment/new")
                    .param("assignmentId", "dummyAssignmentTags")
                    .param("assignmentName", "Dummy Assignment")
                    .param("assignmentPackage", "org.dummy")
                    .param("submissionMethod", "UPLOAD")
                    .param("language", "JAVA")
                    .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
                    .param("editMode", "true")
                    .param("assignmentTags", "sample,complex") // <<<<
            )
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/assignment/info/dummyAssignmentTags"))

            // get assignment detail
            val mvcResult = this.mvc.perform(get("/assignment/info/dummyAssignmentTags"))
                .andExpect(status().isOk)
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignment = mvcResult.modelAndView!!.modelMap["assignment"] as Assignment
            val tagNames = assignment.tagsStr
            assertEquals(2, tagNames?.size)
            org.hamcrest.MatcherAssert.assertThat(tagNames, containsInAnyOrder("sample", "complex"))

            // check available tags
            // it should now return 'sample','complex'
            val globalTags = assignmentTagRepository.findAll().map { it.name }
            assertEquals(4, globalTags.size)
            assertThat(globalTags, Matchers.containsInAnyOrder("sample", "complex", "test", "simple"))

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignmentTags").exists()) {
                File(assignmentsRootLocation, "dummyAssignmentTags").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_18_createNewAssignmentAndInfoWithTestMethods() {

        try {
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignmentTests", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo
            )

            val mvcResult = this.mvc.perform(get("/assignment/info/dummyAssignmentTests"))
                .andExpect(status().isOk)
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val testMethods = mvcResult.modelAndView!!.modelMap["tests"] as List<AssignmentTestMethod>
            assertEquals(4, testMethods.size)
            assertThat(
                testMethods.map { it.testMethod },
                contains("test_001_FindMax", "test_002_FindMaxAllNegative", "test_003_FindMaxNegativeAndPositive", "test_004_FindMaxWithNull")
            )

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignmentTests").exists()) {
                File(assignmentsRootLocation, "dummyAssignmentTests").deleteRecursively()
            }
        }
    }

    @Test
    @DirtiesContext
    fun test_19_listAssignmentsFilteredByTags() {

        val user = User("p1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        try {// list assigments should return empty
            this.mvc.perform(
                get("/assignment/my")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", emptyList<Assignment>()))

            // create two assignments
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment4", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                teacherId = "p1", activateRightAfterCloning = false, tags = "sample,test"
            )

            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment5", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                teacherId = "p1", activateRightAfterCloning = false, tags = "other,test"
            )

            // list assignments filtered by tag "sample" should return one assignment
            this.mvc.perform(
                get("/assignment/my?tags=sample")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(1)))

            // list assignments filtered by tag "notexistent" should return zero assignments
            this.mvc.perform(
                get("/assignment/my?tags=notexistent")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(0)))

            // list assignments filtered by tag "test" should return two assignments
            this.mvc.perform(
                get("/assignment/my?tags=test")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(2)))

            // list assignments filtered by tag "sample,other" should return zero assignments
            this.mvc.perform(
                get("/assignment/my?tags=sample,other")
                    .with(SecurityMockMvcRequestPostProcessors.user(user))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(0)))

        } finally {
            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment4").exists()) {
                File(assignmentsRootLocation, "dummyAssignment4").deleteRecursively()
            }

            if (File(assignmentsRootLocation, "dummyAssignment5").exists()) {
                File(assignmentsRootLocation, "dummyAssignment5").deleteRecursively()
            }
        }
    }

    @Test
    @DirtiesContext
    fun test_20_exportAssignment() {

        try {
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment1", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                dueDate = "2022-10-31T01:30:00.000-00:00"
            )


            val result = this.mvc.perform(
                get("/assignment/export/dummyAssignment1?includeSubmissions=false")
                    .with(user(TEACHER_1))
            )
                .andExpect(status().isFound)
                .andReturn()

            val redirectLocation = result.response.getHeader("Location")
            kotlin.test.assertNotNull(redirectLocation)

            val result2 = this.mvc.perform(
                get(redirectLocation)
                    .with(user(TEACHER_1))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
            )
                .andExpect(
                    header().string(
                        "Content-Disposition",
                        "attachment; filename=dummyAssignment1_${Date().formatJustDate()}.dp"
                    )
                )
                .andExpect(status().isOk)
                .andReturn()

            val downloadedFileContent = result2.response.contentAsByteArray
            val downloadedZipFile = File("result.zip")
            val downloadedJSONFileName = File("result/assignment.json")
            FileUtils.writeByteArrayToFile(downloadedZipFile, downloadedFileContent)
            val downloadedFileAsZipObject = ZipFile(downloadedZipFile)
            downloadedFileAsZipObject.extractFile("assignment.json", "result")

            val mapper = ObjectMapper().registerModule(KotlinModule())
            val node = mapper.readTree(downloadedJSONFileName)
            assertEquals("dummyAssignment1", node.at("/id").asText())
            assertEquals("Dummy Assignment", node.at("/name").asText())
            assertEquals("org.dummy", node.at("/packageName").asText())
            assertEquals("2022-10-31 01:30:00", node.at("/dueDate").asText())
            assertEquals("UPLOAD", node.at("/submissionMethod").asText())
            assertEquals("JAVA", node.at("/language").asText())
            assertEquals("SHOW_PROGRESS", node.at("/hiddenTestsVisibility").asText())
            assertFalse(node.at("/acceptsStudentTests").asBoolean())
            assertEquals(sampleJavaAssignmentRepo, node.at("/gitRepositoryUrl").asText())
            assertEquals(TestsHelper.sampleJavaAssignmentPublicKey, node.at("/gitRepositoryPubKey").asText())
            assertEquals(TestsHelper.sampleJavaAssignmentPrivateKey, node.at("/gitRepositoryPrivKey").asText())
            assertEquals("dummyAssignment1", node.at("/gitRepositoryFolder").asText())

            downloadedZipFile.delete()
            downloadedJSONFileName.delete()

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment1").exists()) {
                File(assignmentsRootLocation, "dummyAssignment1").deleteRecursively()
            }
        }
    }

    @Test
    @DirtiesContext
    fun test_21_importAssignmentOnly() {

        try {
            val fileContent = File("src/test/sampleExports/export-only-assignment.dp").readBytes()
            val multipartFile =
                MockMultipartFile("file", "export-only-assignment.dp", "application/zip", fileContent)

            mvc.perform(
                MockMvcRequestBuilders.fileUpload("/assignment/import")
                    .file(multipartFile)
                    .with(user(TEACHER_1))
            )
                .andExpect(status().isFound())
                .andExpect(flash().attribute("message", "Imported successfully dummyAssignment1. Submissions were not imported"))
                .andExpect(header().string("Location", "/assignment/info/dummyAssignment1"))


            // let's check if it was well imported
            val mvcResult = this.mvc.perform(get("/assignment/info/dummyAssignment1"))
                .andExpect(status().isOk)
                .andReturn()

            val assignment = mvcResult.modelAndView!!.model["assignment"] as Assignment
            assertEquals("dummyAssignment1", assignment.id)
            assertEquals("teacher1", assignment.ownerUserId)
            mvcResult.modelAndView!!.model["tests"]
            mvcResult.modelAndView!!.model["report"]

        } finally {
            // remove the assignments files created during the test
            File(assignmentsRootLocation, "dummyAssignment1").deleteRecursively()
        }
    }

    @Test
    @DirtiesContext
    fun test_22_exportAssignmentAndSubmissions() {

        val assignment01 = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProj"
        )
        assignmentRepository.save(assignment01)

        testsHelper.makeSeveralSubmissions(
            listOf(
                "projectInvalidStructure1",
                "projectInvalidStructure1",
                "projectOK",
                "projectInvalidStructure1"
            ), mvc
        )

        val result = this.mvc.perform(
            get("/assignment/export/testJavaProj?includeSubmissions=true")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isFound)
            .andReturn()

        val redirectLocation = result.response.getHeader("Location")
        kotlin.test.assertNotNull(redirectLocation)

        val result2 = this.mvc.perform(
            get(redirectLocation)
                .with(user(TEACHER_1))
                .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
        )
            .andExpect(
                header().string(
                    "Content-Disposition",
                    "attachment; filename=testJavaProj_${Date().formatJustDate()}.dp"
                )
            )
            .andExpect(status().isOk)
            .andReturn()

        val downloadedFileContent = result2.response.contentAsByteArray
        val downloadedZipFile = File("result.zip")
        val downloadedJSONFileName = File("result/submissions.json")
        FileUtils.writeByteArrayToFile(downloadedZipFile, downloadedFileContent)
        val downloadedFileAsZipObject = ZipFile(downloadedZipFile)
        downloadedFileAsZipObject.extractFile("submissions.json", "result")

        val mapper = ObjectMapper().registerModule(KotlinModule())
        val node = mapper.readTree(downloadedJSONFileName)
        assertEquals("testJavaProj", node.at("/0/assignmentId").asText())
        assertEquals("student1", node.at("/0/submitterUserId").asText())
        assertEquals("V", node.at("/0/status").asText())
        assertTrue(node.at("/0/buildReport").isNull)
        assertEquals("student1", node.at("/0/authors/0/userId").asText())
        assertEquals("PS", node.at("/0/submissionReport/0/key").asText())
        assertEquals("NOK", node.at("/0/submissionReport/0/value").asText())

        assertEquals("student2", node.at("/1/submitterUserId").asText())
        assertEquals("student2", node.at("/1/authors/0/userId").asText())

        assertFalse(node.at("/2/buildReport").isNull)
        assertEquals("PS", node.at("/2/submissionReport/0/key").asText())
        assertEquals("OK", node.at("/2/submissionReport/0/value").asText())
        assertEquals("C", node.at("/2/submissionReport/1/key").asText())
        assertEquals("OK", node.at("/2/submissionReport/1/value").asText())
        assertEquals("TT", node.at("/2/submissionReport/3/key").asText())
        assertEquals("OK", node.at("/2/submissionReport/3/value").asText())
        assertEquals(2, node.at("/2/submissionReport/3/progress").asInt())
        assertEquals(2, node.at("/2/submissionReport/3/goal").asInt())

        val junitReportFileNames = node.at("/2/junitReports").elements().asSequence().toList().map { it.get("filename").textValue() }

        assertThat(
            junitReportFileNames,
            hasItems(
                "TEST-org.dropProject.sampleAssignments.testProj.TestTeacherProject.xml",
                "TEST-org.dropProject.sampleAssignments.testProj.TestTeacherHiddenProject.xml"
            )
        )

        assertEquals("student4", node.at("/3/authors/0/userId").asText())
        assertEquals("student5", node.at("/3/authors/1/userId").asText())

        val fileHeaders = downloadedFileAsZipObject.fileHeaders as List<FileHeader>
        assertEquals(9, fileHeaders.size)
        // TODO: Think of a way to make these tests independent of the headers order
//        assertThat(fileHeaders[3].fileName, matchesPattern("original/testJavaProj/[0-9][0-9]-[0-9][0-9]/"))
//        for (i in 4..7) {
//            assertTrue(fileHeaders[i].fileName.endsWith(".zip"))
//        }

        downloadedZipFile.delete()
        downloadedJSONFileName.delete()

    }

    @Test
    @DirtiesContext
    fun test_23_importAssignmentAndSubmissions() {

        try {
            val fileContent = File("src/test/sampleExports/export-assignment-and-submissions.dp").readBytes()
            val multipartFile =
                MockMultipartFile("file", "export-assignment-and-submissions.dp", "application/zip", fileContent)

            mvc.perform(
                MockMvcRequestBuilders.fileUpload("/assignment/import")
                    .file(multipartFile)
                    .with(user(TEACHER_1))
            )
                .andExpect(status().isFound)
                .andExpect(flash().attribute("message", "Imported successfully dummyAssignment1 and all its submissions"))
                .andExpect(header().string("Location", "/report/dummyAssignment1"))

            val reportResult = this.mvc.perform(
                get("/report/dummyAssignment1")
                    .with(user(TEACHER_1))
            )
                .andExpect(status().isOk())
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val report = reportResult.modelAndView!!.modelMap["submissions"] as List<SubmissionInfo>
            assertEquals(4, report.size)

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment1").exists()) {
                File(assignmentsRootLocation, "dummyAssignment1").deleteRecursively()
            }
        }


    }

    @Test
    @DirtiesContext
    fun test_24_exportAssignmentAndGitSubmissions() {

        val assignment01 = Assignment(
            id = "testJavaProj", name = "Test Project (for automatic tests)",
            packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.GIT, active = true, gitRepositoryUrl = "git://dummyRepo",
            gitRepositoryFolder = "testJavaProj"
        )
        assignmentRepository.save(assignment01)

        testsHelper.connectToGitRepositoryAndBuildReport(
            mvc, gitSubmissionRepository, "testJavaProj",
            "git@github.com:drop-project-edu/sampleJavaSubmission.git", "student1"
        )

        val result = this.mvc.perform(
            get("/assignment/export/testJavaProj?includeSubmissions=true")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isFound)
            .andReturn()

        val redirectLocation = result.response.getHeader("Location")
        kotlin.test.assertNotNull(redirectLocation)

        val result2 = this.mvc.perform(
            get(redirectLocation)
                .with(user(TEACHER_1))
                .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
        )
            .andExpect(
                header().string(
                    "Content-Disposition",
                    "attachment; filename=testJavaProj_${Date().formatJustDate()}.dp"
                )
            )
            .andExpect(status().isOk)
            .andReturn()

        val downloadedFileContent = result2.response.contentAsByteArray
        val downloadedZipFile = File("result.zip")
        val downloadedJSONFileName = File("result/git-submissions.json")
        FileUtils.writeByteArrayToFile(downloadedZipFile, downloadedFileContent)
        val downloadedFileAsZipObject = ZipFile(downloadedZipFile)
        downloadedFileAsZipObject.extractFile("git-submissions.json", "result")

        val mapper = ObjectMapper().registerModule(KotlinModule())
        val node = mapper.readTree(downloadedJSONFileName)
        assertEquals("testJavaProj", node.at("/0/assignmentId").asText())
        assertEquals("student1", node.at("/0/submitterUserId").asText())
        assertEquals("2019-02-26 17:26:53", node.at("/0/lastCommitDate").asText())
        assertEquals("git@github.com:drop-project-edu/sampleJavaSubmission.git", node.at("/0/gitRepositoryUrl").asText())
        assertEquals("student1", node.at("/0/authors/0/userId").asText())
        assertEquals("student2", node.at("/0/authors/1/userId").asText())

        val fileHeaders = downloadedFileAsZipObject.fileHeaders as List<FileHeader>
        assertEquals(41, fileHeaders.size)
        // use a regex because the timestamp (mutable) is part of the name
//        assertThat(fileHeaders[3].fileName, matchesPattern("original/testJavaProj/[0-9][0-9]-[0-9][0-9]/"))
//        assertThat(fileHeaders[4].fileName, matchesPattern("original/testJavaProj/[0-9][0-9]-[0-9][0-9]/[0-9]+-sampleJavaSubmission/"))

        downloadedZipFile.delete()
        downloadedJSONFileName.delete()
    }

    @Test
    @DirtiesContext
    fun test_25_importAssignmentAndGitSubmissions() {

        try {
            val fileContent = File("src/test/sampleExports/export-assignment-and-git-submissions.dp").readBytes()
            val multipartFile =
                MockMultipartFile("file", "export-assignment-and-git-submissions.dp", "application/zip", fileContent)

            mvc.perform(
                MockMvcRequestBuilders.fileUpload("/assignment/import")
                    .file(multipartFile)
                    .with(user(TEACHER_1))
            )
                .andExpect(status().isFound)
                .andExpect(flash().attribute("message", "Imported successfully dummyAssignment1 and all its submissions"))
                .andExpect(header().string("Location", "/report/dummyAssignment1"))

            val reportResult = this.mvc.perform(
                get("/report/dummyAssignment1")
                    .with(user(TEACHER_1))
            )
                .andExpect(status().isOk())
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val report = reportResult.modelAndView!!.modelMap["submissions"] as List<SubmissionInfo>
            assertEquals(4, report.size)

            // TODO check git submission

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment1").exists()) {
                File(assignmentsRootLocation, "dummyAssignment1").deleteRecursively()
            }
        }


    }

    @Test
    @DirtiesContext
    fun test_26_reconnectAssignment() {

        try {
            // create assignment, properly connected to git (sampleJavaAssignmentRepo)
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment", "Dummy Assignment",
                "org.dummy", "UPLOAD", sampleJavaAssignmentRepo,
                teacherId = "p1", activateRightAfterCloning = false
            )

            // remove the private and public keys to mess up the connection with github
            val assignment = assignmentRepository.getById("dummyAssignment")
            assignment.gitRepositoryPrivKey = null
            assignment.gitRepositoryPubKey = null
            assignmentRepository.save(assignment)

            // test git refresh - it should fail
            val contentString = this.mvc.perform(post("/assignment/refresh-git/dummyAssignment"))
                .andExpect(status().isInternalServerError)
                .andReturn().response.contentAsString
            val contentJSON = JSONObject(contentString)
            assertEquals("Error pulling from git@github.com:drop-project-edu/sampleJavaAssignment.git", contentJSON.getString("error"))

            // reconnect assignment (step 1) - open page with the newly generated key
            this.mvc.perform(get("/assignment/setup-git/dummyAssignment?reconnect=true"))
                .andExpect(status().isOk)

            // now force the keys to be equal to the ones previously created in github
            assignment.gitRepositoryPrivKey = sampleJavaAssignmentPrivateKey
            assignment.gitRepositoryPubKey = sampleJavaAssignmentPublicKey
            assignmentRepository.save(assignment)

            // reconnect assignment (step 2) - open page with the newly generated key
            this.mvc.perform(post("/assignment/setup-git/dummyAssignment?reconnect=true"))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/assignment/info/dummyAssignment"))
                .andExpect(
                    flash().attribute(
                        "message",
                        "Assignment was successfully reconnected with git repository"
                    )
                )

        } finally {
            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment").exists()) {
                File(assignmentsRootLocation, "dummyAssignment").deleteRecursively()
            }
        }

    }


    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_27_createNewAssignmentAndInfoWithTestMethodsWithJUnit5() {

        try {
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignmentTests", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentWithJUnit5Repo
            )

            val mvcResult = this.mvc.perform(get("/assignment/info/dummyAssignmentTests"))
                .andExpect(status().isOk)
                .andReturn()


            @Suppress("UNCHECKED_CAST")
            val report = mvcResult.modelAndView!!.modelMap["report"] as List<AssignmentReport>
            assertEquals(6, report.size)
            assertEquals("Assignment has a pom.xml", report[0].message)
            assertEquals("Doesn't use the 'dropProject.currentUserId' system property", report[1].message)
            assertEquals("POM file is prepared to prevent stacktrace trimming on junit errors", report[2].message)
            assertEquals("Found 1 test classes", report[3].message)
            assertEquals("You have defined a global timeout for the test methods.", report[4].message)
            assertEquals("You are using a recent version of checkstyle.", report[5].message)

            @Suppress("UNCHECKED_CAST")
            val testMethods = mvcResult.modelAndView!!.modelMap["tests"] as List<AssignmentTestMethod>
            assertEquals(4, testMethods.size)
            assertThat(
                testMethods.map { it.testMethod },
                contains("test_001_FindMax", "test_002_FindMaxAllNegative", "test_003_FindMaxNegativeAndPositive", "test_004_FindMaxWithNull")
            )

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignmentTests").exists()) {
                File(assignmentsRootLocation, "dummyAssignmentTests").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_28_createAssignmentWithProjectGroupRestrictions() {

        try {

            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment7", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                minGroupSize = "2",
                maxGroupSize = "2",
                exceptions = "student3,\n   student4"
            )

            // get assignment detail
            val mvcResult = this.mvc.perform(get("/assignment/info/dummyAssignment7"))
                .andExpect(status().isOk)
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignment = mvcResult.modelAndView!!.modelMap["assignment"] as Assignment
            assertNotNull(assignment.projectGroupRestrictions)
            assertEquals(2, assignment.projectGroupRestrictions?.minGroupSize)
            assertEquals(2, assignment.projectGroupRestrictions?.maxGroupSize)
            assertEquals("student3,student4", assignment.projectGroupRestrictions?.exceptions)

            // get edit form
            this.mvc.perform(get("/assignment/edit/dummyAssignment7"))
                .andExpect(status().isOk)
                .andExpect(model().hasNoErrors())
                .andExpect(
                    model().attribute(
                        "assignmentForm",
                        AssignmentForm(
                            assignmentId = "dummyAssignment7",
                            assignmentName = "Dummy Assignment",
                            assignmentPackage = "org.dummy",
                            submissionMethod = SubmissionMethod.UPLOAD,
                            language = Language.JAVA,
                            gitRepositoryUrl = sampleJavaAssignmentRepo,
                            hiddenTestsVisibility = TestVisibility.SHOW_PROGRESS,
                            editMode = true,
                            assignmentTags = "",
                            minGroupSize = 2,
                            maxGroupSize = 2,
                            exceptions = "student3,\nstudent4",
                        )
                    )
                )

            // post a change
            mvc.perform(
                post("/assignment/new")
                    .param("assignmentId", "dummyAssignment7")
                    .param("assignmentName", "New Name")
                    .param("editMode", "true")
                    .param("submissionMethod", "UPLOAD")
                    .param("language", "JAVA")
                    .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
                    .param("minGroupSize", "1")
                    .param("maxGroupSize", "2")
                    .param("exceptions", "student3,student4,student5")
            )
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/assignment/info/dummyAssignment7"))

            // get assignment detail
            val mvcResult2 = this.mvc.perform(get("/assignment/info/dummyAssignment7"))
                .andExpect(status().isOk)
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignment2 = mvcResult2.modelAndView!!.modelMap["assignment"] as Assignment
            assertNotNull(assignment2.projectGroupRestrictions)
            assertEquals(1, assignment2.projectGroupRestrictions?.minGroupSize)
            assertEquals(2, assignment2.projectGroupRestrictions?.maxGroupSize)
            assertEquals("student3,student4,student5", assignment2.projectGroupRestrictions?.exceptions)

            // post another change
            mvc.perform(
                post("/assignment/new")
                    .param("assignmentId", "dummyAssignment7")
                    .param("assignmentName", "New Name")
                    .param("editMode", "true")
                    .param("submissionMethod", "UPLOAD")
                    .param("language", "JAVA")
                    .param("gitRepositoryUrl", sampleJavaAssignmentRepo)
                // minGroupSize and the others no longer exist
            )
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/assignment/info/dummyAssignment7"))

            // get assignment detail
            val mvcResult3 = this.mvc.perform(get("/assignment/info/dummyAssignment7"))
                .andExpect(status().isOk)
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignment3 = mvcResult3.modelAndView!!.modelMap["assignment"] as Assignment
            assertNull(assignment3.projectGroupRestrictions)

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment7").exists()) {
                File(assignmentsRootLocation, "dummyAssignment7").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_29_createNewAssignmentAndTryToConnectWithoutSettingUpAccessKeys() {

        // POST /assignment/new
        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "test")
                .param("assignmentName", "test")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", "git@github.com:drop-project-edu/random-private-repo.git") // some random private repo
        )
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "/assignment/setup-git/test"))

        // GET /assignment/setup-git/teste
        mvc.perform(get("/assignment/setup-git/test"))
            .andExpect(status().isOk)

        // POST /assignment/setup-git/teste?reconnect=false
        mvc.perform(post("/assignment/setup-git/test?reconnect=false"))
            .andExpect(status().isOk)
            .andExpect(view().name("setup-git"))
            .andExpect(content().string(containsString("Error cloning")));

        // verificar que, como o assignment fica desconetado, não se consegue ir para o info
        mvc.perform(get("/assignment/info/test"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "/assignment/setup-git/test"))
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_30_createNewKotlinAssignmentAndConnectWithGithub() {

        try {
            val createdAssignment = testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment1", "Dummy Kotlin Assignment",
                "org.dummy",
                "UPLOAD", sampleKotlinAssignmentRepo, language = "KOTLIN"
            )
            assertEquals("dummyAssignment1", createdAssignment.id)
            assertEquals("bcb7cd5bcd81e87043bc8763a36570c398cdc7ec", createdAssignment.gitCurrentHash)

            val result = this.mvc.perform(get("/assignment/info/dummyAssignment1"))
                .andExpect(status().isOk)
                .andExpect(view().name("assignment-detail"))
                .andExpect(model().hasNoErrors())
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignment = result.modelAndView!!.modelMap["assignment"] as Assignment
            assertEquals("dummyAssignment1", assignment.id)
            @Suppress("UNCHECKED_CAST")
            val report = result.modelAndView!!.modelMap["report"] as List<AssignmentReport>
            assertEquals(5, report.size)
            assertEquals("Assignment has a pom.xml", report[0].message)
            assertEquals("Doesn't use the 'dropProject.currentUserId' system property", report[1].message)
            assertEquals("POM file is prepared to prevent stacktrace trimming on junit errors", report[2].message)
            assertEquals("Found 1 test classes", report[3].message)
            assertEquals("You haven't defined a timeout for 2 test methods.", report[4].message)

            @Suppress("UNCHECKED_CAST")
            val tests = result.modelAndView!!.modelMap["tests"] as List<AssignmentTestMethod>
            assertEquals(3, tests.size)
            assertEquals("testFindMax", tests[0].testMethod)
            assertEquals("testFindMaxAllNegative", tests[1].testMethod)

            // change the assignment to have a mandatory tests suffix
            assignment.mandatoryTestsSuffix = "_MANDATORY"
            assignmentRepository.save(assignment)

            // refresh the assignment to kick the validation process
            this.mvc.perform(post("/assignment/refresh-git/dummyAssignment1")).andExpect(status().isOk)

            // get information again
            val result2 = this.mvc.perform(get("/assignment/info/dummyAssignment1"))
                .andExpect(status().isOk)
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val report2 = result2.modelAndView!!.modelMap["report"] as List<AssignmentReport>
            assertEquals(6, report2.size)
            assertEquals("You haven't defined mandatory tests", report2[5].message)

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment1").exists()) {
                File(assignmentsRootLocation, "dummyAssignment1").deleteRecursively()
            }
        }
    }

    @Test
    @DirtiesContext
    fun test_31_listAssignmentsAfterSomeSubmissions() {

        try {

            // create assignment
            testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment4", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo,
                teacherId = TEACHER_1.username, activateRightAfterCloning = true
            )

            testsHelper.uploadProject(this.mvc, "projectCompilationErrors", "dummyAssignment4", STUDENT_1)
            val lastSubmissionId = testsHelper.uploadProject(this.mvc, "projectCheckstyleErrors", "dummyAssignment4", STUDENT_1)

            // list assignments should return one assignment
            val mvcResult = this.mvc.perform(
                get("/assignment/my")
                    .with(user(TEACHER_1))
            )
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("assignments", hasSize<Assignment>(1)))
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignments = mvcResult.modelAndView!!.modelMap["assignments"] as List<Assignment>
            val assignment = assignments[0]

            assertEquals("dummyAssignment4", assignment.id)
            assertEquals("Dummy Assignment", assignment.name)
            assertEquals("org.dummy", assignment.packageName)
            assertEquals(SubmissionMethod.UPLOAD, assignment.submissionMethod)
            assertEquals(sampleJavaAssignmentRepo, assignment.gitRepositoryUrl)
            assertEquals(TEACHER_1.username, assignment.ownerUserId)
            assertEquals(true, assignment.active)
            assertEquals(2, assignment.numSubmissions)
            assertEquals(1, assignment.numUniqueSubmitters)
            assertEquals(submissionRepository.getReferenceById(lastSubmissionId.toLong()).submissionDate, assignment.lastSubmissionDate)
            assertEquals(AssignmentVisibility.ONLY_BY_LINK, assignment.visibility)

        } finally {
            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment4").exists()) {
                File(assignmentsRootLocation, "dummyAssignment4").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_32_refreshSSHKeysForAllAssignments() {

//        println(GitClient().computeSshFingerprint(TestsHelper.sampleJavaAssignmentPublicKey))
        println(GitClient().computeSshFingerprint(ApplicationContextListener.sampleJavaAssignmentPublicKey))

        try {
            val createdAssignment = testsHelper.createAndSetupAssignment(
                mvc, assignmentRepository, "dummyAssignment1", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo
            )
            assertEquals("dummyAssignment1", createdAssignment.id)
            assertEquals("88e3327370242da0c3ae99e6bfdd5ac22148e213", createdAssignment.gitCurrentHash)

            assertEquals(1, scheduledTasks.refreshSSHKeysForAllAssignments())

        } finally {
            // cleanup assignment files
            if (File(assignmentsRootLocation, "dummyAssignment1").exists()) {
                File(assignmentsRootLocation, "dummyAssignment1").deleteRecursively()
            }
        }
    }
}
    
