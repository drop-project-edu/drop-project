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


import org.dropProject.dao.*
import org.dropProject.extensions.getContent
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.*
import org.dropProject.services.AssignmentService
import org.dropProject.services.AssignmentTeacherFiles
import org.dropProject.services.GitClient
import org.eclipse.jgit.api.Git
import org.slf4j.LoggerFactory
import org.dropProject.config.DropProjectProperties
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Profile
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.sql.Timestamp
import java.time.LocalDateTime


@Component
@Transactional
@Profile("!test")
class ApplicationContextListener(val assignmentRepository: AssignmentRepository,
                                 val assignmentReportRepository: AssignmentReportRepository,
                                 val assignmentTestMethodRepository: AssignmentTestMethodRepository,
                                 val assignmentACLRepository: AssignmentACLRepository,
                                 val assigneeRepository: AssigneeRepository,
                                 val submissionRepository: SubmissionRepository,
                                 val submissionReportRepository: SubmissionReportRepository,
                                 val buildReportRepository: BuildReportRepository,
                                 val jUnitReportRepository: JUnitReportRepository,
                                 val authorRepository: AuthorRepository,
                                 val projectGroupRepository: ProjectGroupRepository,
                                 val gitClient: GitClient,
                                 val resourceLoader: ResourceLoader,
                                 val assignmentService: AssignmentService,
                                 val assignmentTeacherFiles: AssignmentTeacherFiles,
                                 val dropProjectProperties: DropProjectProperties) : ApplicationListener<ContextRefreshedEvent> {

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
    }

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    /**
     * This function is executed when DP starts running.
     *
     * If the assignment repository (i.e. database) is empty, this function will create some "fake data" (e.g. students,
     * teachers, and submissions from both) in order to have a "in-memory" database that allows testing the system.
     */
    override fun onApplicationEvent(event: ContextRefreshedEvent) {

        LOG.info("************ Starting Drop Project **************")
        LOG.info("Maven home: ${dropProjectProperties.maven.home}")
        LOG.info("Maven repository: ${dropProjectProperties.maven.repository}")
        LOG.info("Environment variables:")
        for ((key, value) in System.getenv()) {
            LOG.info("\t$key : $value")
        }
        LOG.info("*************************************************")

        // It it's a fresh instance, create two initial assignments (one in Java and the other in Kotlin) just to play
        val assignments = assignmentRepository.findAll()
        if (assignments.size == 0) {
            createAndPopulateSampleJavaAssignment()
            createAndPopulateSampleKotlinAssignment()
        }

        LOG.info("Updating assignment metrics")
        updateAssignmentMetrics()
        LOG.info("Finished updating assignment metrics")
    }

    private fun createAndPopulateSampleJavaAssignment() {
        val assignment = assignmentRepository.save(Assignment(id = "sampleJavaProject", name = "Sample Java Assignment",
            packageName = "org.dropProject.samples.sampleJavaAssignment", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD,
            gitRepositoryUrl = "git@github.com:drop-project-edu/sampleJavaAssignment.git",
            gitRepositoryPrivKey = sampleJavaAssignmentPrivateKey,
            gitRepositoryPubKey = sampleJavaAssignmentPublicKey,
            gitRepositoryFolder = "sampleJavaProject",
            active = true))

        assignmentService.addTagToAssignment(assignment, "sample")
        assignmentACLRepository.save(AssignmentACL(assignmentId = assignment.id, userId = "admin"))

        assignmentTestMethodRepository.save(AssignmentTestMethod(assignment = assignment,
                testClass = "TestTeacherProject", testMethod = "testFindMax"))
        assignmentTestMethodRepository.save(AssignmentTestMethod(assignment = assignment,
                testClass = "TestTeacherProject", testMethod = "testFindMaxWithNull"))
        assignmentTestMethodRepository.save(AssignmentTestMethod(assignment = assignment,
                testClass = "TestTeacherProject", testMethod = "testFindMaxAllNegative"))
        assignmentTestMethodRepository.save(AssignmentTestMethod(assignment = assignment,
                testClass = "TestTeacherProject", testMethod = "testFindMaxNegativeAndPositive"))

        val gitRepository = assignment.gitRepositoryUrl
        var connected = false
        try {
            val directory = File(dropProjectProperties.assignments.rootLocation, assignment.id)
            if (directory.exists()) {
                directory.deleteRecursively()
            }
            gitClient.clone(gitRepository, directory, assignment.gitRepositoryPrivKey!!.toByteArray())
            LOG.info("[${assignment.id}] Successfuly cloned ${gitRepository} to ${directory}")
            // only save if it successfully cloned the assignment
            assignmentRepository.save(assignment)

            assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student1"))
            assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student2"))
            assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student4"))
            assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student5"))

            // check assignment, to produce report
            val report = assignmentTeacherFiles.checkAssignmentFiles(assignment, null)
            report.forEach {
                assignmentReportRepository.save(AssignmentReport(assignmentId = assignment.id, type = it.type,
                    message = it.message, description = it.description))
            }

            connected = true

        } catch (e: Exception) {
            LOG.error("Error cloning ${gitRepository} - ${e}", e)
        }

        if (connected) {

            val author = Author(name = "Student 1", userId = "student1")
            authorRepository.save(author)

            uploadStudentSubmission(author, "2019-01-01T10:34:00", "javaSubmissionError", "NOK", 1, 2)
            uploadStudentSubmission(author, "2019-01-02T11:05:03", "javaSubmissionOk", "OK", 2, 2)

            val author2 = Author(name = "Student 2", userId = "student2")
            authorRepository.save(author2)
            uploadStudentSubmission(author2, "2019-01-02T14:55:30", "javaSubmissionOk", "OK", 2, 2)

            val author3 = Author(name = "BC", userId = "teacher1")
            authorRepository.save(author3)
            uploadStudentSubmission(author3, "2020-12-05T14:28:00", "javaSubmissionError", "NOK", 0, 4)
            uploadStudentSubmission(author3, "2020-12-05T14:37:00", "javaSubmission4Errors", "NOK", 0, 4)

            val author4 = Author(name = "Neo The One", userId = "teacher2")
            authorRepository.save(author4)
            uploadStudentSubmission(author4, "2020-12-05T14:28:00", "javaSubmissionError", "NOK", 0, 4)
            uploadStudentSubmission(author4, "2020-12-05T14:37:00", "javaSubmission4Errors", "NOK", 0, 4)

            val author5 = Author(name = "The Jackal", userId = "student3")
            authorRepository.save(author5)
            uploadStudentSubmission(author5, "2020-12-18T14:37:00", "javaSubmission2Errors", "NOK", 2, 4)

            val author6 = Author(name = "Leo Da Vinci", userId = "student4")
            authorRepository.save(author6)
            uploadStudentSubmission(author6, "2020-12-17T14:37:00", "javaSubmission2Errors", "NOK", 2, 4)


        }
    }

    private fun createAndPopulateSampleKotlinAssignment() {
        val assignment = assignmentRepository.save(Assignment(id = "sampleKotlinProject", name = "Sample Kotlin Assignment",
                packageName = "org.dropProject.samples.sampleKotlinAssignment", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, language = Language.KOTLIN,
                gitRepositoryUrl = "git@github.com:drop-project-edu/sampleKotlinAssignment.git",
                gitRepositoryPrivKey = sampleJavaAssignmentPrivateKey,
                gitRepositoryPubKey = sampleJavaAssignmentPublicKey,
                gitRepositoryFolder = "sampleKotlinProject",
                active = true))

        assignmentService.addTagToAssignment(assignment, "sample")
        assignmentService.addTagToAssignment(assignment, "kotlin")

        assignmentTestMethodRepository.save(AssignmentTestMethod(assignment = assignment,
                testClass = "TestTeacherProject", testMethod = "testFindMax"))
        assignmentTestMethodRepository.save(AssignmentTestMethod(assignment = assignment,
                testClass = "TestTeacherProject", testMethod = "testFindMaxAllNegative"))
        assignmentTestMethodRepository.save(AssignmentTestMethod(assignment = assignment,
                testClass = "TestTeacherProject", testMethod = "testFindMaxNegativeAndPositive"))

        val gitRepository = assignment.gitRepositoryUrl
        try {
            val directory = File(dropProjectProperties.assignments.rootLocation, assignment.id)
            if (directory.exists()) {
                directory.deleteRecursively()
            }
            gitClient.clone(gitRepository, directory, assignment.gitRepositoryPrivKey!!.toByteArray())
            LOG.info("[${assignment.id}] Successfuly cloned ${gitRepository} to ${directory}")
            val git = Git.open(File(dropProjectProperties.assignments.rootLocation, assignment.gitRepositoryFolder))
            assignment.gitCurrentHash = gitClient.getLastCommitInfo(git)?.sha1

            // only save if it successfully cloned the assignment
            assignmentRepository.save(assignment)

            assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student1"))
            assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student2"))
            assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student4"))
            assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = "student5"))

        } catch (e: Exception) {
            LOG.error("Error cloning ${gitRepository} - ${e}")
        }
    }

    /**
     * This is an auxiliary function to create fake submissions to place in the "in-memory" database.
     */
    private fun uploadStudentSubmission(author: Author, submissionDate: String, submissionName: String,
                                        teacherTestsIndicator: String, teacherTestsProgress: Int,
                                        teacherTestsGoal: Int) : Long {

        val submission = Submission(submissionId = "1",
                submissionDate = Timestamp.valueOf(LocalDateTime.parse(submissionDate)),
                status = SubmissionStatus.VALIDATED.code,
                statusDate = Timestamp.valueOf(LocalDateTime.parse(submissionDate)),
                assignmentId = "sampleJavaProject",
                assignmentGitHash = null,
                submitterUserId = author.userId, submissionMode = SubmissionMode.UPLOAD)

        submissionRepository.save(submission)

        val groups = projectGroupRepository.getGroupsForAuthor(author.userId)
        lateinit var group : ProjectGroup
        if (groups.isEmpty()) {
            group = ProjectGroup()
            group.authors.add(author)
        } else {
            group = groups[0]
        }
        group.submissions.add(submission)
        projectGroupRepository.save(group)

        author.group = group
        submission.group = group

        authorRepository.save(author)
        submissionRepository.save(submission)

        submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                reportKey = Indicator.PROJECT_STRUCTURE.code, reportValue = "OK"))
        submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                reportKey = Indicator.COMPILATION.code, reportValue = "OK"))
        submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                reportKey = Indicator.CHECKSTYLE.code, reportValue = "OK"))
        submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                reportKey = Indicator.TEACHER_UNIT_TESTS.code, reportValue = teacherTestsIndicator,
                reportProgress = teacherTestsProgress, reportGoal = teacherTestsGoal))

        // this file must be coherent with the report
        val buildReport = BuildReport(buildReport =
            (resourceLoader.getResource("classpath:/initialData/${submissionName}MavenOutput.txt") as ClassPathResource).getContent())
        buildReportRepository.save(buildReport)

        submission.buildReport = buildReport
        submissionRepository.save(submission)

        jUnitReportRepository.save(JUnitReport(submissionId = submission.id,
                fileName = "TEST-org.dropProject.samples.sampleJavaAssignment.TestTeacherProject.xml",
                xmlReport = (resourceLoader.getResource("classpath:/initialData/${submissionName}JUnitXml.txt")as ClassPathResource).getContent()))

        return submission.id
    }

    /**
     * Updates assignment metrics for all the assignments. This is a very slow operation!
     */
    private fun updateAssignmentMetrics() {

        val allAssignments = assignmentRepository.findAllByNumSubmissions(0)

        for ((idx, assignment) in allAssignments.withIndex()) {
            LOG.info("Updating metrics for assignment ${assignment.id} (${idx+1} / ${allAssignments.size})")
            assignment.numSubmissions = submissionRepository.countByAssignmentIdAndStatusNot(assignment.id, SubmissionStatus.DELETED.code).toInt()
            if (assignment.numSubmissions > 0) {
                    assignment.lastSubmissionDate =
                        submissionRepository.findFirstByAssignmentIdOrderBySubmissionDateDesc(assignment.id).submissionDate
            }
            assignment.numUniqueSubmitters = submissionRepository.findUniqueSubmittersByAssignmentId(assignment.id).toInt()
            assignmentRepository.save(assignment)
        }
    }

}
