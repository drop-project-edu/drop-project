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

import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.util.Zip4jConstants
import org.apache.commons.io.FileUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.*
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.dropProject.MAVEN_MAX_EXECUTION_TIME
import org.dropProject.dao.BuildReport
import org.dropProject.dao.Indicator
import org.dropProject.dao.SubmissionStatus
import org.dropProject.data.AuthorDetails
import org.dropProject.data.TestType
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.*
import org.dropProject.services.*
import org.dropProject.storage.StorageService
import org.dropProject.storage.unzip
import org.springframework.security.access.AccessDeniedException
import java.io.File
import java.io.FileWriter
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Paths
import java.security.Principal
import java.util.*
import java.util.logging.Logger
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Controller
class ReportController(
        val authorRepository: AuthorRepository,
        val projectGroupRepository: ProjectGroupRepository,
        val assignmentRepository: AssignmentRepository,
        val assignmentACLRepository: AssignmentACLRepository,
        val submissionRepository: SubmissionRepository,
        val gitSubmissionRepository: GitSubmissionRepository,
        val submissionReportRepository: SubmissionReportRepository,
        val buildReportRepository: BuildReportRepository,
        val assignmentTeacherFiles: AssignmentTeacherFiles,
        val buildReportBuilder: BuildReportBuilder,
        val gitClient: GitClient,
        val submissionService: SubmissionService,
        val storageService: StorageService,
        val zipService: ZipService,
        val templateEngine: TemplateEngine
) {

    @Value("\${mavenizedProjects.rootLocation}")
    val mavenizedProjectsRootLocation: String = ""

    @Value("\${storage.rootLocation}/upload")
    val uploadSubmissionsRootLocation: String = "submissions/upload"

    @Value("\${storage.rootLocation}/git")
    val gitSubmissionsRootLocation: String = "submissions/git"

    val LOG = Logger.getLogger(this.javaClass.name)

    @RequestMapping(value = ["/report/{assignmentId}"], method = [(RequestMethod.GET)])
    fun getReport(@PathVariable assignmentId: String, model: ModelMap,
                  principal: Principal, request: HttpServletRequest): String {
        model["assignmentId"] = assignmentId

        val assignment = assignmentRepository.findOne(assignmentId)
        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)

        if (principal.name != assignment.ownerUserId && acl.find { it -> it.userId == principal.name } == null) {
            throw IllegalAccessError("Assignment reports can only be accessed by their owner or authorized teachers")
        }

        val submissionInfoList = submissionService.getSubmissionsList(assignment)

        if (submissionInfoList.any { it.lastSubmission.coverage != null }) {
            model["hasCoverage"] = true
        }

        model["submissions"] = submissionInfoList
        model["countMarkedAsFinal"] = submissionInfoList.asSequence().filter { it.lastSubmission.markedAsFinal }.count()
        model["isAdmin"] = request.isUserInRole("DROP_PROJECT_ADMIN")

        return "report"
    }


    @RequestMapping(value = ["/buildReport/{submissionId}"], method = [(RequestMethod.GET)])
    fun getSubmissionReport(@PathVariable submissionId: Long, model: ModelMap, principal: Principal,
                            request: HttpServletRequest): String {

        val submission = submissionRepository.findOne(submissionId)

        if (submission != null) {

            // check that principal belongs to the group that made this submission
            if (!request.isUserInRole("TEACHER")) {
                val groupElements = submission.group.authors
                if (groupElements.filter { it -> it.userId == principal.name }.isEmpty()) {
                    throw org.springframework.security.access.AccessDeniedException("${principal.name} is not allowed to view this report")
                }
            }

            model["numSubmissions"] = submissionRepository.countBySubmitterUserIdAndAssignmentId(principal.name, submission.assignmentId)

            val assignment = assignmentRepository.findOne(submission.assignmentId)

            model["assignment"] = assignment

            model["submission"] = submission
            model["gitSubmission"] =
                    if (submission.gitSubmissionId != null) {
                        gitSubmissionRepository.getOne(submission.gitSubmissionId)
                    } else {
                        null
                    }


            // check the submission status
            when (submission.getStatus()) {
                SubmissionStatus.ILLEGAL_ACCESS -> model["error"] = "O projecto não pode aceder a ficheiros fora da pasta no qual é executado"
                SubmissionStatus.FAILED -> model["error"] = "Ocorreu um erro interno a validar o seu projecto. Tente novamente. Caso o problema persista, contacte o administrador."
                SubmissionStatus.ABORTED_BY_TIMEOUT -> model["error"] = "O processo de validação foi abortado pois estava a demorar demasiado. Tempo máximo permitido: ${MAVEN_MAX_EXECUTION_TIME} seg"
                SubmissionStatus.SUBMITTED, SubmissionStatus.SUBMITTED_FOR_REBUILD, SubmissionStatus.REBUILDING -> {
                    model["error"] = "A submissão ainda não foi validada. Aguarde..."
                    model["autoRefresh"] = true
                }
                SubmissionStatus.VALIDATED, SubmissionStatus.VALIDATED_REBUILT -> {
                    val submissionReport = submissionReportRepository.findBySubmissionId(submission.id)

                    // fill the assignment in the reports
                    submissionReport.forEach { it.assignment = assignment }

                    model["summary"] = submissionReport
                    model["structureErrors"] = submission.structureErrors?.split(";") ?: emptyList<String>()

                    val authors = ArrayList<AuthorDetails>()
                    for (authorDB in submission.group.authors) {
                        authors.add(AuthorDetails(name = authorDB.name, number = authorDB.userId,
                                submitter = submission.submitterUserId == authorDB.userId))
                    }
                    model["authors"] = authors

                    if (submission.buildReportId != null) {
                        val mavenizedProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission,
                                submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT)
                        val buildReportDB = buildReportRepository.getOne(submission.buildReportId)
                        model["buildReport"] = buildReportBuilder.build(buildReportDB.buildReport.split("\n"),
                                mavenizedProjectFolder.absolutePath, assignment, submission)
                    }
                }
            }
        }

        model["isTeacher"] = request.isUserInRole("TEACHER")

        return "build-report"
    }

    @RequestMapping(value = ["/downloadMavenProject/{submissionId}"],
            method = [(RequestMethod.GET)], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    @ResponseBody
    fun downloadMavenProject(@PathVariable submissionId: Long, principal: Principal,
                             request: HttpServletRequest, response: HttpServletResponse): FileSystemResource {

        val submission = submissionRepository.findOne(submissionId)
        if (submission != null) {

            // check that principal belongs to the group that made this submission
            if (!request.isUserInRole("TEACHER")) {
                throw org.springframework.security.access.AccessDeniedException("${principal.name} is not allowed to view this report")
            }

            val projectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission,
                    wasRebuilt = submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT)
            LOG.info("[${principal.name}] downloaded ${projectFolder.name}")

            val zipFilename = submission.group.authorsStr().replace(",", "_") + "_mavenized"
            val zipFile = zipService.createZipFromFolder(zipFilename, projectFolder)

            LOG.info("Created ${zipFile.file.absolutePath}")

            response.setHeader("Content-Disposition", "attachment; filename=${zipFilename}.zip")

            return FileSystemResource(zipFile.file)

        } else {
            throw ResourceNotFoundException()
        }
    }


    @RequestMapping(value = ["/downloadOriginalProject/{submissionId}"],
            method = [(RequestMethod.GET)], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    @ResponseBody
    fun downloadOriginalProject(@PathVariable submissionId: Long, principal: Principal,
                                request: HttpServletRequest, response: HttpServletResponse): FileSystemResource {

        val submission = submissionRepository.findOne(submissionId)
        if (submission != null) {

            // check that principal belongs to the group that made this submission
            if (!request.isUserInRole("TEACHER")) {
                val groupElements = submission.group.authors
                if (groupElements.filter { it -> it.userId == principal.name }.isEmpty()) {
                    throw org.springframework.security.access.AccessDeniedException("${principal.name} is not allowed to view this report")
                }
            }

            if (submission.submissionId != null) {
                val projectFolder = File(uploadSubmissionsRootLocation, submission.submissionFolder)
                val projectFile = File("${projectFolder.absolutePath}.zip")  // for every folder, there is a corresponding zip file with the same name

                LOG.info("[${principal.name}] downloaded ${projectFile.name}")

                val filename = submission.group.authorsStr().replace(",", "_")
                response.setHeader("Content-Disposition", "attachment; filename=${filename}.zip")

                return FileSystemResource(projectFile)
            } else {
                val gitSubmission = gitSubmissionRepository.findOne(submission.gitSubmissionId)
                        ?: throw IllegalArgumentException("git submission ${submissionId} is not registered")
                val repositoryFolder = File(gitSubmissionsRootLocation, gitSubmission.getFolderRelativeToStorageRoot())

                val zipFilename = submission.group.authorsStr().replace(",", "_")
                val zFile = File.createTempFile(zipFilename, ".zip")
                if (zFile.exists()) {
                    zFile.delete();
                }
                val zipFile = ZipFile(zFile)
                val zipParameters = ZipParameters()
                zipParameters.isIncludeRootFolder = false
                zipParameters.compressionLevel = Zip4jConstants.DEFLATE_LEVEL_ULTRA
                zipFile.createZipFileFromFolder(repositoryFolder, zipParameters, false, -1)

                LOG.info("Created ${zipFile.file.absolutePath}")

                response.setHeader("Content-Disposition", "attachment; filename=${zipFilename}.zip")

                return FileSystemResource(zipFile.file)
            }

        } else {
            throw ResourceNotFoundException()
        }
    }

    @RequestMapping(value = ["/downloadOriginalAll/{assignmentId}"],
            method = [(RequestMethod.GET)], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    @ResponseBody
    fun downloadOriginalAll(@PathVariable assignmentId: String, principal: Principal,
                            response: HttpServletResponse): FileSystemResource {

        val assignment = assignmentRepository.findOne(assignmentId)
                ?: throw IllegalArgumentException("assignment ${assignmentId} is not registered")
        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)

        if (principal.name != assignment.ownerUserId && acl.find { it -> it.userId == principal.name } == null) {
            throw IllegalAccessError("Assignment reports can only be accessed by their owner or authorized teachers")
        }

        if (assignment.submissionMethod != SubmissionMethod.UPLOAD) {
            throw IllegalArgumentException("downloadOriginalAll is only implemented for assignments whose submissions are through upload")
        }

        val submissionInfoList = submissionService.getSubmissionsList(assignment)

        val tempFolder = Files.createTempDirectory("dp-${assignmentId}").toFile()
        try {
            for (submissionInfo in submissionInfoList) {
                val projectFolder = File(tempFolder, submissionInfo.projectGroup.authorsStr("_"))
                projectFolder.mkdir()

                val submission = submissionInfo.lastSubmission

                val originalProjectFolder = storageService.retrieveProjectFolder(submission)
                        ?: throw IllegalArgumentException("projectFolder for ${submission.submissionId} doesn't exist")

                var hadToUnzip = false
                if (!originalProjectFolder.exists()) {
                    // let's check if there is a zip file with this project
                    val originalProjectZipFile = File("${originalProjectFolder.absolutePath}.zip")
                    if (originalProjectZipFile.exists()) {
                        unzip(Paths.get(originalProjectZipFile.path), originalProjectFolder.name)
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

            LOG.info("Created ${zipFile.file.absolutePath} with ${submissionInfoList.size} projects from ${assignmentId}")

            response.setHeader("Content-Disposition", "attachment; filename=${assignmentId}_last_submissions.zip")

            return FileSystemResource(zipFile.file)
        } finally {
            tempFolder.delete()
        }

    }


    @RequestMapping(value = ["/downloadMavenizedAll/{assignmentId}"],
            method = [(RequestMethod.GET)], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    @ResponseBody
    fun downloadMavenizedAll(@PathVariable assignmentId: String, principal: Principal,
                             response: HttpServletResponse): FileSystemResource {

        val assignment = assignmentRepository.findOne(assignmentId)
                ?: throw IllegalArgumentException("assignment ${assignmentId} is not registered")
        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)

        if (principal.name != assignment.ownerUserId && acl.find { it -> it.userId == principal.name } == null) {
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
                    LOG.warning("${originalProjectFolder.absolutePath} doesn't exist. " +
                            "Probably, it has structure errors. This submission will not be included in the zip file.")
                } else {

                    val projectFolder = File(tempFolder, submissionInfo.projectGroup.authorsStr("_"))
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
                                            val projectGroupStr = submissionInfo.projectGroup.authorsStr("_")
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
            templateEngine.process("download-all-pom", ctx, FileWriter(File(tempFolder, "pom.xml")))

            val zipFilename = tempFolder.name
            val zipFile = zipService.createZipFromFolder(zipFilename, tempFolder)

            LOG.info("Created ${zipFile.file.absolutePath} with ${submissionInfoList.size} projects from ${assignmentId}")

            response.setHeader("Content-Disposition", "attachment; filename=${assignmentId}_last_mavenized_submissions.zip")

            return FileSystemResource(zipFile.file)
        } finally {
            tempFolder.delete()
        }

    }

    @RequestMapping(value = ["/mySubmissions"], method = [(RequestMethod.GET)])
    fun getMySubmissions(@RequestParam("assignmentId") assignmentId: String, model: ModelMap,
                         principal: Principal, request: HttpServletRequest): String {

        val assignment = assignmentRepository.findOne(assignmentId)

        model["username"] = principal.name
        model["isTeacher"] = request.isUserInRole("TEACHER")
        model["assignment"] = assignment
        model["numSubmissions"] = submissionRepository.countBySubmitterUserIdAndAssignmentId(principal.name, assignment.id)

        // TODO this is similar to getSubmissions: refactor
        val submissions = submissionRepository.findBySubmitterUserIdAndAssignmentId(principal.name, assignmentId)
        for (submission in submissions) {
            val reportElements = submissionReportRepository.findBySubmissionId(submission.id)
            submission.reportElements = reportElements
            if (submission.buildReportId != null) {
                val mavenizedProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission,
                        submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT)
                val buildReportDB = buildReportRepository.getOne(submission.buildReportId)
                val buildReport = buildReportBuilder.build(buildReportDB.buildReport.split("\n"),
                        mavenizedProjectFolder.absolutePath, assignment, submission)
                submission.ellapsed = buildReport.elapsedTimeJUnit()
                submission.teacherTests = buildReport.junitSummaryAsObject()
            }
        }

        model["submissions"] = submissions

        return "submissions"
    }

    @RequestMapping(value = ["/submissions"], method = [(RequestMethod.GET)])
    fun getSubmissions(@RequestParam("assignmentId") assignmentId: String,
                       @RequestParam("groupId") groupId: Long,
                       model: ModelMap, principal: Principal,
                       request: HttpServletRequest): String {

        val assignment = assignmentRepository.findOne(assignmentId)
        val group = projectGroupRepository.findOne(groupId)

        model["username"] = principal.name
        model["isTeacher"] = request.isUserInRole("TEACHER")
        model["assignment"] = assignment
        model["numSubmissions"] = submissionRepository.countBySubmitterUserIdAndAssignmentId(principal.name, assignment.id)

        val submissions = submissionRepository.findByGroupAndAssignmentIdOrderBySubmissionDateDescStatusDateDesc(group, assignmentId)
        for (submission in submissions) {
            val reportElements = submissionReportRepository.findBySubmissionId(submission.id)
            submission.reportElements = reportElements
            if (submission.buildReportId != null) {
                val mavenizedProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission,
                        submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT)
                val buildReportDB = buildReportRepository.getOne(submission.buildReportId)
                val buildReport = buildReportBuilder.build(buildReportDB.buildReport.split("\n"),
                        mavenizedProjectFolder.absolutePath, assignment, submission)
                submission.ellapsed = buildReport.elapsedTimeJUnit()
                submission.teacherTests = buildReport.junitSummaryAsObject(TestType.TEACHER)
                submission.hiddenTests = buildReport.junitSummaryAsObject(TestType.HIDDEN)
            }
        }

        model["group"] = group
        model["submissions"] = submissions

        if (assignment.submissionMethod == SubmissionMethod.GIT && !submissions.isEmpty()) {
            val gitSubmission = gitSubmissionRepository.getOne(submissions[0].gitSubmissionId)

            val repositoryFolder = File(gitSubmissionsRootLocation, gitSubmission.getFolderRelativeToStorageRoot())
            val history = gitClient.getHistory(repositoryFolder)
            model["gitHistory"] = history
            model["gitRepository"] = gitClient.convertSSHGithubURLtoHttpURL(gitSubmission.gitRepositoryUrl)
        }

        return "submissions"
    }


    @RequestMapping(value = ["/exportCSV/{assignmentId}"], method = [(RequestMethod.GET)])
    fun exportCSV(@PathVariable assignmentId: String, principal: Principal): ResponseEntity<String> {

        val assignment = assignmentRepository.findOne(assignmentId)
        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)

        if (principal.name != assignment.ownerUserId && acl.find { it -> it.userId == principal.name } == null) {
            throw IllegalAccessError("Assignment reports can only be accessed by their owner or authorized teachers")
        }

        var resultCSV = ""

        val submissions = submissionRepository.findByAssignmentIdAndMarkedAsFinal(assignmentId, true)
        for (submission in submissions) {
            val reportElements = submissionReportRepository.findBySubmissionId(submission.id)
            submission.reportElements = reportElements
            if (submission.buildReportId != null) {
                val mavenizedProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission,
                        submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT)
                val buildReportDB = buildReportRepository.getOne(submission.buildReportId)
                val buildReport = buildReportBuilder.build(buildReportDB.buildReport.split("\n"),
                        mavenizedProjectFolder.absolutePath, assignment, submission)
                submission.ellapsed = buildReport.elapsedTimeJUnit()
                submission.teacherTests = buildReport.junitSummaryAsObject(TestType.TEACHER)
                submission.hiddenTests = buildReport.junitSummaryAsObject(TestType.HIDDEN)
                if (buildReport.jacocoResults.isNotEmpty()) {
                    submission.coverage = buildReport.jacocoResults[0].lineCoveragePercent
                }
            }

            val r1 = reportElements.getOrNull(0)?.reportValue.orEmpty()
            val r2 = reportElements.getOrNull(1)?.reportValue.orEmpty()
            val r3 = reportElements.getOrNull(2)?.reportValue.orEmpty()

            var ellapsed = submission.ellapsed
            if (ellapsed != null) {
                ellapsed = ellapsed.setScale(2, RoundingMode.UP)
            }

            for (author in submission.group.authors) {
                resultCSV += "${submission.group.id};${author.userId};${author.name};${r1};${r2};${r3};" +
                        "${submission.teacherTests?.toStr().orEmpty()};${ellapsed?.toPlainString().orEmpty()}"
                if (submission.hiddenTests != null) {
                    resultCSV += ";${submission.hiddenTests!!.toStr()}"
                }
                if (submission.coverage != null) {
                    resultCSV += ";${submission.coverage}"
                }
                resultCSV += "\n"
            }
        }

        val headers = HttpHeaders()
        headers.contentType = MediaType("application", "csv")
        headers.setContentDispositionFormData("attachment", "${assignmentId}_final_results.csv");
        return ResponseEntity(resultCSV, headers, HttpStatus.OK);
    }

    @RequestMapping(value = ["/leaderboard/{assignmentId}"], method = [(RequestMethod.GET)])
    fun getLeaderboard(@PathVariable assignmentId: String, model: ModelMap,
                  principal: Principal, request: HttpServletRequest): String {

        val assignment = assignmentRepository.findOne(assignmentId)
        if (!assignment.showLeaderBoard) {
            throw AccessDeniedException("Leaderboard for this assignment is not turned on")
        }

        val submissionInfoList = submissionService.getSubmissionsList(assignment)

        val sortedList =
            submissionInfoList
                .map { it.lastSubmission }
                .filter { it.getStatus() in listOf(SubmissionStatus.VALIDATED, SubmissionStatus.VALIDATED_REBUILT) }
                .filter { it.teacherTests?.progress ?: 0 > 0 }
                // compare by progress descending and ellapsed ascending
                .sortedWith( compareBy( { -it.teacherTests!!.progress }, { it.ellapsed } ))
                .map {
                    it.reportElements = it.reportElements?.filter { it.indicator == Indicator.TEACHER_UNIT_TESTS }
                    it  // just return itself
                }

        model["assignmentId"] = assignmentId
        model["submissions"] = sortedList
        model["isTeacher"] = request.isUserInRole("TEACHER")

        return "leaderboard"
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
}
    
    
