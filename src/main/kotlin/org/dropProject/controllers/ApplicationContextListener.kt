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


import org.dropproject.DotEnv
import org.dropproject.dao.*
import org.dropproject.extensions.getContent
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
import org.dropproject.services.AssignmentService
import org.dropproject.services.AssignmentTeacherFiles
import org.dropproject.services.GitClient
import org.eclipse.jgit.api.Git
import org.slf4j.LoggerFactory
import org.dropproject.config.DropProjectProperties
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
                                 val dropProjectProperties: DropProjectProperties,
                                 val environment: org.springframework.core.env.Environment) : ApplicationListener<ContextRefreshedEvent> {

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
        LOG.info("Maven use current JDK: ${dropProjectProperties.maven.useCurrentJdk}")
        LOG.info("Java home (running JVM): ${System.getProperty("java.home")}")
        LOG.info("Environment variables:")
        val sensitiveEnvVariable = Regex("KEY|SECRET|TOKEN|PASSWORD", RegexOption.IGNORE_CASE)
        for ((key, value) in System.getenv()) {
            LOG.info("\t$key : ${if (sensitiveEnvVariable.containsMatchIn(key)) "***redacted***" else value}")
        }
        LOG.info("*************************************************")

        if (!environment.activeProfiles.contains("dev")) {
            val logPath = environment.getProperty("logging.file.path", "logs")
            println("Logging to ${logPath}/dp.log. To see logs in the console, run with the 'dev' profile: mvn spring-boot:run -Dspring-boot.run.profiles=dev")
        }

        validateJavaVersionForSecurityManager()

        // Abort all pending submissions since they can't continue after a restart
        val pendingStatuses = listOf(SubmissionStatus.SUBMITTED.code, SubmissionStatus.SUBMITTED_FOR_REBUILD.code, SubmissionStatus.REBUILDING.code)
        var abortedCount = 0
        for (status in pendingStatuses) {
            val pendingSubmissions = submissionRepository.findByStatusOrderByStatusDate(status)
            for (submission in pendingSubmissions) {
                LOG.info("Aborting pending submission ${submission.id} (status: ${submission.getStatus()})")
                submission.setStatus(SubmissionStatus.ABORTED_BY_TIMEOUT)
                submissionRepository.save(submission)
                abortedCount++
            }
        }
        if (abortedCount > 0) {
            LOG.info("Aborted ${abortedCount} pending submission(s)")
        }

        // It it's a fresh instance, create two initial assignments (one in Java and the other in Kotlin) just to play
        val assignments = assignmentRepository.findAll()
        if (assignments.size == 0) {
            val samplePrivateKey = DotEnv.resolve("DP_SAMPLE_JAVA_ASSIGNMENT_PRIVATE_KEY")
            if (samplePrivateKey != null) {
                val samplePublicKey = gitClient.derivePublicKey(samplePrivateKey.toByteArray())
                createAndPopulateSampleJavaAssignment(samplePrivateKey, samplePublicKey)
                createAndPopulateSampleKotlinAssignment(samplePrivateKey, samplePublicKey)
            } else {
                LOG.warn("Sample assignments not created: no ssh key for the sample repositories. " +
                        "Set DP_SAMPLE_JAVA_ASSIGNMENT_PRIVATE_KEY or create a .env file (see .env.example)")
            }
        }

        LOG.info("Updating assignment metrics")
        updateAssignmentMetrics()
        LOG.info("Finished updating assignment metrics")
    }

    private fun createAndPopulateSampleJavaAssignment(privateKey: String, publicKey: String) {
        val assignment = assignmentRepository.save(Assignment(id = "sampleJavaProject", name = "Sample Java Assignment",
            packageName = "org.dropProject.samples.sampleJavaAssignment", ownerUserId = "teacher1",
            submissionMethod = SubmissionMethod.UPLOAD,
            gitRepositoryUrl = "git@github.com:drop-project-edu/sampleJavaAssignment.git",
            gitRepositoryPrivKey = privateKey,
            gitRepositoryPubKey = publicKey,
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

            // update hash
            val git = Git.open(File(dropProjectProperties.assignments.rootLocation, assignment.gitRepositoryFolder))
            assignment.gitCurrentHash = gitClient.getLastCommitInfo(git)?.sha1

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

    private fun createAndPopulateSampleKotlinAssignment(privateKey: String, publicKey: String) {
        val assignment = assignmentRepository.save(Assignment(id = "sampleKotlinProject", name = "Sample Kotlin Assignment",
                packageName = "org.dropProject.samples.sampleKotlinAssignment", ownerUserId = "teacher1",
                submissionMethod = SubmissionMethod.UPLOAD, language = Language.KOTLIN,
                gitRepositoryUrl = "git@github.com:drop-project-edu/sampleKotlinAssignment.git",
                gitRepositoryPrivKey = privateKey,
                gitRepositoryPubKey = publicKey,
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

            // update hash
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

    private fun validateJavaVersionForSecurityManager() {
        val javaVersion = Runtime.version().feature()
        if (javaVersion >= 24) {
            val errorMessage = "Java ${javaVersion} detected. The SecurityManager was removed in Java 24 (JEP 486) " +
                    "and Drop Project requires it to sandbox student submissions. Please use Java 17-23."
            LOG.error(errorMessage)
            throw IllegalStateException(errorMessage)
        }
        if (javaVersion >= 18) {
            val securityManagerFlag = System.getProperty("java.security.manager")
            if (securityManagerFlag != "allow") {
                val errorMessage = "Java ${javaVersion} detected. The SecurityManager requires the JVM flag " +
                        "-Djava.security.manager=allow to be set. Please add this flag to the JVM arguments."
                LOG.error(errorMessage)
                throw IllegalStateException(errorMessage)
            }
        }
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
