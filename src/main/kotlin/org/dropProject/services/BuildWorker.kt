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
package org.dropProject.services

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.dropProject.dao.*
import org.dropProject.data.BuildReport
import org.dropProject.data.TestType
import org.dropProject.repository.*
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.*
import java.util.logging.Logger

fun hasCoverageReport(mavenizedProjectFolder: File): Boolean {
    val pomFile = File("${mavenizedProjectFolder}/pom.xml")
    return pomFile.readText().contains("jacoco-maven-plugin")
}

@Service
class BuildWorker(
        val mavenInvoker: MavenInvoker,
        val assignmentRepository: AssignmentRepository,
        val submissionRepository: SubmissionRepository,
        val gitSubmissionRepository: GitSubmissionRepository,
        val submissionReportRepository: SubmissionReportRepository,
        val buildReportRepository: BuildReportRepository,
        val jUnitReportRepository: JUnitReportRepository,
        val jacocoReportRepository: JacocoReportRepository,
        val buildReportBuilder: BuildReportBuilder) {

    val LOG = Logger.getLogger(this.javaClass.name)

    @Async
    @Transactional
    fun checkProject(mavenizedProjectFolder: File, authorsStr: String, submission: Submission,
                          principalName: String?, dontChangeStatusDate: Boolean = false, rebuildByTeacher: Boolean = false) {

        val assignment = assignmentRepository.findOne(submission.assignmentId)

        if (assignment.maxMemoryMb != null) {
            LOG.info("[${authorsStr}] Started maven invocation (max: ${assignment.maxMemoryMb}Mb)")
        } else {
            LOG.info("[${authorsStr}] Started maven invocation")
        }

        val realPrincipalName = if (rebuildByTeacher) submission.submitterUserId else principalName
        val mavenResult = mavenInvoker.run(mavenizedProjectFolder, realPrincipalName, assignment.maxMemoryMb)

        LOG.info("[${authorsStr}] Finished maven invocation")

        if (!mavenResult.expiredByTimeout) {

            LOG.info("[${authorsStr}] Maven invoker OK")
            val buildReport = buildReportBuilder.build(mavenResult.outputLines, mavenizedProjectFolder.absolutePath,
                    assignment, submission)

            if (!buildReport.mavenExecutionFailed()) {

                // clear previous indicators
                submissionReportRepository.deleteBySubmissionIdAndReportKey(submission.id, Indicator.COMPILATION.code)
                submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                        reportKey = Indicator.COMPILATION.code, reportValue = if (buildReport.compilationErrors().isEmpty()) "OK" else "NOK"))

                if (buildReport.compilationErrors().isEmpty()) {

                    if (buildReport.checkstyleValidationActive()) {
                        submissionReportRepository.deleteBySubmissionIdAndReportKey(submission.id, Indicator.CHECKSTYLE.code)
                        submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                                reportKey = Indicator.CHECKSTYLE.code, reportValue = if (buildReport.checkstyleErrors().isEmpty()) "OK" else "NOK"))
                    }


                    // PMD not yet implemented
//                submissionReportRepository.deleteBySubmissionIdAndReportKey(submission.id, "Code Quality (PMD)")
//                submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
//                        reportKey = "Code Quality (PMD)", reportValue = if (buildReport.PMDerrors().isEmpty()) "OK" else "NOK"))



                    if (assignment.acceptsStudentTests) {

                        val junitSummary = buildReport.junitSummaryAsObject(TestType.STUDENT)
                        val indicator =
                            if (buildReport.hasJUnitErrors(TestType.STUDENT) == true) {
                                "NOK"
                            } else if (junitSummary?.numTests == null || junitSummary.numTests < assignment.minStudentTests!!) {
                                // student hasn't implemented enough tests
                                "Not Enough Tests"
                            } else {
                                "OK"
                            }

                        submissionReportRepository.deleteBySubmissionIdAndReportKey(submission.id, Indicator.STUDENT_UNIT_TESTS.code)
                        submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                                    reportKey = Indicator.STUDENT_UNIT_TESTS.code,
                                    reportValue = indicator,
                                    reportProgress = junitSummary?.progress,
                                    reportGoal = junitSummary?.numTests))
                    }

                    if (buildReport.hasJUnitErrors(TestType.TEACHER) != null) {
                        val junitSummary = buildReport.junitSummaryAsObject(TestType.TEACHER)
                        submissionReportRepository.deleteBySubmissionIdAndReportKey(submission.id, Indicator.TEACHER_UNIT_TESTS.code)
                        submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                                reportKey = Indicator.TEACHER_UNIT_TESTS.code,
                                reportValue = if (buildReport.hasJUnitErrors(TestType.TEACHER) == true) "NOK" else "OK",
                                reportProgress = junitSummary?.progress,
                                reportGoal = junitSummary?.numTests))
                    }

                    if (buildReport.hasJUnitErrors(TestType.HIDDEN) != null) {
                        val junitSummary = buildReport.junitSummaryAsObject(TestType.HIDDEN)
                        submissionReportRepository.deleteBySubmissionIdAndReportKey(submission.id, Indicator.HIDDEN_UNIT_TESTS.code)
                        submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                                reportKey = Indicator.HIDDEN_UNIT_TESTS.code,
                                reportValue = if (buildReport.hasJUnitErrors(TestType.HIDDEN) == true) "NOK" else "OK",
                                reportProgress = junitSummary?.progress,
                                reportGoal = junitSummary?.numTests))
                    }
                }
            }


            val buildReportDB = buildReportRepository.save(org.dropProject.dao.BuildReport(
                    buildReport = buildReport.mavenOutput()))
            submission.buildReportId = buildReportDB.id

            // store the junit reports in the DB
            File("${mavenizedProjectFolder}/target/surefire-reports")
                    .walkTopDown()
                    .filter { it -> it.name.endsWith(".xml") }
                    .forEach {
                        val report = JUnitReport(submissionId = submission.id, fileName = it.name,
                                xmlReport = it.readText(Charset.forName("UTF-8")))
                        jUnitReportRepository.save(report)
                    }


            if (assignment.calculateStudentTestsCoverage && hasCoverageReport(mavenizedProjectFolder)) {

                // this may seem stupid but I have to rename TestTeacher files to something that will make junit ignore them,
                // then invoke maven again, so that the coverage report is based
                // on the sole execution of the student unit tests (otherwise, it will include coverage from
                // the teacher tests) and finally rename TestTeacher files back
                File("${mavenizedProjectFolder}/src/test")
                        .walkTopDown()
                        .filter { it -> it.name.startsWith("TestTeacher") }
                        .forEach {
                            Files.move(it.toPath(), it.toPath().resolveSibling("${it.name}.ignore"))
                        }


                LOG.info("[${authorsStr}] Started maven invocation again (for coverage)")

                val mavenResultCoverage = mavenInvoker.run(mavenizedProjectFolder, realPrincipalName, assignment.maxMemoryMb)
                if (!mavenResultCoverage.expiredByTimeout) {
                    LOG.info("[${authorsStr}] Finished maven invocation (for coverage)")

                    // check again the result of the tests
                    val buildReportCoverage = buildReportBuilder.build(mavenResult.outputLines, mavenizedProjectFolder.absolutePath,
                            assignment, submission)
                    if (buildReportCoverage.hasJUnitErrors(TestType.STUDENT) == true) {
                        LOG.warning("Submission ${submission.id} failed executing student tests when isolated from teacher tests")
                    } else {
                        if (File("${mavenizedProjectFolder}/target/site/jacoco").exists()) {
                            // store the jacoco reports in the DB
                            File("${mavenizedProjectFolder}/target/site/jacoco")
                                    .listFiles()
                                    .filter { it -> it.name.endsWith(".csv") }
                                    .forEach {
                                        val report = JacocoReport(submissionId = submission.id, fileName = it.name,
                                                csvReport = it.readText(Charset.forName("UTF-8")))
                                        jacocoReportRepository.save(report)
                                    }
                        } else {
                            LOG.warning("Submission ${submission.id} failed measuring coverage because the folder " +
                                    "[${mavenizedProjectFolder}/target/site/jacoco] doesn't exist")
                        }
                    }

                }

                File("${mavenizedProjectFolder}/src/test")
                        .walkTopDown()
                        .filter { it -> it.name.endsWith(".ignore") }
                        .forEach {
                            Files.move(it.toPath(), it.toPath().resolveSibling(it.name.replace(".ignore","")))
                        }

            }

            if (!rebuildByTeacher) {
                submission.setStatus(SubmissionStatus.VALIDATED, dontUpdateStatusDate = dontChangeStatusDate)
            } else {
                submission.setStatus(SubmissionStatus.VALIDATED_REBUILT, dontUpdateStatusDate = dontChangeStatusDate)
            }
        } else {
            submission.setStatus(SubmissionStatus.ABORTED_BY_TIMEOUT, dontUpdateStatusDate = dontChangeStatusDate)
        }

        if (submission.gitSubmissionId != null) {
            val gitSubmission = gitSubmissionRepository.getOne(submission.gitSubmissionId)
            gitSubmission.lastSubmissionId = submission.id
        }

        submissionRepository.save(submission)
    }

    fun checkAssignment(assignmentFolder: File, assignment: Assignment, principalName: String?) : BuildReport? {

        LOG.info("Started maven invocation to check ${assignment.id}");

        val mavenResult = mavenInvoker.run(assignmentFolder, principalName, assignment.maxMemoryMb)

        LOG.info("Finished maven invocation to check ${assignment.id}");

        if (!mavenResult.expiredByTimeout) {

            LOG.info("Maven invoker OK for ${assignment.id}")
            return buildReportBuilder.build(mavenResult.outputLines, assignmentFolder.absolutePath, assignment)
        } else {
            LOG.info("Maven invoker aborted by timeout for ${assignment.id}")
            return null
        }

    }

}
