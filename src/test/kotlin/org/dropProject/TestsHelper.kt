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
package org.dropProject

import org.dropProject.dao.Assignment
import org.dropProject.dao.AssignmentVisibility
import org.dropProject.repository.AssignmentRepository
import org.dropProject.repository.GitSubmissionRepository
import org.dropProject.repository.SubmissionRepository
import org.dropProject.services.ZipService
import org.json.JSONObject
import org.junit.Assert
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ResourceLoader
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.stereotype.Service
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultMatcher
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.io.File
import java.nio.file.Files
import java.util.*

@Service
@ActiveProfiles("test")
class TestsHelper {

    @Autowired
    lateinit var zipService: ZipService

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    @Autowired
    lateinit var submissionRepository: SubmissionRepository

    companion object {
        val sampleJavaAssignmentPrivateKey = """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEowIBAAKCAQEAs3TOBSiW0ug1ikAtI16HeuNeHBKsXjdKhoVVw2bRjZwzyp0z
            Yn8TzI9TVjIXsKPpm0thVaftd2bznWfSBBTtKOXiww7yre1PyPzeSGtBRm+2Nh/H
            BaLayyEAOouMLNFs57k+fPAGPcIp8Wexsa0vXsZ7LW67tT842Cb7yiJjdWhVOQrn
            horfOJWi159fBWuzCmrWNJzHfVJPR1QmYcmJ+yabD8Zpi/H3XeHg0j2I45gLnego
            ZLH53Ecidm/yZ665mQE6qFA280w4sC2MMUlOGA90RzaSF/1d89vyqNKQhvvcc3q1
            scV/Cqn1ghSPJUsrloIuuktFN4Wpc60TLlrsRwIDAQABAoIBAA1nssII46dSiDlR
            DO4g8A7acBu5u114VN1SlXL4ubuRyP6gGogHhROZOzjrmgBsZhVfHqC24BK0worm
            B/adF5AgB/3ZHoCmgvi5BuOy+1fHHX3Shtvha+WTjABTjz+Dz1ZJ7KSJi3XOjLKH
            M+tZS/oQ6n+cz3G9DMJ8uv9A7VwGNIUnT5zDFigt65v+ul70/SxyU4N8KMR9G5HE
            TQIe7/4Lsfe7cUWp9mLxVOTG/Ha56u5rEN7vWUwn1t/rLOCy8CY9rCZ3TIT+vjbl
            imoNq86B1PGfNjiIJldhWPhwZc7AaKAtJ7YDy+FLFgsEgMX9VBewSm43WSCPatta
            cExq1HECgYEAxk/DOOhJgXtrizVy3CQZKR90bmglUSYrAz9FPh3oxrRu6Zlh630x
            HKljedpCVBbybjYLDD/QsYNA0o+MRmOH+W9hFhXHmalWnHOyxMXJ8AwkgiegLwr8
            CigH+pBf31MwVl+/mNOrA5yrFaQrefhlTmB+JVIxL9pWoPdIKJpKbbECgYEA56jj
            3f3jgS37e/Y+RY6aMwlYP2LaIDTNt1fWfrdKklS45S8YiWZOOcCdQ3tzs+Lgq+ZB
            f0qZDc47afuK3PFcmBjL6vA4FYVnGeOKyl6aVOmAdC2lZziz411m0MGYq4wAeguV
            rUqwdrf3ON0tDF1KKFrjbQY4msSd5gK+03jxn3cCgYA/2byknP3Vx9Q3jSz/Pkwv
            lmYZikTBnQVqVTvJJT4mhD/VzMHfXX6rmMpjmGeUxZKm85WZCw75qKX9ZaSnoTJN
            mJPs1XRfwEsXspTTkE9Vj8NNeM61dtbxujPfdA66TAGbPdblsPk1/4KCREqPSe/s
            TVswTwdxPd54k0XTdOIT8QKBgQDSrywF0gidjIdCFxJNSkL9FYuXojyEu+E31H/0
            IJiGetzpOqrTEyMjrQSZweXZfQYd8DwzG1IVVzF70tRY2n3+qdaTJcOr9vZseh/Y
            qq8reG1lu7nJJa2co26FfvxtT9eDJ5QJ1XqljeweYDC/JPzztK1Pky/ZueVssaSB
            SWZeQwKBgBx9G3mJ1mLkSpnX+ig5Cqil7zLyNJpLqjMt82ftqK/UbtNGAg4yxsDW
            3a+wZiW/dwSmnUdfWs1SlO5H5tPOxLbW7/4OO8v7pUaAG/W/oK3HK1MgeKAFX4Wv
            v3h9YHdvBHkGlTtQPQlt0p1ic8AsLeGmZxnBr0pfLW9JbNrAUwsi
            -----END RSA PRIVATE KEY-----
        """.trimIndent()

        val sampleJavaAssignmentPublicKey = """
            ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCzdM4FKJbS6DWKQC0jXod6414cEq
            xeN0qGhVXDZtGNnDPKnTNifxPMj1NWMhewo+mbS2FVp+13ZvOdZ9IEFO0o5eLDDvKt
            7U/I/N5Ia0FGb7Y2H8cFotrLIQA6i4ws0WznuT588AY9winxZ7GxrS9exnstbru1Pz
            jYJvvKImN1aFU5CueGit84laLXn18Fa7MKatY0nMd9Uk9HVCZhyYn7JpsPxmmL8fdd
            4eDSPYjjmAud6ChksfncRyJ2b/JnrrmZATqoUDbzTDiwLYwxSU4YD3RHNpIX/V3z2/
            Ko0pCG+9xzerWxxX8KqfWCFI8lSyuWgi66S0U3halzrRMuWuxH
        """.trimIndent()

        val sampleJavaSubmissionPrivateKey =
            """-----BEGIN RSA PRIVATE KEY-----
                |MIIEowIBAAKCAQEAiMhJ11TF285JSEKMTT/WfGrS2mncXqyAmV3XwjRem7eOV9Up
                |MMZKv5mKjSW0J/6V8TB+Gi/1KMB3zzmVsQ7gRWixZ0s7E7edy2lc1fclqfYyDVBY
                |JH9G3UcgMz4kYiHgOrAjMgU8rVGbKUYXkxmaSVCqmz4dNwTO16w54xfpk3t0MiIb
                |HCO4XWfi4s78NPoF7op7FhOj6ZRsqs8JU9rPY+VPb0PAQRhUiUgp7pL+D8xu3w83
                |ylxAfhfTJXbLohqoHoWRQWJlVHQKK7oITM9DgCuQfxomkYtUvc67wmwg2G1ejGec
                |b/Slj7nYHdmeKPfgyr3tM7GNGhu/LGX0AOSBFQIDAQABAoIBAHdGD/3RUmeFzqlQ
                |Pn/uNt5vWEZVIXD9A3a5OjeC6yzmNx2oJy78+plxDjxesGZfveh/3LaBM0rB6ret
                |AzdOPYrI7EmidHWRG+wQiH+b8B/xK1wov3Oz+Ntj9lQ40AkyeRE0HryzjGGmU6L+
                |F/iRztQ3n02YMGmVq7it2hyI8YlESq+FxDOsUck21KYX6FP+Wkp3D8kdGCSIPoAr
                |2wq1akyWBOD3brDiycATahgM6RNwMQmjxNb6H9X5QFfUltBEEcxyHXMoj7Ss31Tg
                |YfElQkWp99ZSmRXiMRsIU7n7EpfXe9icRmyW33byNasx8qgcS0jbw2BO5hKmxj0L
                |2k8AjAECgYEAyRu4LD+1VVt5+iCGNrtMasVdBPLpGZ8JLAvT5jnAORRYunXYuFN8
                |NUDcuVyo9PL+cxAyeTnY6VF67WMn6NxtZbdfaGGmZvF1DpSLp0ikWbCzOnFFkXS1
                |dWsWC3RkQLYOKIAqDJLjTOYRbmiWfPJMU+tScB/v1Xu3DdNepoqa3dUCgYEArh3Z
                |EGpunPd2VNs9+UKIAMpPw93WA3dI0Lybgp0IDhYQxvicQRq0LL6j1kuJBTkAowOl
                |b+iM/GY3M0Tkp15voPlPSrK7dhN0Jq0rFlfwjkUsxIBs7WBfgg1ss851DLhW6pM8
                |sDPOC20j1P8/vCBwJ05f8rYIibkIv+FgyjcKdkECgYBsML5nB6sNDZZUatTpct/M
                |fPrq46dlgjpT+NT5gU4ZJwoI3cdGeptSpt9X1TKL/IHI8wjEUMuR0aTZOsPIsXUl
                |CH6KUCZOMU3xFxb+WsUX/0icgz2S3/+S+JY2eVpJuedqAHEerJMd1pPAGwICm/NW
                |Hj4OVhAXgGPdSz4bSGvCNQKBgCK2nLIjJg5xX8wnMcHiO888ho6cSbDDNWc4UyDF
                |Qffc+ldQ9YYdVtKc53kv8g9yf+gAMBmRmZownsy/7X5Y25SSX/aaj7lIw516ZN69
                |hZ43uGcs24qv7hq1pnhqrvdsQRffZvgSPAAXME+k3AYbyYBspcBz/lvG4jGvM2bd
                |dl6BAoGBAIweHVdNlcE5UMOrbgXlL/93hlZPIJZs5eAh2BfrjzT2HMjm+SIs9UNx
                |6/KST0cBzpw1QjP+UG3jTdFxdro5f7lkPVCNxazKFovZ3ON1DYX5dbTEWoPn+Nrv
                |+3/dIoPGEwgf9snkZCbhXd+VFfXCkGtYKgQjTOghc3kAb9ZFspGW
                |-----END RSA PRIVATE KEY-----""".trimMargin()

        const val sampleJavaSubmissionPublicKey = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCIy" +
                "EnXVMXbzklIQoxNP9Z8atLaadxerICZXdfCNF6bt45X1Skwxkq/mYqNJbQn/pXxMH4aL/" +
                "UowHfPOZWxDuBFaLFnSzsTt53LaVzV9yWp9jINUFgkf0bdRyAzPiRiIeA6sCMyBTytUZs" +
                "pRheTGZpJUKqbPh03BM7XrDnjF+mTe3QyIhscI7hdZ+Lizvw0+gXuinsWE6PplGyqzwlT" +
                "2s9j5U9vQ8BBGFSJSCnukv4PzG7fDzfKXEB+F9MldsuiGqgehZFBYmVUdAorughMz0OAK" +
                "5B/GiaRi1S9zrvCbCDYbV6MZ5xv9KWPudgd2Z4o9+DKve0zsY0aG78sZfQA5IEV"
    }

    val STUDENT_1 = User("student1", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
    val STUDENT_2 = User("student2", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
    val TEACHER_1 = User("teacher1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

    val defaultAssignmentId = "testJavaProj"

    fun createAndSetupAssignment(mvc: MockMvc,
                                 assignmentRepository: AssignmentRepository,
                                 assignmentId: String, assignmentName: String,
                                 assignmentPackage: String, submissionMethod: String,
                                 repositoryUrl: String, privateKey: String = sampleJavaAssignmentPrivateKey,
                                 publicKey: String = sampleJavaAssignmentPublicKey,
                                 assignees: String? = null, acl: String? = null,
                                 teacherId: String = "teacher1", language: String = "JAVA",
                                 activateRightAfterCloning: Boolean = false,
                                 hiddenTestsVisibility: String = "SHOW_PROGRESS",
                                 tags: String? = null,
                                 dueDate: String? = null,
                                 minGroupSize: String? = null,
                                 maxGroupSize: String? = null,
                                 exceptions: String? = null,
                                 visibility: String = "ONLY_BY_LINK"
                                 ): Assignment {

        val user = User(teacherId, "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))

        // post form
        mvc.perform(MockMvcRequestBuilders.post("/assignment/new")
                .with(SecurityMockMvcRequestPostProcessors.user(user))
                .param("assignmentId", assignmentId)
                .param("assignmentName", assignmentName)
                .param("assignmentPackage", assignmentPackage)
                .param("language", language)
                .param("submissionMethod", submissionMethod)
                .param("gitRepositoryUrl", repositoryUrl)
                .param("assignees", assignees)
                .param("acl", acl)
                .param("hiddenTestsVisibility", hiddenTestsVisibility)
                .param("assignmentTags", tags)
                .param("dueDate", dueDate)
                .param("minGroupSize", minGroupSize)
                .param("maxGroupSize", maxGroupSize)
                .param("exceptions", exceptions)
                .param("visibility", visibility)
        )
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.header().string("Location", "/assignment/setup-git/${assignmentId}"))

        // get assignment detail
        mvc.perform(MockMvcRequestBuilders.get("/assignment/setup-git/${assignmentId}")
                .with(SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(MockMvcResultMatchers.status().isOk)

        // inject private and public key to continue
        var assignment = assignmentRepository.getById(assignmentId)
        assignment.gitRepositoryPrivKey = privateKey
        assignment.gitRepositoryPubKey = publicKey
        if (activateRightAfterCloning) {
            assignment.active = true
        }
        assignmentRepository.save(assignment)

        // connect to git repository
        mvc.perform(MockMvcRequestBuilders.post("/assignment/setup-git/${assignmentId}")
                .with(SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.header().string("Location", "/assignment/info/${assignmentId}"))
                .andExpect(MockMvcResultMatchers.flash().attribute("message", "Assignment was successfully created and connected to git repository"))

        // refresh assignment
        assignment = assignmentRepository.getById(assignmentId)

        return assignment
    }

    fun connectToGitRepositoryAndBuildReport(mvc: MockMvc, gitSubmissionRepository: GitSubmissionRepository,
                                             assignmentId: String, gitRepository: String,
                                             studentUsername: String,
                                             privateKey : String = sampleJavaSubmissionPrivateKey,
                                             publicKey : String = sampleJavaSubmissionPublicKey) : Long {

        val student = User(studentUsername, "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))

        /*** POST /student/setup-git ***/
        mvc.perform(MockMvcRequestBuilders.post("/student/setup-git")
                .param("assignmentId", assignmentId)
                .param("gitRepositoryUrl", gitRepository)
                .with(user(student))
        )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.view().name("student-setup-git"))


        val id = gitSubmissionRepository.findAll().last().id

        val gitSubmission = gitSubmissionRepository.getById(id)
        Assert.assertFalse(gitSubmission.connected)

        // inject public and private key
        gitSubmission.gitRepositoryPrivKey = privateKey
        gitSubmission.gitRepositoryPubKey = publicKey
        gitSubmissionRepository.save(gitSubmission)

        /*** POST /student/setup-git-2 ***/
        mvc.perform(MockMvcRequestBuilders.post("/student/setup-git-2/${id}")
                .with(user(student)))
                .andExpect(MockMvcResultMatchers.status().isFound())
                .andExpect(MockMvcResultMatchers.header().string("Location", "/upload/${assignmentId}"))
                .andExpect(MockMvcResultMatchers.flash().attribute("message", "Ligado com sucesso ao repositório git"))

        val updatedGitSubmission = gitSubmissionRepository.getById(id)
        Assert.assertTrue(updatedGitSubmission.connected)

        /*** GET /upload/ ***/
        mvc.perform(MockMvcRequestBuilders.get("/upload/${assignmentId}").with(user(student)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.view().name("student-git-form"))
                .andExpect(MockMvcResultMatchers.model().attribute("gitSubmission", updatedGitSubmission))

        /*** POST /git-submission/generate-report ***/
        mvc.perform(MockMvcRequestBuilders.post("/git-submission/generate-report/${id}")
                .with(user(student)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("{ \"submissionId\": \"1\"}"))

        return gitSubmission.id
    }

    // returns the submission id
    fun uploadProject(mvc: MockMvc, projectName: String, assignmentId: String, uploader: User,
                      authors: List<Pair<String,String>>? = null,
                      expectedResultMatcher: ResultMatcher = MockMvcResultMatchers.status().isOk()): String {

        val multipartFile = prepareFile(projectName, authors)

        val contentString = mvc.perform(MockMvcRequestBuilders.multipart("/upload")
                .file(multipartFile)
                .param("assignmentId", assignmentId)
                .with(user(uploader)))
                .andExpect(expectedResultMatcher)
                .andReturn().response.contentAsString

        val contentJSON = JSONObject(contentString)

        if (contentJSON.has("error")) {
            return contentJSON.getString("error");
        }

        return contentJSON.getString("submissionId")
    }

    // returns the submission id
    fun uploadProjectByAPI(mvc: MockMvc, projectName: String, assignmentId: String, uploader: Pair<String,String>,
                      authors: List<Pair<String,String>>? = null): Int {

        val multipartFile = prepareFile(projectName, authors)
        val (username, token) = uploader

        val contentString = mvc.perform(MockMvcRequestBuilders.multipart("/api/student/submissions/new")
            .file(multipartFile)
            .param("assignmentId", assignmentId)
            .header("authorization", header(username, token)))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn().response.contentAsString

        val contentJSON = JSONObject(contentString)

        return contentJSON.getInt("submissionId")
    }

    private fun prepareFile(projectName: String, authors: List<Pair<String,String>>? = null): MockMultipartFile {
        val projectFolder = resourceLoader.getResource("file:src/test/sampleProjects/$projectName").file
        val authorsFile = File(projectFolder, "AUTHORS.txt")
        val authorsBackupFile = File(projectFolder, "AUTHORS.txt.bak")
        if (authors != null) {
            // backup original AUTHORS.txt
            authorsFile.copyTo(authorsBackupFile)

            // create another AUTHORS.txt populated with the contents of parameter 'authors'
            val writer = Files.newBufferedWriter(authorsFile.toPath())
            for ((authorId,authorName) in authors) {
                writer.write("$authorId;$authorName")
                writer.newLine()
            }
            writer.close()
        }


        val zipFile = zipService.createZipFromFolder("test", projectFolder)
        zipFile.deleteOnExit()

        if (authors != null) {
            // restore original AUTHORS.txt
            authorsBackupFile.copyTo(authorsFile, overwrite = true)
            authorsBackupFile.delete()
        }

        val multipartFile = MockMultipartFile("file", zipFile.name, "application/zip", zipFile.readBytes())

        return multipartFile
    }

    fun makeSeveralSubmissions(projectNames: List<String>, mvc: MockMvc, submissionDate: Date? = null) {

        if (projectNames.size > 5) {
            throw Exception("This function is not prepared for more than 5 submissions")
        }

        val student3 = User("student3", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
        val student4 = User("student4", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
        val student5 = User("student5", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))

        for ((index, projectName) in projectNames.withIndex()) {

            val projectRoot = resourceLoader.getResource("file:src/test/sampleProjects/$projectName").file
            val path = File(projectRoot, "AUTHORS.txt").toPath()
            val lines = Files.readAllLines(path)
            Assert.assertEquals("student1;Student 1", lines[0])
            Assert.assertEquals("student2;Student 2", lines[1])

            try {
                val authors: Pair<User, List<Pair<String, String>>?> = when (index) {
                    0 -> Pair(STUDENT_1, listOf(STUDENT_1.username to "Student 1"))
                    1 -> Pair(STUDENT_2, listOf(STUDENT_2.username to "Student 2"))
                    2 -> Pair(student3, listOf(student3.username to "Student 3"))
                    3 -> Pair(student4, listOf(student4.username to "Student 4", student5.username to "Student 5"))
                    4 -> Pair(STUDENT_1, listOf(STUDENT_1.username to "Student 1"))  // another submissions from user1
                    else -> throw Exception("Not possible")
                }

                uploadProject(mvc, projectName, defaultAssignmentId, authors.first, authors.second)

            } finally {
                // restore original AUTHORS.txt
                val writer = Files.newBufferedWriter(path)
                writer.write(lines[0])
                writer.newLine()
                writer.write(lines[1])
                writer.close()
            }
        }

//            // upload five times, each time with a different author
//            uploadProject(mvc, projectName, defaultAssignmentId, STUDENT_1)
//            uploadProject(mvc, projectName, defaultAssignmentId, STUDENT_2,
//                    authors = listOf(STUDENT_2.username to "Student 2"))
//            uploadProject(mvc, projectName, defaultAssignmentId, student3,
//                    authors = listOf(student3.username to "Student 3"))
//            uploadProject(mvc, projectName, defaultAssignmentId, STUDENT_2,
//                    authors = listOf(STUDENT_2.username to "Student 2"))
//            uploadProject(mvc, projectName, defaultAssignmentId, STUDENT_1,
//                    authors = listOf(STUDENT_1.username to "Student 3"))

        // force submissionDate
        if (submissionDate != null) {
            submissionRepository.findAll().forEach {
                it.submissionDate = submissionDate
                submissionRepository.save(it)
            }
        }

    }

    fun header(username: String, personalToken: String) =
        "basic ${Base64.getEncoder().encodeToString("$username:$personalToken".toByteArray())}"
}
