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

import org.dropproject.TestUsers.TEACHER_1
import org.junit.jupiter.api.Tag
import org.dropproject.DropProjectIntegrationTest
import org.dropproject.TestKeys.sampleJavaAssignmentPrivateKey
import org.dropproject.TestKeys.sampleJavaAssignmentPublicKey
import org.dropproject.dao.*
import org.dropproject.repository.*
import org.dropproject.services.GitClient
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.Matchers.*
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File
import java.util.*


@DropProjectIntegrationTest
@Tag("integration")
class AssignmentGitConnectionTests : AssignmentTestBase() {

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `create new assignment and connect with github`() {

        try {
            val createdAssignment = assignmentFixtures.createAndSetupAssignment("dummyAssignment1", "Dummy Assignment",
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
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment1").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment1").deleteRecursively()
            }
        }
    }

    @Disabled("THIS TEST IS FAILING BECAUSE BITBUCKET DOESNT RECOGNIZE THE PUBLIC KEY")
    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `create new assignment and connect with bitbucket`() {

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
            val assignment = assignmentRepository.findById("dummyAssignment2").get()
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

            val updatedAssignment = assignmentRepository.findById("dummyAssignment2").get()
            assert(updatedAssignment.active == false)

        } finally {

            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment2").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment2").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `create new assignment and forget to connect with github`() {  // assignment should be marked as inactive

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

        val assignment = assignmentRepository.findById("dummyAssignment5").get()
        assert(assignment.active == false)
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `create new assignment and try to connect without setting up access keys`() {

        // POST /assignment/new
        mvc.perform(
            post("/assignment/new")
                .param("assignmentId", "test")
                .param("assignmentName", "test")
                .param("language", "JAVA")
                .param("submissionMethod", "UPLOAD")
                .param(
                    "gitRepositoryUrl",
                    "git@github.com:drop-project-edu/random-private-repo.git"
                ) // some random private repo
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
    fun `create new kotlin assignment and connect with github`() {

        try {
            val createdAssignment = assignmentFixtures.createAndSetupAssignment("dummyAssignment1", "Dummy Kotlin Assignment",
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
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment1").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment1").deleteRecursively()
            }
        }
    }

    @Test
    fun `reconnect assignment`() {

        try {
            // create assignment, properly connected to git (sampleJavaAssignmentRepo)
            assignmentFixtures.createAndSetupAssignment("dummyAssignment", "Dummy Assignment",
                "org.dummy", "UPLOAD", sampleJavaAssignmentRepo,
                teacherId = "teacher1", activateRightAfterCloning = false
            )

            // remove the private and public keys to mess up the connection with github
            val assignment = assignmentRepository.findById("dummyAssignment").get()
            assignment.gitRepositoryPrivKey = null
            assignment.gitRepositoryPubKey = null
            assignmentRepository.save(assignment)

            // test git refresh - it should fail
            val contentString = this.mvc.perform(post("/assignment/refresh-git/dummyAssignment").with(user(TEACHER_1)))
                .andExpect(status().isInternalServerError)
                .andReturn().response.contentAsString
            val contentJSON = JSONObject(contentString)
            assertEquals(
                "Error pulling from git@github.com:drop-project-edu/sampleJavaAssignment.git",
                contentJSON.getString("error")
            )

            // reconnect assignment (step 1) - open page with the newly generated key
            this.mvc.perform(get("/assignment/setup-git/dummyAssignment?reconnect=true").with(user(TEACHER_1)))
                .andExpect(status().isOk)

            // now force the keys to be equal to the ones previously created in github
            assignment.gitRepositoryPrivKey = sampleJavaAssignmentPrivateKey
            assignment.gitRepositoryPubKey = sampleJavaAssignmentPublicKey
            assignmentRepository.save(assignment)

            // reconnect assignment (step 2) - open page with the newly generated key
            this.mvc.perform(post("/assignment/setup-git/dummyAssignment?reconnect=true").with(user(TEACHER_1)))
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
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment").deleteRecursively()
            }
        }

    }

    // refreshAssignmentGitRepository
    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `refresh assignment git repository`() {

        try {
            assignmentFixtures.createAndSetupAssignment("dummyAssignment1", "Dummy Assignment",
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
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment1").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment1").deleteRecursively()
            }
        }
    }

    @Test
    @WithMockUser("teacher1", roles = ["TEACHER"])
    fun `refresh ssh keys for all assignments`() {

        try {
            val createdAssignment = assignmentFixtures.createAndSetupAssignment("dummyAssignment1", "Dummy Assignment",
                "org.dummy",
                "UPLOAD", sampleJavaAssignmentRepo
            )
            assertEquals("dummyAssignment1", createdAssignment.id)
            assertEquals("88e3327370242da0c3ae99e6bfdd5ac22148e213", createdAssignment.gitCurrentHash)

            assertEquals(1, scheduledTasks.refreshSSHKeysForAllAssignments())

        } finally {
            // cleanup assignment files
            if (File(dropProjectProperties.assignments.rootLocation, "dummyAssignment1").exists()) {
                File(dropProjectProperties.assignments.rootLocation, "dummyAssignment1").deleteRecursively()
            }
        }
    }
}
