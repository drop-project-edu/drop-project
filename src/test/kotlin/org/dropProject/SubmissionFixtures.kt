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
package org.dropproject

import com.fasterxml.jackson.databind.ObjectMapper
import net.lingala.zip4j.ZipFile
import org.apache.commons.io.FileUtils
import org.dropproject.TestUsers.STUDENT_1
import org.dropproject.TestUsers.STUDENT_2
import org.dropproject.TestUsers.STUDENT_3
import org.dropproject.TestUsers.STUDENT_4
import org.dropproject.TestUsers.STUDENT_5
import org.dropproject.TestUsers.TEACHER_1
import org.dropproject.dao.Assignment
import org.dropproject.dao.GitSubmission
import org.dropproject.dao.Language
import org.dropproject.dao.Submission
import org.dropproject.dao.SubmissionGitInfo
import org.dropproject.dao.SubmissionStatus
import org.dropproject.dao.SubmissionStructure
import org.dropproject.repository.SubmissionGitInfoRepository
import org.dropproject.repository.SubmissionRepository
import org.junit.jupiter.api.Assertions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ResourceLoader
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.stereotype.Service
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultMatcher
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.dropproject.services.ZipService
import java.io.File
import java.nio.file.Files
import java.util.*

/**
 * Test fixtures for making submissions (by upload or by API) and inspecting their results.
 */
@Service
class SubmissionFixtures {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var zipService: ZipService

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    @Autowired
    lateinit var submissionRepository: SubmissionRepository

    @Autowired
    lateinit var submissionGitInfoRepository: SubmissionGitInfoRepository

    val defaultAssignmentId = "testJavaProj"

    // returns the submission id
    fun uploadProject(projectName: String, assignmentId: String, uploader: User,
                      authors: List<Pair<String,String>>? = null,
                      expectedResultMatcher: ResultMatcher = MockMvcResultMatchers.status().isOk(),
                      submissionStructure: SubmissionStructure = SubmissionStructure.COMPACT,
                      language: Language = Language.JAVA): String {

        val multipartFile = prepareFile(projectName, submissionStructure, language, authors)

        val contentString = mvc.perform(MockMvcRequestBuilders.multipart("/upload")
                .file(multipartFile)
                .param("assignmentId", assignmentId)
                .with(user(uploader)))
                .andExpect(expectedResultMatcher)
                .andReturn().response.contentAsString

        val contentJSON = ObjectMapper().readTree(contentString)

        if (contentJSON.has("error")) {
            return contentJSON.get("error").asText()
        }

        return contentJSON.get("submissionId").asText()
    }

    // returns the submission id
    fun uploadProjectByAPI(projectName: String, assignmentId: String, uploader: Pair<String,String>,
                      authors: List<Pair<String,String>>? = null,
                      submissionStructure: SubmissionStructure = SubmissionStructure.COMPACT,
                      language: Language = Language.JAVA): Int {

        val multipartFile = prepareFile(projectName, submissionStructure, language, authors)
        val (username, token) = uploader

        val contentString = mvc.perform(MockMvcRequestBuilders.multipart("/api/student/submissions/new")
            .file(multipartFile)
            .param("assignmentId", assignmentId)
            .header("authorization", basicAuthHeader(username, token)))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn().response.contentAsString

        val contentJSON = ObjectMapper().readTree(contentString)

        return contentJSON.get("submissionId").asInt()
    }

    private fun getProjectPath(projectName: String, submissionStructure: SubmissionStructure, language: Language): String {
        val structure = when (submissionStructure) {
            SubmissionStructure.COMPACT -> "compact"
            SubmissionStructure.MAVEN -> "maven"
        }
        val lang = when (language) {
            Language.JAVA -> "java"
            Language.KOTLIN -> "kotlin"
        }

        return "file:src/test/sampleProjects/$structure/$lang/$projectName"
    }

    private fun prepareFile(projectName: String, submissionStructure: SubmissionStructure, language: Language,
                           authors: List<Pair<String,String>>? = null): MockMultipartFile {
        val projectFolder = resourceLoader.getResource(getProjectPath(projectName, submissionStructure, language)).file
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

    fun makeSeveralSubmissions(projectNames: List<String>, submissionDate: Date? = null,
                              submissionStructure: SubmissionStructure = SubmissionStructure.COMPACT,
                              language: Language = Language.JAVA) {

        if (projectNames.size > 5) {
            throw Exception("This function is not prepared for more than 5 submissions")
        }

        for ((index, projectName) in projectNames.withIndex()) {

            val projectRoot = resourceLoader.getResource(getProjectPath(projectName, submissionStructure, language)).file
            val path = File(projectRoot, "AUTHORS.txt").toPath()
            val lines = Files.readAllLines(path)
            Assertions.assertEquals("student1;Student 1", lines[0])
            Assertions.assertEquals("student2;Student 2", lines[1])

            try {
                val authors: Pair<User, List<Pair<String, String>>?> = when (index) {
                    0 -> Pair(STUDENT_1, listOf(STUDENT_1.username to "Student 1"))
                    1 -> Pair(STUDENT_2, listOf(STUDENT_2.username to "Student 2"))
                    2 -> Pair(STUDENT_3, listOf(STUDENT_3.username to "Student 3"))
                    3 -> Pair(STUDENT_4, listOf(STUDENT_4.username to "Student 4", STUDENT_5.username to "Student 5"))
                    4 -> Pair(STUDENT_1, listOf(STUDENT_1.username to "Student 1"))  // another submissions from user1
                    else -> throw Exception("Not possible")
                }

                uploadProject(projectName, defaultAssignmentId, authors.first, authors.second,
                             submissionStructure = submissionStructure, language = language)

            } finally {
                // restore original AUTHORS.txt
                val writer = Files.newBufferedWriter(path)
                writer.write(lines[0])
                writer.newLine()
                writer.write(lines[1])
                writer.close()
            }
        }

        // force submissionDate
        if (submissionDate != null) {
            submissionRepository.findAll().forEach {
                it.submissionDate = submissionDate
                submissionRepository.save(it)
            }
        }

    }

    // saves a Submission + matching SubmissionGitInfo, pairing a student commit with the teacher-files
    // commit that was active in the assignment at that time
    fun saveHistoricalSubmission(gitSubmission: GitSubmission, assignment: Assignment,
                                 assignmentGitHash: String, studentCommitHash: String,
                                 submissionDate: Date): Submission {
        val submission = Submission(
            gitSubmissionId = gitSubmission.id, submissionDate = submissionDate,
            submitterUserId = "student1", status = SubmissionStatus.VALIDATED.code, statusDate = submissionDate,
            assignmentId = assignment.id, assignmentGitHash = assignmentGitHash, markedAsFinal = true
        )
        submission.group = gitSubmission.group
        submissionRepository.save(submission)
        submissionGitInfoRepository.save(SubmissionGitInfo(submissionId = submission.id, gitCommitHash = studentCommitHash))
        return submission
    }

    fun downloadMavenProjectZip(submissionId: Long, teacher: User = TEACHER_1): ByteArray {
        return mvc.perform(
            MockMvcRequestBuilders.get("/downloadMavenProject/$submissionId")
                .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .with(user(teacher))
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn()
            .response.contentAsByteArray
    }

    fun zipEntryContent(zipBytes: ByteArray, entrySuffix: String): String {
        val tempZipFile = File.createTempFile("downloaded", ".zip")
        try {
            FileUtils.writeByteArrayToFile(tempZipFile, zipBytes)
            val zip = ZipFile(tempZipFile)
            val header = zip.fileHeaders.first { it.fileName.endsWith(entrySuffix) }
            return zip.getInputStream(header).bufferedReader().readText()
        } finally {
            tempZipFile.delete()
        }
    }
}
