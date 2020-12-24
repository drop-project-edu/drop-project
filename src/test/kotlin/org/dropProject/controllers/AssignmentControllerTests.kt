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

import org.dropProject.TestsHelper
import org.dropProject.dao.*
import org.dropProject.data.SubmissionInfo
import org.dropProject.forms.AssignmentForm
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.AssigneeRepository
import org.dropProject.repository.AssignmentRepository
import org.dropProject.repository.AssignmentTagRepository
import org.dropProject.repository.SubmissionRepository
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.Matchers
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.validation.BindingResult
import java.io.File


@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
@TestPropertySource(locations=["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AssignmentControllerTests {

    @Autowired
    lateinit var mvc : MockMvc

    @Autowired
    lateinit var assignmentRepository: AssignmentRepository

    @Autowired
    lateinit var assigneeRepository: AssigneeRepository

    @Autowired
    lateinit var submissionRepository: SubmissionRepository

    @Autowired
    lateinit var assignmentTagRepository: AssignmentTagRepository

    @Autowired
    lateinit var testsHelper: TestsHelper

    @Value("\${assignments.rootLocation}")
    val assignmentsRootLocation : String = ""

    @Test
    @WithMockUser("teacher1",roles=["TEACHER"])
    @DirtiesContext
    fun test_00_getNewAssignmentForm() {
        this.mvc.perform(get("/assignment/new"))
                .andExpect(status().isOk)
    }


    @Test
    @WithMockUser("teacher1",roles=["TEACHER"])
    @DirtiesContext
    fun test_01_createInvalidAssignment() {

        mvc.perform(post("/assignment/new"))
                .andExpect(status().isOk)
                .andExpect(view().name("assignment-form"))
                .andExpect(model().attributeHasFieldErrors("assignmentForm","assignmentId"))


        mvc.perform(post("/assignment/new")
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
                .andExpect(model().attributeHasFieldErrors("assignmentForm","acceptsStudentTests"))

        mvc.perform(post("/assignment/new")
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
                .andExpect(model().attributeHasFieldErrors("assignmentForm","acceptsStudentTests"))

        mvc.perform(post("/assignment/new")
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
                .andExpect(model().attributeHasFieldErrors("assignmentForm","acceptsStudentTests"))

        mvc.perform(post("/assignment/new")
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
                .andExpect(model().attributeHasFieldErrors("assignmentForm","acl"))

        mvc.perform(post("/assignment/new")
                .param("assignmentId", "assignmentId")
                .param("assignmentName", "assignmentName")
                .param("assignmentPackage", "assignmentPackage")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param("gitRepositoryUrl", "git@github.com:palves-ulht/sampleJavaAssignment.git")
                .param("acceptsStudentTests", "true")    // <<<<
                .param("calculateStudentTestsCoverage", "true")  // <<<<
                .param("minStudentTests", "1")   // <<<<
        )
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.header().string("Location", "/assignment/setup-git/assignmentId"))


    }

    @Test
    @WithMockUser("teacher1",roles=["TEACHER"])
    @DirtiesContext
    fun test_02_createNewAssignmentAndConnectWithGithub() {

        try {
            testsHelper.createAndSetupAssignment(mvc, assignmentRepository, "dummyAssignment1", "Dummy Assignment",
                    "org.dummy",
                    "UPLOAD", "git@github.com:palves-ulht/sampleJavaAssignment.git"
                    )

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation,"dummyAssignment1").exists()) {
                File(assignmentsRootLocation,"dummyAssignment1").deleteRecursively()
            }
        }
    }

    @Ignore("THIS TEST IS FAILING BECAUSE BITBUCKET DOESNT RECOGNIZE THE PUBLIC KEY")
    @Test
    @WithMockUser("teacher1",roles=["TEACHER"])
    @DirtiesContext
    fun test_03_createNewAssignmentAndConnectWithBitbucket() {

        try {
            // post form
            this.mvc.perform(post("/assignment/new")
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
            val assignment = assignmentRepository.getOne("dummyAssignment2")
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
                    .andExpect(flash().attribute("message","Assignment was successfully created and connected to git repository"))

            val updatedAssignment = assignmentRepository.getOne("dummyAssignment2")
            assert(updatedAssignment.active == false)

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation,"dummyAssignment2").exists()) {
                File(assignmentsRootLocation,"dummyAssignment2").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1",roles=["TEACHER"])
    @DirtiesContext
    fun test_04_createAssignmentWithInvalidGitRepository() {

        val mvcResult = this.mvc.perform(post("/assignment/new")
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

        val result = mvcResult.modelAndView.model.get(BindingResult.MODEL_KEY_PREFIX + "assignmentForm") as BindingResult
        assertEquals("Error cloning git repository. Are you sure the url is right?", result.getFieldError("gitRepositoryUrl").defaultMessage)

        try {
            assignmentRepository.getOne("dummyAssignment3")
            fail("dummyAssignment shouldn't exist in the database")
        } catch (e: Exception) {
        }

    }

    @Test
    @DirtiesContext
    fun test_05_listAssignments() {

        val user = User("p1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        try {// list assigments should return empty
            this.mvc.perform(get("/assignment/my")
                    .with(SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk())
                    .andExpect(model().hasNoErrors())
                    .andExpect(model().attribute("assignments", emptyList<Assignment>()))

            // create assignment
            testsHelper.createAndSetupAssignment(mvc, assignmentRepository, "dummyAssignment4", "Dummy Assignment",
                    "org.dummy",
                    "UPLOAD", "git@github.com:palves-ulht/sampleJavaAssignment.git",
                    teacherId = "p1", activateRightAfterCloning = false)

            // list assignments should return one assignment
            val mvcResult = this.mvc.perform(get("/assignment/my")
                    .with(SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk())
                    .andExpect(model().hasNoErrors())
                    .andExpect(model().attribute("assignments", hasSize<Assignment>(1)))
                    .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignments = mvcResult.modelAndView.modelMap["assignments"] as List<Assignment>
            val assignment = assignments[0]

            assertEquals("dummyAssignment4", assignment.id)
            assertEquals("Dummy Assignment", assignment.name)
            assertEquals("org.dummy", assignment.packageName)
            assertEquals(SubmissionMethod.UPLOAD, assignment.submissionMethod)
            assertEquals("git@github.com:palves-ulht/sampleJavaAssignment.git", assignment.gitRepositoryUrl)
            assertEquals("p1", assignment.ownerUserId)
            assertEquals(false, assignment.active)

        } finally {
            // cleanup assignment files
            if (File(assignmentsRootLocation,"dummyAssignment4").exists()) {
                File(assignmentsRootLocation,"dummyAssignment4").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1",roles=["TEACHER"])
    @DirtiesContext
    fun test_06_createNewAssignmentAndForgetToConnectWithGithub() {  // assignment should be marked as inactive

        // post form
        this.mvc.perform(post("/assignment/new")
                .param("assignmentId", "dummyAssignment5")
                .param("assignmentName", "Dummy Assignment")
                .param("assignmentPackage", "org.dummy")
                .param("submissionMethod", "UPLOAD")
                .param("language", "JAVA")
                .param("gitRepositoryUrl", "git@github.com:palves-ulht/sampleJavaAssignment.git")
        )
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/assignment/setup-git/dummyAssignment5"))


        // get assignment detail
        this.mvc.perform(get("/assignment/setup-git/dummyAssignment5"))
                .andExpect(status().isOk)

        val assignment = assignmentRepository.getOne("dummyAssignment5")
        assert(assignment.active == false)
    }

    @Test
    @WithMockUser("teacher1",roles=["TEACHER"])
    @DirtiesContext
    fun test_07_showOnlyActiveAssignments() {

        try {
            // create an assignment with white-list. it will start as inactive
            testsHelper.createAndSetupAssignment(mvc, assignmentRepository, "dummyAssignment6", "Dummy Assignment",
                    "org.dummy",
                    "UPLOAD", "git@github.com:palves-ulht/sampleJavaAssignment.git",
                    assignees = "21800000")

            // login as 21800000 and get an empty list of assignments
            this.mvc.perform(get("/")
                    .with(SecurityMockMvcRequestPostProcessors.user("21800000")))
                    .andExpect(status().isOk)
                    .andExpect(model().hasNoErrors())
                    .andExpect(model().attribute("assignments", hasSize<Assignment>(0)))

            // mark the assignment as active
            this.mvc.perform(get("/assignment/toggle-status/dummyAssignment6"))
                    .andExpect(status().isFound)
                    .andExpect(header().string("Location", "/assignment/my"))
                    .andExpect(flash().attribute("message","Assignment was marked active"))

            // login again as 21800000 and get a redirect to the assignment
            this.mvc.perform(get("/")
                    .with(SecurityMockMvcRequestPostProcessors.user("21800000")))
                    .andExpect(status().isFound)
                    .andExpect(header().string("Location", "/upload/dummyAssignment6"))

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation,"dummyAssignment6").exists()) {
                File(assignmentsRootLocation,"dummyAssignment6").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser(username="teacher1",roles=["TEACHER"])
    @DirtiesContext
    fun test_08_createAssignmentWithOtherTeachers() {

        try {

            testsHelper.createAndSetupAssignment(mvc, assignmentRepository, "dummyAssignment7", "Dummy Assignment",
                    "org.dummy",
                    "UPLOAD", "git@github.com:palves-ulht/sampleJavaAssignment.git",
                    acl = "p1000, p1001")

            // get assignment detail
            val mvcResult = this.mvc.perform(get("/assignment/info/dummyAssignment7"))
                    .andExpect(status().isOk)
                    .andReturn()

            @Suppress("UNCHECKED_CAST")
            val aclList = mvcResult.modelAndView.modelMap["acl"] as List<AssignmentACL>
            assertEquals(2, aclList.size)
            assertEquals("p1000", aclList[0].userId)
            assertEquals("p1001", aclList[1].userId)

            // accessing "/assignments/my" with p1000 should give one assignment
            val user = User("p1000", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))
            this.mvc.perform(get("/assignment/my")
                    .with(SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk)
                    .andExpect(model().hasNoErrors())
                    .andExpect(model().attribute("assignments", hasSize<Assignment>(1)))

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation,"dummyAssignment7").exists()) {
                File(assignmentsRootLocation,"dummyAssignment7").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1",roles=["TEACHER"])
    @DirtiesContext
    fun test_09_createNewAssignmentAndEdit() {

        try {
            testsHelper.createAndSetupAssignment(mvc, assignmentRepository, "dummyAssignment8", "Dummy Assignment",
                    "org.dummy",
                    "UPLOAD", "git@github.com:palves-ulht/sampleJavaAssignment.git")

            // get edit form
            this.mvc.perform(get("/assignment/edit/dummyAssignment8"))
                    .andExpect(status().isOk)
                    .andExpect(model().hasNoErrors())
                    .andExpect(model().attribute("assignmentForm",
                            AssignmentForm(assignmentId = "dummyAssignment8",
                                    assignmentName = "Dummy Assignment",
                                    assignmentPackage = "org.dummy",
                                    submissionMethod = SubmissionMethod.UPLOAD,
                                    language = Language.JAVA,
                                    gitRepositoryUrl = "git@github.com:palves-ulht/sampleJavaAssignment.git",
                                    hiddenTestsVisibility= TestVisibility.SHOW_PROGRESS,
                                    editMode = true)))

            // post a change
            mvc.perform(post("/assignment/new")
                    .param("assignmentId", "dummyAssignment8")
                    .param("assignmentName", "New Name")
                    .param("editMode", "true")
                    .param("submissionMethod","UPLOAD")
                    .param("language", "JAVA")
                    .param("gitRepositoryUrl", "git@github.com:palves-ulht/sampleJavaAssignment.git")
                    .param("leaderboardType", "ELLAPSED")
            )
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", "/assignment/info/dummyAssignment8"))

            // get edit form again
            this.mvc.perform(get("/assignment/edit/dummyAssignment8"))
                    .andExpect(status().isOk)
                    .andExpect(model().hasNoErrors())
                    .andExpect(model().attribute("assignmentForm",
                            AssignmentForm(assignmentId = "dummyAssignment8",
                                    assignmentName = "New Name",
                                    submissionMethod = SubmissionMethod.UPLOAD,
                                    language = Language.JAVA,
                                    gitRepositoryUrl = "git@github.com:palves-ulht/sampleJavaAssignment.git",
                                    editMode = true,
                                    leaderboardType = LeaderboardType.ELLAPSED)))


        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation,"dummyAssignment8").exists()) {
                File(assignmentsRootLocation,"dummyAssignment8").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1",roles=["TEACHER"])
    @DirtiesContext
    fun test_10_checkAssignmentHasNoErrors() {

        // create initial assignment
        val assignment01 = Assignment(id = "testJavaProj", name = "Test Project (for automatic tests)",
                packageName = "org.testProj", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, active = false, gitRepositoryUrl = "git://dummyRepo",
                gitRepositoryFolder = "testJavaProj", hiddenTestsVisibility = TestVisibility.HIDE_EVERYTHING)
        assignmentRepository.save(assignment01)

        // toggle status
        this.mvc.perform(get("/assignment/toggle-status/testJavaProj"))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/assignment/my"))
                .andExpect(flash().attribute("message","Assignment was marked active"))

        // confirm it is now active
        val assignment = assignmentRepository.getOne("testJavaProj")
        assertTrue("assignment is not active", assignment.active)
    }


    @Test
    @WithMockUser("teacher1",roles=["TEACHER"])
    @DirtiesContext
    fun test_11_getAssignmentInfo() {

        // create initial assignment
        val assignment = Assignment(id = "testJavaProj", name = "Test Project (for automatic tests)",
                packageName = "org.testProj", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, active = false, gitRepositoryUrl = "git://dummyRepo",
                gitRepositoryFolder = "testJavaProj", public = false)
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

        // create initial assignment
        val assignment = Assignment(id = "testJavaProj", name = "Test Project (for automatic tests)",
                packageName = "org.testProj", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
                gitRepositoryFolder = "testJavaProj", public = false)
        assignmentRepository.save(assignment)
        assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student1"))


        // make a submission
        val submissionId = testsHelper.uploadProject(this.mvc, "projectInvalidStructure1", "testJavaProj", STUDENT_1).toLong()

        // try to delete the assignment but DP will issue an error since it has submissions
        this.mvc.perform(post("/assignment/delete/testJavaProj")
                .with(user(TEACHER_1)))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/assignment/my"))
                .andExpect(flash().attribute("error","Assignment can't be deleted because it has submissions"))

        // remove the submission
        submissionRepository.deleteById(submissionId)

        // try to delete the assignment again, this time with success
        this.mvc.perform(post("/assignment/delete/testJavaProj")
                .with(user(TEACHER_1)))
                .andExpect(status().isFound)
                .andExpect(header().string("Location", "/assignment/my"))
                .andExpect(flash().attribute("message","Assignment was successfully deleted"))

    }

    @Test
    @DirtiesContext
    fun test_13_listArchivedAssignments() {

        val user = User("p1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        try {// list archived assignments should return empty
            this.mvc.perform(get("/assignment/archived")
                    .with(SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk())
                    .andExpect(model().hasNoErrors())
                    .andExpect(model().attribute("assignments", emptyList<Assignment>()))

            // create assignment
            testsHelper.createAndSetupAssignment(mvc, assignmentRepository, "dummyAssignment4", "Dummy Assignment",
                    "org.dummy",
                    "UPLOAD", "git@github.com:palves-ulht/sampleJavaAssignment.git",
                    teacherId = "p1", activateRightAfterCloning = false)

            // list archived assignments should still return empty
            this.mvc.perform(get("/assignment/archived")
                    .with(SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk())
                    .andExpect(model().hasNoErrors())
                    .andExpect(model().attribute("assignments", emptyList<Assignment>()))

            // archive assignment
            this.mvc.perform(post("/assignment/archive/dummyAssignment4")
                    .with(SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isFound)
                    .andExpect(header().string("Location", "/assignment/my"))
                    .andExpect(flash().attribute("message","Assignment was archived. You can now find it in the Archived assignments page"))

            // list archived assignments should now return 1 assignment
            val mvcResult = this.mvc.perform(get("/assignment/archived")
                    .with(SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk())
                    .andExpect(model().hasNoErrors())
                    .andExpect(model().attribute("assignments", hasSize<Assignment>(1)))
                    .andReturn()

            @Suppress("UNCHECKED_CAST")
            val assignments = mvcResult.modelAndView.modelMap["assignments"] as List<Assignment>
            val assignment = assignments[0]

            assertEquals("dummyAssignment4", assignment.id)


        } finally {
            // cleanup assignment files
            if (File(assignmentsRootLocation,"dummyAssignment4").exists()) {
                File(assignmentsRootLocation,"dummyAssignment4").deleteRecursively()
            }
        }
    }

    // refreshAssignmentGitRepository
    @Test
    @WithMockUser("teacher1",roles=["TEACHER"])
    @DirtiesContext
    fun test_14_refreshAssignmentGitRepository() {

        try {
            testsHelper.createAndSetupAssignment(mvc, assignmentRepository, "dummyAssignment1", "Dummy Assignment",
                    "org.dummy",
                    "UPLOAD", "git@github.com:palves-ulht/sampleJavaAssignment.git"
            )

            val contentString = this.mvc.perform(post("/assignment/refresh-git/dummyAssignment1"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

            val contentJSON = JSONObject(contentString)
            assertEquals(true, contentJSON.getBoolean("success"))

        } finally {

            // cleanup assignment files
            if (File(assignmentsRootLocation,"dummyAssignment1").exists()) {
                File(assignmentsRootLocation,"dummyAssignment1").deleteRecursively()
            }
        }
    }

    @Test
    @DirtiesContext
    fun test_15_markAllAsFinal() {

        val assignmentId = testsHelper.defaultAssignmentId

        // create assignment
        val assignment01 = Assignment(id = assignmentId, name = "Test Project (for automatic tests)",
                packageName = "org.dropProject.sampleAssignments.testProj", ownerUserId = testsHelper.TEACHER_1.username,
                submissionMethod = SubmissionMethod.UPLOAD, active = true, gitRepositoryUrl = "git://dummyRepo",
                gitRepositoryFolder = "testJavaProj")
        assignmentRepository.save(assignment01)

        // make several submissions for that assignment
        testsHelper.makeSeveralSubmissions(
                listOf("projectInvalidStructure1",
                        "projectInvalidStructure1",
                        "projectInvalidStructure1",
                        "projectInvalidStructure1"), mvc)

        // mark all as final
        this.mvc.perform(post("/assignment/markAllAsFinal/${assignmentId}")
                .with(user(testsHelper.TEACHER_1)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/report/${assignmentId}"))

        // check results
        val reportResult = this.mvc.perform(get("/report/testJavaProj")
                .with(user(testsHelper.TEACHER_1)))
                .andExpect(status().isOk())
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val report = reportResult.modelAndView.modelMap["submissions"] as List<SubmissionInfo>
        report.forEach {
            assertTrue(it.lastSubmission.markedAsFinal)
        }

    }

    @Test
    @WithMockUser("teacher1",roles=["TEACHER"])
    @DirtiesContext
    fun test_16_createNewAssignmentWithTags() {

        // check available tags
        // it shouldn't exist none
        var globalTags = assignmentTagRepository.findAll()
        assertEquals(0, globalTags.size)

        // post form
        this.mvc.perform(post("/assignment/new")
                .param("assignmentId", "dummyAssignmentTags")
                .param("assignmentName", "Dummy Assignment")
                .param("assignmentPackage", "org.dummy")
                .param("submissionMethod", "UPLOAD")
                .param("language", "JAVA")
                .param("gitRepositoryUrl", "git@github.com:palves-ulht/sampleJavaAssignment.git")
                .param("assignmentTags", "sample,test,simple")
        )
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/assignment/setup-git/dummyAssignmentTags"))

        // get assignment detail
        val mvcResult = this.mvc.perform(get("/assignment/info/dummyAssignmentTags"))
                .andExpect(status().isOk)
                .andReturn()

        @Suppress("UNCHECKED_CAST")
        val assignment = mvcResult.modelAndView.modelMap["assignment"] as Assignment
        val tagNames = assignment.tags.map { it.name }
        assertThat(tagNames, Matchers.containsInAnyOrder("sample", "test", "simple"))

        // check available tags
        // it should now return 'sample','test','simple'
        globalTags = assignmentTagRepository.findAll()
        assertEquals(3, globalTags.size)
        assertThat(tagNames, Matchers.containsInAnyOrder("sample", "test", "simple"))
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    @DirtiesContext
    fun test_17_updateAssignmentWithTags() {

        try {
            testsHelper.createAndSetupAssignment(mvc, assignmentRepository, "dummyAssignmentTags", "Dummy Assignment",
                    "org.dummy",
                    "UPLOAD", "git@github.com:palves-ulht/sampleJavaAssignment.git",
                    tags = "sample,test,simple")  // <<<<

            // add one tag and remove 2 tags
            mvc.perform(post("/assignment/new")
                    .param("assignmentId", "dummyAssignmentTags")
                    .param("assignmentName", "Dummy Assignment")
                    .param("assignmentPackage", "org.dummy")
                    .param("submissionMethod", "UPLOAD")
                    .param("language", "JAVA")
                    .param("gitRepositoryUrl", "git@github.com:palves-ulht/sampleJavaAssignment.git")
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
            val assignment = mvcResult.modelAndView.modelMap["assignment"] as Assignment
            val tagNames = assignment.tags.map { it.name }
            assertEquals(2, tagNames.size)
            assertThat(tagNames, Matchers.containsInAnyOrder("sample", "complex"))

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
    @WithMockUser("teacher1",roles=["TEACHER"])
    @DirtiesContext
    fun test_18_createNewAssignmentAndInfoWithTestMethods() {

        try {
            testsHelper.createAndSetupAssignment(mvc, assignmentRepository, "dummyAssignmentTests", "Dummy Assignment",
                    "org.dummy",
                    "UPLOAD", "git@github.com:palves-ulht/sampleJavaAssignment.git")

            val mvcResult = this.mvc.perform(get("/assignment/info/dummyAssignmentTests"))
                    .andExpect(status().isOk)
                    .andReturn()

            @Suppress("UNCHECKED_CAST")
            val testMethods = mvcResult.modelAndView.modelMap["tests"] as List<AssignmentTestMethod>
            assertEquals(2, testMethods.size)
            assertThat(testMethods.map { it.testMethod }, Matchers.contains("testFindMax", "testFindMaxWithNull"))

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
            this.mvc.perform(get("/assignment/my")
                    .with(SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk())
                    .andExpect(model().hasNoErrors())
                    .andExpect(model().attribute("assignments", emptyList<Assignment>()))

            // create two assignments
            testsHelper.createAndSetupAssignment(mvc, assignmentRepository, "dummyAssignment4", "Dummy Assignment",
                    "org.dummy",
                    "UPLOAD", "git@github.com:palves-ulht/sampleJavaAssignment.git",
                    teacherId = "p1", activateRightAfterCloning = false, tags = "sample,test")

            testsHelper.createAndSetupAssignment(mvc, assignmentRepository, "dummyAssignment5", "Dummy Assignment",
                    "org.dummy",
                    "UPLOAD", "git@github.com:palves-ulht/sampleJavaAssignment.git",
                    teacherId = "p1", activateRightAfterCloning = false, tags = "other,test")

            // list assignments filtered by tag "sample" should return one assignment
            this.mvc.perform(get("/assignment/my?tags=sample")
                    .with(SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk())
                    .andExpect(model().hasNoErrors())
                    .andExpect(model().attribute("assignments", hasSize<Assignment>(1)))

            // list assignments filtered by tag "notexistent" should return zero assignments
            this.mvc.perform(get("/assignment/my?tags=notexistent")
                    .with(SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk())
                    .andExpect(model().hasNoErrors())
                    .andExpect(model().attribute("assignments", hasSize<Assignment>(0)))

            // list assignments filtered by tag "test" should return two assignments
            this.mvc.perform(get("/assignment/my?tags=test")
                    .with(SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk())
                    .andExpect(model().hasNoErrors())
                    .andExpect(model().attribute("assignments", hasSize<Assignment>(2)))

            // list assignments filtered by tag "sample,other" should return zero assignments
            this.mvc.perform(get("/assignment/my?tags=sample,other")
                    .with(SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk())
                    .andExpect(model().hasNoErrors())
                    .andExpect(model().attribute("assignments", hasSize<Assignment>(0)))

        } finally {
            // cleanup assignment files
            if (File(assignmentsRootLocation,"dummyAssignment4").exists()) {
                File(assignmentsRootLocation,"dummyAssignment4").deleteRecursively()
            }

            if (File(assignmentsRootLocation,"dummyAssignment5").exists()) {
                File(assignmentsRootLocation,"dummyAssignment5").deleteRecursively()
            }
        }
    }

}
    
