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

import jakarta.persistence.EntityNotFoundException
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import org.apache.commons.io.FileUtils
import org.dropproject.dao.*
import org.dropproject.data.TestType
import org.dropproject.extensions.formatDefault
import org.dropproject.extensions.realName
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
import org.dropproject.services.*
import org.dropproject.storage.StorageService
import org.slf4j.LoggerFactory
import org.dropproject.config.DropProjectProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Controller
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.*
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.io.File
import java.io.FileWriter
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Paths
import java.security.Principal
import java.util.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * ReportController contains MVC controller functions to handle requests related with submission reports
 * (for example, build report, submissions list, etc.).
 */
@Controller
class ReportController(
    val projectGroupRepository: ProjectGroupRepository,
    val assignmentRepository: AssignmentRepository,
    val assignmentACLRepository: AssignmentACLRepository,
    val submissionRepository: SubmissionRepository,
    val gitSubmissionRepository: GitSubmissionRepository,
    val submissionReportRepository: SubmissionReportRepository,
    val authorRepository: AuthorRepository,
    val assignmentTeacherFiles: AssignmentTeacherFiles,
    val buildReportBuilder: BuildReportBuilder,
    val gitClient: GitClient,
    val submissionService: SubmissionService,
    val storageService: StorageService,
    val zipService: ZipService,
    val templateEngine: TemplateEngine,
    val assignmentService: AssignmentService,
    val reportService: ReportService,
    val jPlagService: JPlagService,
    val studentService: StudentService,
    val dropProjectProperties: DropProjectProperties
) {

    @Value("\${spring.web.locale}")
    val currentLocale : Locale = Locale.getDefault()

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    /**
     * Controller that handles requests for the list of signalled groups in an [Assignment].
     * The signalled groups are groups of students that are failing exactly the same tests.
     * @param assignmentId is a String identifying the relevant Assignment
     * @param model is a [ModelMap] that will be populated with information to use in a View
     * @param principal is a [Principal] representing the user making the request
     * @param request is an [HttpServletRequest]
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/signalledSubmissions/{assignmentId}"], method = [(RequestMethod.GET)])
    fun getSignaledGroupsOrSubmissions(@PathVariable assignmentId: String, model: ModelMap,
                                       principal: Principal, request: HttpServletRequest): String {
        model["assignmentId"] = assignmentId

        assignmentService.getAllSubmissionsForAssignment(assignmentId, principal, model, request,
            includeTestDetails = true,
            mode = "signalledSubmissions")

        return "signalled-submissions"
    }

    /**
     * Controller that handles requests for an [Assignment]'s report (for example, list of submissions per student/group).
     * @param assignmentId is a String identifying the relevant Assignment
     * @param model is a [ModelMap] that will be populated with information to use in a View
     * @param principal is a [Principal] representing the user making the request
     * @param request is an [HttpServletRequest]
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/report/{assignmentId}"], method = [(RequestMethod.GET)])
    fun getReport(@PathVariable assignmentId: String, model: ModelMap,
                  principal: Principal, request: HttpServletRequest): String {

        assignmentService.getAllSubmissionsForAssignment(assignmentId, principal, model, request, mode = "summary")

        return "report"
    }

    /**
     * Controller that handles requests for an [Assignment]'s Test Matrix. The Test Matrix is a matrix where each row
     * represents a student/group and each column represents an evaluation test. The intersection between lines and
     * columns will tell us if each group has passed each specific test.
     * @param assignmentId is a String identifying the relevant Assignment
     * @param model is a [ModelMap] that will be populated with information to use in a View
     * @param principal is a [Principal] representing the user making the request
     * @param request is an [HttpServletRequest]
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/testMatrix/{assignmentId}"], method = [(RequestMethod.GET)])
    fun getTestMatrix(@PathVariable assignmentId: String, model: ModelMap,
                      principal: Principal, request: HttpServletRequest): String {
        model["assignmentId"] = assignmentId

        assignmentService.getAllSubmissionsForAssignment(assignmentId, principal, model, request,
            includeTestDetails = true,
            mode = "testMatrix")

        return "test-matrix"
    }

    /**
     * Controller that handles requests for a [Submission]'s "Build Report".
     *
     * @param submissionId is a Long identifying the relevant Submission
     * @param model is a [ModelMap] that will be populated with information to use in a View
     * @param principal is a [Principal] representing the user making the request
     * @param request is an [HttpServletRequest]
     *
     * @return is a String identifying the relevant View
     */
    @RequestMapping(value = ["/buildReport/{submissionId}"], method = [(RequestMethod.GET)])
    fun getSubmissionReport(@PathVariable submissionId: Long, model: ModelMap, principal: Principal,
                            request: HttpServletRequest): String {

        val buildReport = reportService.buildReport(submissionId, principal, request)

        model["numSubmissions"] = buildReport.numSubmissions
        model["assignment"] = buildReport.assignment

        model["submission"] = buildReport.submission
        model["gitSubmission"] = buildReport.gitSubmission
        model["gitRepository"] = buildReport.gitRepository
        model["gitRepositoryWithHash"] = buildReport.gitRepositoryWithHash
        model["readmeHTML"] = buildReport.readmeHtml
        model["error"] = buildReport.error
        model["autoRefresh"] = buildReport.isValidating
        model["summary"] = buildReport.summary
        model["structureErrors"] = buildReport.structureErrors
        model["authors"] = buildReport.authors
        model["buildReport"] = buildReport.buildReport

        return "build-report"
    }

    /**
     * Controller that handles the download of a specific submission's code. The submission is downloaded in
     * a format compatible with Maven.
     * @param submissionId is a Long, representing the [Submission] to download
     * @param principal is a [Principal] representing the user making the request
     * @param request is a [HttpServletRequest]
     * @param response is a [HttpServletResponse]
     * @return A [FileSystemResource] containing a [ZipFile]
     */
    @RequestMapping(value = ["/downloadMavenProject/{submissionId}"],
        method = [(RequestMethod.GET)], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    @ResponseBody
    fun downloadMavenProject(@PathVariable submissionId: Long, principal: Principal,
                             request: HttpServletRequest, response: HttpServletResponse): FileSystemResource {

        val submission = submissionRepository.findById(submissionId).orElse(null)
        if (submission != null) {

            // check that principal belongs to the group that made this submission
            if (!request.isUserInRole("TEACHER")) {
                throw AccessDeniedException("${principal.realName()} is not allowed to view this report")
            }

            val projectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission,
                wasRebuilt = submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT)
            LOG.info("[${principal.realName()}] downloaded ${projectFolder.name}")

            val zipFilename = submission.group.authorsIdStr().replace(",", "_") + "_mavenized"
            val zipFile = zipService.createZipFromFolder(zipFilename, projectFolder)

            LOG.info("Created ${zipFile.absolutePath}")

            response.setHeader("Content-Disposition", "attachment; filename=${zipFilename}.zip")

            return FileSystemResource(zipFile)

        } else {
            throw ResourceNotFoundException()
        }
    }

    /**
     * Controller that handles the download of a specific submission's code. The submission is downloaded in
     * it's original format.
     * @param submissionId is a Long, representing the Submission to download
     * @param principal is a [Principal] representing the user making the request
     * @param request is an [HttpServletRequest]
     * @param response is an [HttpServletResponse]
     * @return A [FileSystemResource] containing a [ZipFile]
     */
    @RequestMapping(value = ["/downloadOriginalProject/{submissionId}"],
        method = [(RequestMethod.GET)], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    @ResponseBody
    fun downloadOriginalProject(@PathVariable submissionId: Long, principal: Principal,
                                request: HttpServletRequest, response: HttpServletResponse): FileSystemResource {

        val submission = submissionRepository.findById(submissionId).orElse(null)
        if (submission != null) {

            // check that principal belongs to the group that made this submission
            if (!request.isUserInRole("TEACHER")) {
                val groupElements = submission.group.authors
                if (groupElements.filter { it -> it.userId == principal.realName() }.isEmpty()) {
                    throw AccessDeniedException("${principal.realName()} is not allowed to view this report")
                }
            }

            if (submission.submissionId != null) {  // submission by upload
                val projectFolder = File(dropProjectProperties.storage.uploadLocation, submission.submissionFolder)
                val projectFile = File("${projectFolder.absolutePath}.zip")  // for every folder, there is a corresponding zip file with the same name

                LOG.info("[${principal.realName()}] downloaded ${projectFile.name}")

                val filename = submission.group.authorsIdStr().replace(",", "_")
                response.setHeader("Content-Disposition", "attachment; filename=${filename}.zip")

                return FileSystemResource(projectFile)
            } else {  // submission by git
                val gitSubmissionId = submission.gitSubmissionId ?:
                throw IllegalArgumentException("Git submission without gitSubmissionId")
                val gitSubmission = gitSubmissionRepository.findById(gitSubmissionId).orElse(null)
                    ?: throw IllegalArgumentException("git submission ${gitSubmissionId} is not registered")
                val repositoryFolder = File(dropProjectProperties.storage.gitLocation, gitSubmission.getFolderRelativeToStorageRoot())

                val zipFilename = submission.group.authorsIdStr().replace(",", "_")
                val zFile = File.createTempFile(zipFilename, ".zip")
                if (zFile.exists()) {
                    zFile.delete();
                }
                val zipFile = ZipFile(zFile)
                val zipParameters = ZipParameters()
                zipParameters.isIncludeRootFolder = false
                zipParameters.compressionLevel = CompressionLevel.ULTRA
                zipFile.addFolder(repositoryFolder, zipParameters)
//                zipFile.createZipFileFromFolder(repositoryFolder, zipParameters, false, -1)

                LOG.info("Created ${zipFile.file.absolutePath}")

                response.setHeader("Content-Disposition", "attachment; filename=${zipFilename}.zip")

                return FileSystemResource(zipFile.file)
            }

        } else {
            throw ResourceNotFoundException()
        }
    }

    /**
     * Controller that handles requests related with the download of ALL the students' submissions (code)
     * for a certain Assignment. The submissions are downloaded in their original format.
     * @param assignmentId is a String identifying the relevant Assignment
     * @param principal is a [Principal] representing the user making the request
     * @param response is an [HttpServletResponse]
     * @return A [FileSystemResource] containing a [ZipFile]
     */
    @RequestMapping(value = ["/downloadOriginalAll/{assignmentId}"],
        method = [(RequestMethod.GET)], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    @ResponseBody
    fun downloadOriginalAll(@PathVariable assignmentId: String, principal: Principal,
                            response: HttpServletResponse): FileSystemResource {

        val assignment = assignmentRepository.findById(assignmentId).orElse(null)
            ?: throw IllegalArgumentException("assignment ${assignmentId} is not registered")
        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)

        if (principal.realName() != assignment.ownerUserId && acl.find { it -> it.userId == principal.realName() } == null) {
            throw IllegalAccessError("Assignment reports can only be accessed by their owner or authorized teachers")
        }

        if (assignment.submissionMethod != SubmissionMethod.UPLOAD) {
            throw IllegalArgumentException("downloadOriginalAll is only implemented for assignments whose submissions are through upload")
        }

        val submissionInfoList = submissionService.getSubmissionsList(assignment)

        val tempFolder = Files.createTempDirectory("dp-${assignmentId}").toFile()
        try {
            for (submissionInfo in submissionInfoList) {
                val projectFolder = File(tempFolder, submissionInfo.projectGroup.authorsIdStr("_"))
                projectFolder.mkdir()

                val submission = submissionInfo.lastSubmission

                val originalProjectFolder = storageService.retrieveProjectFolder(submission)
                    ?: throw IllegalArgumentException("projectFolder for ${submission.submissionId} doesn't exist")

                var hadToUnzip = false
                if (!originalProjectFolder.exists()) {
                    // let's check if there is a zip file with this project
                    val originalProjectZipFile = File("${originalProjectFolder.absolutePath}.zip")
                    if (originalProjectZipFile.exists()) {
                        zipService.unzip(Paths.get(originalProjectZipFile.path), originalProjectFolder.name)
                        hadToUnzip = true
                    }
                }

                LOG.info("Copying ${originalProjectFolder.absolutePath} to ${projectFolder.absolutePath}")

                FileUtils.copyDirectory(originalProjectFolder, projectFolder)

                if (hadToUnzip) {
                    originalProjectFolder.delete()
                }
            }

            val zipFilename = tempFolder.name
            val zipFile = zipService.createZipFromFolder(zipFilename, tempFolder)

            LOG.info("Created ${zipFile.absolutePath} with ${submissionInfoList.size} projects from ${assignmentId}")

            response.setHeader("Content-Disposition", "attachment; filename=${assignmentId}_last_submissions.zip")

            return FileSystemResource(zipFile)
        } finally {
            tempFolder.delete()
        }

    }

    /**
     * Controller that handles requests related with the download of ALL the students' submissions (code)
     * for a certain [Assignment]. The submissions are downloaded in a format compatible with Maven.
     * @param assignmentId is a String identifying the relevant Assignment
     * @param principal is a [Principal] representing the user making the request
     * @param response is an [HttpServletResponse]
     * @return A [FileSystemResource] containing a [ZipFile]
     */
    @RequestMapping(value = ["/downloadMavenizedAll/{assignmentId}"],
        method = [(RequestMethod.GET)], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    @ResponseBody
    fun downloadMavenizedAll(@PathVariable assignmentId: String, principal: Principal,
                             response: HttpServletResponse): FileSystemResource {

        val assignment = assignmentRepository.findById(assignmentId).orElse(null)
            ?: throw IllegalArgumentException("assignment ${assignmentId} is not registered")
        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)

        if (principal.realName() != assignment.ownerUserId && acl.find { it -> it.userId == principal.realName() } == null) {
            throw IllegalAccessError("Assignment reports can only be accessed by their owner or authorized teachers")
        }

        val submissionInfoList = submissionService.getSubmissionsList(assignment)
        val modulesList = mutableListOf<String>()

        val tempFolder = Files.createTempDirectory("dp-mavenized-${assignmentId}").toFile()

        try {
            for (submissionInfo in submissionInfoList) {

                val submission = submissionInfo.lastSubmission
                val originalProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission,
                    wasRebuilt = submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT)

                if (!originalProjectFolder.exists()) {
                    LOG.warn("${originalProjectFolder.absolutePath} doesn't exist. " +
                            "Probably, it has structure errors. This submission will not be included in the zip file.")
                } else {

                    val projectFolder = File(tempFolder, submissionInfo.projectGroup.authorsIdStr("_"))
                    projectFolder.mkdir()

                    LOG.info("Copying ${originalProjectFolder.absolutePath} to ${projectFolder.absolutePath}")

                    FileUtils.copyDirectory(originalProjectFolder, projectFolder) {
                        val relativePath = it.toRelativeString(originalProjectFolder)
                        !relativePath.startsWith("target")
                    }

                    // replace artifactId in pom.xml
                    val newPomFileContent = ArrayList<String>()
                    val pomFile = File(projectFolder, "pom.xml")
                    var firstArtifactIdLineFound = false
                    pomFile
                        .readLines()
                        .forEach {
                            newPomFileContent.add(
                                if (!firstArtifactIdLineFound && it.contains("<artifactId>")) {
                                    val projectGroupStr = submissionInfo.projectGroup.authorsIdStr("_")
                                    modulesList.add(projectGroupStr)
                                    firstArtifactIdLineFound = true
                                    "    <artifactId>${assignmentId}-${projectGroupStr}</artifactId>"
                                } else {
                                    it
                                }
                            )
                        }

                    Files.write(pomFile.toPath(), newPomFileContent)
                }
            }

            // create aggregate pom
            val ctx = Context()
            ctx.setVariable("groupId", assignment.packageName)
            ctx.setVariable("artifactId", assignment.id)
            ctx.setVariable("modules", modulesList)
            try {
                templateEngine.process("download-all-pom", ctx, FileWriter(File(tempFolder, "pom.xml")))
            } catch (e: Exception) {
                println("e = ${e}")
            }

            val zipFilename = tempFolder.name
            val zipFile = zipService.createZipFromFolder(zipFilename, tempFolder)

            LOG.info("Created ${zipFile.absolutePath} with ${submissionInfoList.size} projects from ${assignmentId}")

            response.setHeader("Content-Disposition", "attachment; filename=${assignmentId}_last_mavenized_submissions.zip")

            return FileSystemResource(zipFile)
        } finally {
            tempFolder.delete()
        }

    }

    @RequestMapping(value = ["/checkPlagiarism/{assignmentId}"], method = [(RequestMethod.GET)])
    fun checkPlagiarism(@PathVariable assignmentId: String, model: ModelMap, principal: Principal): String {

        val assignment = assignmentRepository.findById(assignmentId).orElse(null)
            ?: throw IllegalArgumentException("assignment ${assignmentId} is not registered")
        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)

        if (principal.realName() != assignment.ownerUserId && acl.find { it -> it.userId == principal.realName() } == null) {
            throw IllegalAccessError("Assignment reports can only be accessed by their owner or authorized teachers")
        }

        val submissionInfos = submissionService.getSubmissionsList(assignment, retrieveReport = false)

        // check if there are any submissions marked as final. in that case, consider only final submissions
        // for plagiarismo detection
        val hasSubmissionsMarkedAsFinal = submissionInfos.any { it.lastSubmission.markedAsFinal }
        val submissions = submissionInfos
            .filter { !hasSubmissionsMarkedAsFinal || it.lastSubmission.markedAsFinal }
            .map { it.lastSubmission }

        val tempDir = FileSystemResource(System.getProperty("java.io.tmpdir")).file
        val submissionsToCheckFolder = File(tempDir, "dp-jplag-${assignmentId}-submissions")
        submissionsToCheckFolder.deleteRecursively()  // make sure it doesn't exist
        val plagiarismReportFolder = File(tempDir, "dp-jplag-${assignmentId}-report")
        plagiarismReportFolder.deleteRecursively()  // make sure it doesn't exist
        try {
            jPlagService.prepareSubmissions(submissions, submissionsToCheckFolder)
            LOG.info("Prepared submissions for jplag on ${submissionsToCheckFolder.absolutePath}")

            val result = jPlagService.checkSubmissions(submissionsToCheckFolder, assignment, plagiarismReportFolder)
            LOG.info("Checked submissions using jplag on ${submissionsToCheckFolder.absolutePath}. " +
                    "Wrote report to ${plagiarismReportFolder.absolutePath}.zip")

            // complement comparisons with info about the number of submissions
            for (comparison in result.comparisons) {
                comparison.firstNumTries = submissionInfos
                    .find { it.lastSubmission.id == comparison.firstSubmission.id }?.allSubmissions?.count() ?: -1
                comparison.secondNumTries = submissionInfos
                    .find { it.lastSubmission.id == comparison.secondSubmission.id }?.allSubmissions?.count() ?: -1
            }

            model["assignment"] = assignment
            model["comparisons"] = result.comparisons
            model["ignoredSubmissions"] = result.ignoredSubmissions

            return "teacher-submissions-plagiarism"

        } finally {
            submissionsToCheckFolder.delete()
            // TODO: when to delete plagiarismReportFolder?
        }
    }


    @RequestMapping(value = ["/downloadPlagiarismMatchReport/{assignmentId}"],
        method = [(RequestMethod.GET)], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    @ResponseBody
    fun downloadPlagiarismMatchReport(@PathVariable assignmentId: String, principal: Principal,
                                      response: HttpServletResponse): FileSystemResource {

        val assignment = assignmentRepository.findById(assignmentId).orElse(null)
            ?: throw IllegalArgumentException("assignment ${assignmentId} is not registered")
        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)

        if (principal.realName() != assignment.ownerUserId && acl.find { it.userId == principal.realName() } == null) {
            throw IllegalAccessError("Plagiarism match reports can only be accessed by their owner or authorized teachers")
        }

        val tempDir = FileSystemResource(System.getProperty("java.io.tmpdir")).file
        val plagiarismReportFile = File(tempDir, "dp-jplag-${assignmentId}-report.zip")

        response.setHeader("Content-Disposition", "attachment; filename=dp-jplag-${assignmentId}-report.zip")

        return FileSystemResource(plagiarismReportFile.absoluteFile)
    }

    /**
     * Controller that handles requests for the submissions of the current user.
     * @param model is a [ModelMap] that will be populated with information to use in a View
     * @param principal is a [Principal] representing the user making the request. This is the user whose submissions
     * will be returned.
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/mySubmissions"], method = [(RequestMethod.GET)])
    fun getMySubmissions(model: ModelMap,
                         principal: Principal): String {

        model["studentHistory"] = studentService.getStudentHistory(principal.realName())

        if (model["studentHistory"] == null) {
            model["message"] = "Student with id ${principal.realName()} does not exist"
        }

        return "student-history";
    }

    /**
     * Controller that handles requests related with listing [Submission]s of a certain [Assignment] and
     * [ProjectGroup].
     * @param assignmentId is a String identifying the relevant Assignment
     * @param groupId is a String identifying the relevant ProjectGroup
     * @param model is a [ModelMap] that will be populated with information to use in a View
     * @param principal is a [Principal] representing the user making the request
     * @param request is an [HttpServletRequest]
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/submissions"], method = [(RequestMethod.GET)])
    fun getSubmissions(@RequestParam("assignmentId") assignmentId: String,
                       @RequestParam("groupId") groupId: Long,
                       model: ModelMap, principal: Principal,
                       request: HttpServletRequest): String {

        val assignment = assignmentRepository.findById(assignmentId).orElse(null)
        val group = projectGroupRepository.findById(groupId).orElse(null)

        model["assignment"] = assignment
        model["numSubmissions"] = submissionRepository.countBySubmitterUserIdAndAssignmentId(principal.realName(), assignment.id)

        val submissions = submissionRepository
            .findByGroupAndAssignmentIdOrderBySubmissionDateDescStatusDateDesc(group, assignmentId)
            .filter { it.getStatus() != SubmissionStatus.DELETED }
        for (submission in submissions) {
            val reportElements = submissionReportRepository.findBySubmissionId(submission.id)
            submission.reportElements = reportElements
            submission.overdue = assignment.overdue(submission)
            submission.buildReport?.let {
                    buildReportDB ->
                val mavenizedProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission,
                    submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT)
                val buildReport = buildReportBuilder.build(buildReportDB.buildReport.split("\n"),
                    mavenizedProjectFolder.absolutePath, assignment, submission)
                submission.ellapsed = buildReport.elapsedTimeJUnit()
                submission.teacherTests = buildReport.junitSummaryAsObject(TestType.TEACHER)
                submission.hiddenTests = buildReport.junitSummaryAsObject(TestType.HIDDEN)
            }
            submission.submitterName = authorRepository.findByUserId(submission.submitterUserId)?.last()?.name
        }

        model["group"] = group
        model["submissions"] = submissions

        if (assignment.submissionMethod == SubmissionMethod.GIT && submissions.isNotEmpty()) {
            submissions[0].gitSubmissionId?.let { gitSubmissionId ->
                val gitSubmission = gitSubmissionRepository.findById(gitSubmissionId)
                    .orElseThrow { EntityNotFoundException("GitSubmission $gitSubmissionId not found") }

                val repositoryFolder = File(dropProjectProperties.storage.gitLocation, gitSubmission.getFolderRelativeToStorageRoot())
                val history = gitClient.getHistory(repositoryFolder)
                model["gitHistory"] = history
                model["gitRepository"] = gitClient.convertSSHGithubURLtoHttpURL(gitSubmission.gitRepositoryUrl)
            }
        }

        return "submissions"
    }

    /**
     * Controller that handles the exportation of an Assignment's submission results to a CSV file.
     * @param assignmentId is a String, identifying the relevant Assignment
     * @return A ResponseEntity<String>
     */
    @RequestMapping(value = ["/exportCSV/{assignmentId}"], method = [(RequestMethod.GET)])
    fun exportCSV(@PathVariable assignmentId: String,
                  @RequestParam(name="ellapsed", defaultValue = "true") includeEllapsed: Boolean, principal: Principal): ResponseEntity<String> {

        val assignment = assignmentRepository.findById(assignmentId).orElse(null)
        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)

        if (principal.realName() != assignment.ownerUserId && acl.find { it -> it.userId == principal.realName() } == null) {
            throw IllegalAccessError("Assignment reports can only be accessed by their owner or authorized teachers")
        }

        var headersCSV = LinkedHashSet(mutableListOf("submission id","student id","student name","project structure", "compilation", "code quality"))
        var resultCSV = ""

        val submissions = submissionRepository.findByAssignmentIdAndMarkedAsFinal(assignmentId, true)
            .filter { it.getStatus() != SubmissionStatus.DELETED }
        for (submission in submissions) {
            val reportElements = submissionReportRepository.findBySubmissionId(submission.id)
            submission.reportElements = reportElements
            submission.overdue = assignment.overdue(submission)
            submission.buildReport?.let {
                    buildReportDB ->
                val mavenizedProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission,
                    submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT)
                val buildReport = buildReportBuilder.build(buildReportDB.buildReport.split("\n"),
                    mavenizedProjectFolder.absolutePath, assignment, submission)
                submission.ellapsed = buildReport.elapsedTimeJUnit()
                if (assignment.acceptsStudentTests) {
                    submission.studentTests = buildReport.junitSummaryAsObject(TestType.STUDENT)
                }
                submission.teacherTests = buildReport.junitSummaryAsObject(TestType.TEACHER)
                submission.hiddenTests = buildReport.junitSummaryAsObject(TestType.HIDDEN)
                if (assignment.calculateStudentTestsCoverage && buildReport.jacocoResults.isNotEmpty()) {
                    submission.coverage = buildReport.jacocoResults[0].lineCoveragePercent
                }
            }
        }

        val hasTeacherTests = submissions.any { it.teacherTests != null }
        val hasHiddenTests = submissions.any { it.hiddenTests != null }

        for (submission in submissions) {
            val r1 = submission.reportElements?.getOrNull(0)?.reportValue.orEmpty()  // Project Structure
            val r2 = submission.reportElements?.getOrNull(1)?.reportValue.orEmpty()  // Compilation
            val r3 = submission.reportElements?.getOrNull(2)?.reportValue.orEmpty()  // Code Quality

            var ellapsed = submission.ellapsed
            if (ellapsed != null) {
                ellapsed = ellapsed.setScale(2, RoundingMode.UP)
            }

            for (author in submission.group.authors) {
                resultCSV += "${submission.group.id};${author.userId};${author.name};${r1};${r2};${r3};"

                if (assignment.acceptsStudentTests) {
                    headersCSV.add("student tests")
                    if (submission.studentTests != null) {
                        resultCSV += "${submission.studentTests!!.progress};"
                    } else {
                        resultCSV += ";"
                    }
                }

                if (hasTeacherTests) {
                    headersCSV.add("teacher tests")
                    resultCSV += if (submission.teacherTests != null) "${submission.teacherTests!!.progress};" else ";"
                }

                if (hasHiddenTests) {
                    headersCSV.add("hidden tests")
                    resultCSV += if (submission.hiddenTests != null) "${submission.hiddenTests!!.progress};" else ";"
                }

                if (assignment.calculateStudentTestsCoverage) {
                    headersCSV.add("coverage")
                    resultCSV += if (submission.coverage != null) "${submission.coverage};" else ";"
                }

                if (includeEllapsed) {
                    headersCSV.add("ellapsed")
                    resultCSV += "${ellapsed?.toPlainString().orEmpty()};"
                }

                headersCSV.add("submission date")
                resultCSV += submission.submissionDate.formatDefault() + ";"

                headersCSV.add("# submissions")
                resultCSV += submissionRepository.countByAssignmentIdAndSubmitterUserId(submission.assignmentId, author.userId)

                if (!assignment.mandatoryTestsSuffix.isNullOrEmpty()) {
                    headersCSV.add("# mandatory")
                    resultCSV += ";" + (submission.teacherTests?.numMandatoryOK ?: 0)
                }

                headersCSV.add("overdue")
                resultCSV += ";" + submission.overdue

                resultCSV += "\n"
            }
        }

        resultCSV = headersCSV.joinToString(";") + "\n" + resultCSV

        val headers = HttpHeaders()
        headers.contentType = MediaType("application", "csv")
        headers.setContentDispositionFormData("attachment", "${assignmentId}_final_results.csv");
        return ResponseEntity(resultCSV, headers, HttpStatus.OK);
    }

    /**
     * Controller that handles requests for an [Assignment]'s Leaderboard.
     * @param assignmentId is a String identifying the relevant Assignment
     * @param model is a [ModelMap] that will be populated with information to use in a View
     * @param principal is a [Principal] representing the user making the request
     * @param request is an [HttpServletRequest]
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/leaderboard/{assignmentId}"], method = [(RequestMethod.GET)])
    fun getLeaderboard(@PathVariable assignmentId: String, model: ModelMap,
                       principal: Principal, request: HttpServletRequest): String {

        val assignment = assignmentRepository.findById(assignmentId).orElse(null)
        if (!assignment.showLeaderBoard) {
            throw AccessDeniedException("Leaderboard for this assignment is not turned on")
        } else {
            if (assignment.leaderboardType == null) {  // TODO: Remove this after making this field mandatory
                assignment.leaderboardType = LeaderboardType.TESTS_OK
            }
        }

        val submissionInfoList = submissionService.getSubmissionsList(assignment)

        val comparator: Comparator<Submission> =
            when (assignment.leaderboardType ?: LeaderboardType.TESTS_OK) {
                LeaderboardType.TESTS_OK -> compareBy({ -it.teacherTests!!.progress }, { it.statusDate.time })
                LeaderboardType.ELLAPSED -> compareBy({ -it.teacherTests!!.progress }, { it.ellapsed }, { it.statusDate.time })
                LeaderboardType.COVERAGE -> compareBy({ -it.teacherTests!!.progress }, { -(it.coverage ?: 0) }, { it.statusDate.time })
            }

        val sortedList =
            submissionInfoList
                .map { it.lastSubmission }
                .filter { it.getStatus() in listOf(SubmissionStatus.VALIDATED, SubmissionStatus.VALIDATED_REBUILT) }
                .filter { it.teacherTests?.progress ?: 0 > 0 }
                // compare by progress descending and ellapsed ascending
                .sortedWith( comparator )
                .map {
                    it.reportElements = it.reportElements?.filter { it.indicator == Indicator.TEACHER_UNIT_TESTS }
                    it  // just return itself
                }

        model["assignment"] = assignment
        model["submissions"] = sortedList

        return "leaderboard"
    }

    @RequestMapping(value = ["/studentHistoryForm"], method = [(RequestMethod.GET)])
    fun getStudentHistoryForm(): String {
        return "student-history-form"
    }

    @RequestMapping(value = ["/studentList"], method = [(RequestMethod.GET)], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getStudentList(@RequestParam("q") q: String): ResponseEntity<List<StudentListResponse>> {

        return ResponseEntity(studentService.getStudentList(q), HttpStatus.OK)
    }

    @RequestMapping(value = ["/studentHistory"], method = [(RequestMethod.GET)])
    fun getStudentHistory(@RequestParam("id") studentId: String, model: ModelMap,
                          principal: Principal, request: HttpServletRequest): String {

        model["studentHistory"] = studentService.getStudentHistory(studentId, principal)

        if (model["studentHistory"] == null) {
            model["message"] = "Student with id $studentId does not exist"
        }

        return "student-history";
    }

    /**
     * Controller that handles requests for assets included in the submission such as image files
     */
    @RequestMapping(
        value = ["/buildReport/{submissionId}/{asset}"],
        method = [(RequestMethod.GET)],
        produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE]
    )
    @ResponseBody
    fun downloadSubmissionAsset(
        @PathVariable submissionId: Long,
        @PathVariable asset: String,
        principal: Principal,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): FileSystemResource {

        val submission = submissionRepository.findById(submissionId).orElse(null)

        if (submission != null) {

            // check that principal belongs to the group that made this submission
            if (!request.isUserInRole("TEACHER")) {
                val groupElements = submission.group.authors
                if (groupElements.filter { it.userId == principal.realName() }.isEmpty()) {
                    throw AccessDeniedException("${principal.realName()} is not allowed to download this asset")
                }
            }

            if (submission.getStatus() == SubmissionStatus.DELETED) {
                throw AccessDeniedException("This submission was deleted")
            }

            val mavenizedProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(
                submission,
                submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT
            )

            // remove an eventual "?xxx=yyy" suffix from the asset name
            val sanitizedAssetName = asset.split("?")[0]

            val assetFile = File(mavenizedProjectFolder, sanitizedAssetName)
            if (assetFile.exists()) {
                return FileSystemResource(assetFile)
            }
        }

        throw ResourceNotFoundException()
    }

    @RequestMapping(value = ["/migrate/{idx1}/{idx2}"], method = [(RequestMethod.GET)])
    fun migrate(@PathVariable idx1: Long, @PathVariable idx2: Long) {

//        val submissions = submissionRepository.findByIdBetween(idx1, idx2)
//
//        for (submission in submissions) {
//            if (submission.buildReport != null) {
//                val buildReport = BuildReport(buildReport = submission.buildReport!!)
//                buildReportRepository.save(buildReport)
//
//                submission.buildReportId = buildReport.id
//                submissionRepository.save(submission)
//            }
//        }


//        val assignments = assignmentRepository.findAll()
//
//        for (assignment in assignments) {
//
//            if (assignment.buildReportId != null) {
//                val buildReport = BuildReport(buildReport = assignment.buildReport!!)
//                buildReportRepository.save(buildReport)
//
//                assignment.buildReportId = buildReport.id
//                assignmentRepository.save(assignment)
//            }
//
//        }
    }

//    @RequestMapping(value = ["/size"], method = [(RequestMethod.GET)])
//    fun size() {
//
//        val assignments = assignmentRepository.findAll()
//
//        for (assignment in assignments) {
//
//            var countDeletedFolders = 0
//            var countExistentFolders = 0
//            var totalSize = 0L
//
//            val submissions = submissionRepository.findByAssignmentId(assignment.id)
//
//            for (submission in submissions) {
//                val mavenizedFolder = File(dropProjectProperties.mavenizedProjects.rootLocation + "/" + submission.submissionId + "-mavenized")
//                if (mavenizedFolder.exists()) {
//                    countExistentFolders++
//                    totalSize += FileUtils.sizeOfDirectory(mavenizedFolder)
//                } else {
//                    countDeletedFolders++
//                }
//            }
//
//            LOG.info("*** ${assignment.id} - ${countExistentFolders} - ${countDeletedFolders} (${totalSize / 1_000_000} Mb) ***")
//        }
//
//    }


    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleError(e: ResourceNotFoundException): ResponseEntity<String> {
        LOG.error(e.message)
        return ResponseEntity("Asset not found", HttpStatus.NOT_FOUND);
    }
}
