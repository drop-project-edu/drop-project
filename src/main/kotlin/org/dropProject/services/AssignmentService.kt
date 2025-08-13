/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2020 Pedro Alves
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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import jakarta.persistence.EntityNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.apache.commons.io.FileUtils
import org.dropProject.Constants
import org.dropProject.config.PendingTasks
import org.dropProject.dao.*
import org.dropProject.dao.BuildReport
import org.dropProject.data.*
import org.dropProject.extensions.formatJustDate
import org.dropProject.extensions.realName
import org.dropProject.forms.AssignmentForm
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.*
import org.eclipse.jgit.api.Git
import org.kohsuke.github.GitHub
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Async
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.ui.ModelMap
import java.io.File
import java.nio.file.Files
import java.security.Principal
import java.util.*

data class AssignmentImportResult(val type: String, val message: String, val redirectUrl: String)

/**
 * AssignmentService provides [Assignment] related functionality (e.g. list of assignments).
 */
@Service
class AssignmentService(
    val assignmentRepository: AssignmentRepository,
    val assignmentReportRepository: AssignmentReportRepository,
    val assignmentACLRepository: AssignmentACLRepository,
    val submissionRepository: SubmissionRepository,
    val gitSubmissionRepository: GitSubmissionRepository,
    val assigneeRepository: AssigneeRepository,
    val submissionService: SubmissionService,
    val assignmentTestMethodRepository: AssignmentTestMethodRepository,
    val submissionReportRepository: SubmissionReportRepository,
    val assignmentTagRepository: AssignmentTagRepository,
    val buildReportRepository: BuildReportRepository,
    val jUnitReportRepository: JUnitReportRepository,
    val jacocoReportRepository: JacocoReportRepository,
    val projectGroupRestrictionsRepository: ProjectGroupRestrictionsRepository,
    val zipService: ZipService,
    val pendingTasks: PendingTasks,
    val projectGroupService: ProjectGroupService,
    val gitClient: GitClient,
    val assignmentTeacherFiles: AssignmentTeacherFiles
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    @Value("\${assignments.rootLocation}")
    val assignmentsRootLocation: String = ""

    @Value("\${mavenizedProjects.rootLocation}")
    val mavenizedProjectsRootLocation: String = ""

    @Value("\${storage.rootLocation}/upload")
    val uploadSubmissionsRootLocation: String = "submissions/upload"

    @Value("\${storage.rootLocation}/git")
    val gitSubmissionsRootLocation: String = "submissions/git"

    @Value("\${github.token:no-token}")
    val githubToken: String = ""

    /**
     * Returns the [Assignment]s that a certain user can access. The returned assignments will be all the public ones,
     * the one that are owned by the user and also the ones that the user has been given access to.
     * @param principal is a [Principal], representing the user whose assignments shall be retrieved.
     * @param archived is a Boolean. If true, only archived Assignment(s) will be returned. Otherwise, only
     * non-archived Assignment(s) will be returned.
     * @return An [ArrayList] of Assignment(s)
     */
    @Cacheable(
        value = [Constants.CACHE_ARCHIVED_ASSIGNMENTS_KEY],
        key = "#principal.name",
        condition = "#archived==true")
    fun getMyAssignments(principal: Principal, archived: Boolean): List<Assignment> {
        val assignmentsOwns = assignmentRepository.findAllByOwnerUserId(principal.realName())
        val assignmentsPublic = assignmentRepository.findAllByVisibility(AssignmentVisibility.PUBLIC)

        val assignmentsACL = assignmentACLRepository.findByUserId(principal.realName()).mapNotNull {
            assignmentRepository.findById(it.assignmentId).orElse(null)
        }

        val assignmentsAssignee = assigneeRepository.findByAuthorUserId(principal.realName()).mapNotNull {
            assignmentRepository.findById(it.assignmentId).orElse(null)
        }

        val assignments = HashSet<Assignment>()  // use HashSet to remove duplicates
        assignments.addAll(assignmentsOwns)
        assignments.addAll(assignmentsPublic)
        assignments.addAll(assignmentsACL)
        assignments.addAll(assignmentsAssignee)

        val filteredAssigments = assignments.filter { it.archived == archived }.sortedBy { it.id }
        return filteredAssigments
    }

    /**
     * Collects into [model] information about all the [Submission]s related with a certain [Assignment].
     * @param assignmentId is a String identifying the relevant assignment.
     * @param principal is a [Principal] representing the user making the request.
     * @param model is a [ModelMap] that will be populated with information to use in a View.
     * @param request is a [HttpServletRequest]
     * @param includeTestDetails is a Boolean, indicating if test-matrix information should be included.
     * @param mode is a String which indicates the page that is being served and influences the information that is
     * placed in the model. Possible values are:
     * - "summary" - meaning that the data is being loaded for the "Summary" page;
     * - "testMatrix" - meaning that the data is being loaded for the "Test Matrix" page; and
     * - "signalledSubmissions" - meaning that the data is being loaded for the "Signalled Groups" page.
     */
    fun getAllSubmissionsForAssignment(assignmentId: String, principal: Principal, model: ModelMap,
                                       request: HttpServletRequest, includeTestDetails: Boolean = false,
                                       mode: String) {
        val assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow { EntityNotFoundException("Assignment $assignmentId not found") }

        model["assignment"] = assignment

        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)

        if (principal.realName() != assignment.ownerUserId && acl.find { it.userId == principal.realName() } == null) {
            throw IllegalAccessError("Assignment reports can only be accessed by their owner or authorized teachers")
        }

        val submissionInfoList = submissionService.getSubmissionsList(assignment)

        if (submissionInfoList.any { it.lastSubmission.coverage != null }) {
            model["hasCoverage"] = true
        }

        if (includeTestDetails) {
            val assignmentTests = assignmentTestMethodRepository.findByAssignmentId(assignmentId)

            if (assignmentTests.isEmpty()) {
                model["message"] = "No information about tests for this assignment"
            } else {
                // calculate how many submissions pass each test
                val testCounts = assignmentTests.map { "${it.testMethod}:${it.testClass}" to 0 }.toMap(LinkedHashMap())
                var hashMap : HashMap<ProjectGroup, java.util.ArrayList<String>> = HashMap()

                var submissionStatistics = mutableListOf<GroupSubmissionStatistics>()

                submissionInfoList.forEach {

                    var passedTests = 0
                    var failedTests = 0

                    val group = it.projectGroup
                    var failed = java.util.ArrayList<String>()

                    it.lastSubmission.testResults?.forEach {
                        if (it.type == JUnitMethodResultType.SUCCESS) {
                            testCounts.computeIfPresent("${it.methodName}:${it.getClassName()}") { _, v -> v + 1 }
                            passedTests++
                        }
                        else {
                            failed.add(it.methodName)
                            failedTests++
                        }
                    }

                    if(submissionCompilledCorrectly(it.lastSubmission)) {
                        if (!failed.isEmpty()) {
                            hashMap.put(group, failed)
                        }
                        val groupStats = GroupSubmissionStatistics(group.id, passedTests, it.allSubmissions.size, group)
                        submissionStatistics.add(groupStats)
                    }
                }

                model["tests"] = testCounts

                if(mode == "signalledSubmissions") {
                    val signalledGroups = groupGroupsByFailures(hashMap);
                    if(signalledGroups.isEmpty()) {
                        if(model["message"] == null) {
                            model["message"] = "No groups identified as similar"
                        }
                    }
                    else {
                        model["signalledGroups"] = signalledGroups
                    }

                    var nrTests = assignmentTests.size
                    var assignmentStatistics = computeStatistics(submissionStatistics, nrTests)
                    var groupsOutsideNorm = assignmentStatistics.identifyGroupsOutsideStatisticalNorms()
                    if(groupsOutsideNorm.size > 0) {
                        // FIXME: maybe do the rounding to two decimal places in the Thymeleaf / View file
                        model["offTheAverage"] = groupsOutsideNorm
                        val df = java.text.DecimalFormat("#.##")
                        model["assignmentAverageSubmissions"] = df.format(assignmentStatistics.average)
                        model["assignmentStandardDeviation"] = df.format(assignmentStatistics.standardDeviation)
                        val threshold = (assignmentStatistics.average - assignmentStatistics.standardDeviation)
                        model["submissionsThreshold"] = df.format(threshold)
                        model["assignmentNrOfTests"] = nrTests
                    }
                    else {
                        model["otherMessage"] = "No groups outside norms"
                    }
                }
            }
        }

        model["submissions"] = submissionInfoList
        model["countMarkedAsFinal"] = submissionInfoList.asSequence().filter { it.lastSubmission.markedAsFinal }.count()
        model["isAdmin"] = request.isUserInRole("DROP_PROJECT_ADMIN")
        model["mode"] = mode
    }

    /**
     * Checks if a Submission was compiled correctly.
     * @param submission is a [Submission]
     * @return a Boolean
     */
    fun submissionCompilledCorrectly(submission: Submission): Boolean {
        val reports = submissionReportRepository.findBySubmissionId(submission.id)
        for(report in reports) {
            if (report.indicator == Indicator.COMPILATION) {
                return report.reportValue == "OK";
            }
        }
        return false;
    }

    /**
     * Identifies and joins into a group the student groups that are failing the same unit tests.
     *
     * @param failuresByGroup is an [HashMap] with a [ProjectGroup] as key and an [ArrayList] of Strings as value. Each
     * String in the ArrayList represents the name of a unit test that the group fails.
     * @return a [List] of [GroupedProjectsGroup]s
     */
    public fun groupGroupsByFailures(failuresByGroup: HashMap<ProjectGroup, java.util.ArrayList<String>>): List<GroupedProjectGroups> {

        val projectGroupsByFailures = mutableMapOf<String, java.util.ArrayList<ProjectGroup>>()

        // first, build an HashMap where
        // the key is going to be all the test names concatenated into a String
        // (e.g. "test01, test02" and "test01, test03, test05")
        // and the value is going to be the groups that fail those lists
        for ((projectGroup, failures) in failuresByGroup) {
            failures.sort()

            val key: String = failures.joinToString()

            if (projectGroupsByFailures.containsKey(key)) {
                val groups: java.util.ArrayList<ProjectGroup>? = projectGroupsByFailures.get(key)
                groups?.add(projectGroup)
                if (groups != null) {
                    projectGroupsByFailures.put(key, groups)
                }
            } else {
                val newList: java.util.ArrayList<ProjectGroup> = java.util.ArrayList<ProjectGroup>()
                newList.add(projectGroup)
                projectGroupsByFailures.put(key, newList)
            }
        }

        val result = mutableListOf<GroupedProjectGroups>()

        // second, using the newly created HashMap, create a list of
        // GroupedProjectGroups
        for ((failures, groups) in projectGroupsByFailures) {
            val failedTestNames = failures.split(", ")
            // when there is only one ProjectGroup with a specific set of failures, it will be ignored
            if(groups.size > 1) {
                result.add(GroupedProjectGroups(groups, failedTestNames))
            }
        }
        return result
    }

    /**
     * Updates an existing Assignment with the contents of an AssignmentForm.
     * @param existingAssignment, the Assignment that will be updated
     * @param assignmentForm, the AssignmentForm from which the Assignment contents will be copied
     */
    fun updateAssignment(existingAssignment: Assignment, assignmentForm: AssignmentForm) {
        existingAssignment.name = assignmentForm.assignmentName!!
        existingAssignment.packageName = assignmentForm.assignmentPackage
        existingAssignment.language = assignmentForm.language!!
        existingAssignment.dueDate = if (assignmentForm.dueDate != null) java.sql.Timestamp.valueOf(assignmentForm.dueDate) else null
        existingAssignment.submissionMethod = assignmentForm.submissionMethod!!
        existingAssignment.acceptsStudentTests = assignmentForm.acceptsStudentTests
        existingAssignment.minStudentTests = assignmentForm.minStudentTests
        existingAssignment.calculateStudentTestsCoverage = assignmentForm.calculateStudentTestsCoverage
        existingAssignment.mandatoryTestsSuffix = assignmentForm.mandatoryTestsSuffix
        existingAssignment.cooloffPeriod = assignmentForm.cooloffPeriod
        existingAssignment.maxMemoryMb = assignmentForm.maxMemoryMb
        existingAssignment.showLeaderBoard = assignmentForm.leaderboardType != null
        existingAssignment.hiddenTestsVisibility = assignmentForm.hiddenTestsVisibility
        existingAssignment.leaderboardType = assignmentForm.leaderboardType
        existingAssignment.visibility = assignmentForm.visibility

        // remove projectGroupRestrictions if minGroupSize was updated to null
        if (assignmentForm.minGroupSize == null && existingAssignment.projectGroupRestrictions != null) {
            val projectGroupRestrictions = existingAssignment.projectGroupRestrictions!!
            existingAssignment.projectGroupRestrictions = null
            projectGroupRestrictionsRepository.delete(projectGroupRestrictions)
        }

        if (assignmentForm.minGroupSize != null) {
            if (existingAssignment.projectGroupRestrictions != null) {
                existingAssignment.projectGroupRestrictions!!.minGroupSize = assignmentForm.minGroupSize!!
                existingAssignment.projectGroupRestrictions!!.maxGroupSize = assignmentForm.maxGroupSize
                existingAssignment.projectGroupRestrictions!!.exceptions = assignmentForm.exceptions
                projectGroupRestrictionsRepository.save(existingAssignment.projectGroupRestrictions!!)
            } else {
                val newProjectGroupRestrictions = ProjectGroupRestrictions(minGroupSize = assignmentForm.minGroupSize!!,
                    maxGroupSize = assignmentForm.maxGroupSize, exceptions = assignmentForm.exceptions)
                projectGroupRestrictionsRepository.save(newProjectGroupRestrictions)
                existingAssignment.projectGroupRestrictions = newProjectGroupRestrictions
            }
        }

        // update tags
        val tagNames = assignmentForm.assignmentTags?.lowercase(Locale.getDefault())?.split(",")
        clearAllTags(existingAssignment)
        tagNames?.forEach {
            addTagToAssignment(existingAssignment, it)
        }
    }

    /**
     * Handles the exportation of an assignment and (optionally) its submissions
     * @return a pair with (filename, file)
     *
     * NOTE: If you change the name of this method, update MyAsyncUncaughtExceptionHandler
     */
    @Async
    @Transactional
    fun exportAssignment(assignmentId: String, includeSubmissions: Boolean, taskId: String) {

        val assignment = assignmentRepository.findById(assignmentId).orElse(null)
            ?: throw IllegalArgumentException("assignment ${assignmentId} is not registered")
        assignment.authorizedStudentIds = assigneeRepository.findByAssignmentId(assignmentId).map { it.authorUserId }

        val submissionsExport = mutableListOf<SubmissionExport>()
        val gitSubmissionsExport = mutableListOf<GitSubmissionExport>()
        if (includeSubmissions) {
            val submissions = submissionRepository.findByAssignmentId(assignment.id)

            // for each submission, create the corresponding "full" SubmissionExport object
            submissions.forEach {
                with(it) {
                    val buildReport =
                        if (buildReport != null) buildReport!!.buildReport else null
                    val submissionReport = submissionReportRepository.findBySubmissionId(id).map { eachReport ->
                        SubmissionExport.SubmissionReport(
                            eachReport.reportKey, eachReport.reportValue,
                            eachReport.reportProgress, eachReport.reportGoal
                        )
                    }
                    val junitReports = jUnitReportRepository.findBySubmissionId(id)?.map { jUnitReport ->
                        SubmissionExport.JUnitReport(jUnitReport.fileName, jUnitReport.xmlReport)
                    }
                    val jacocoReports = jacocoReportRepository.findBySubmissionId(id)?.map { jacocoReport ->
                        SubmissionExport.JacocoReport(jacocoReport.fileName, jacocoReport.csvReport)
                    }
                    val submissionExport = SubmissionExport(
                        id = id, submissionId = submissionId,
                        gitSubmissionId = gitSubmissionId, submissionFolder = submissionFolder,
                        submissionDate = submissionDate, submitterUserId = submitterUserId, status = getStatus().code,
                        statusDate = statusDate, assignmentId = assignmentId, assignmentGitHash = assignmentGitHash,
                        buildReport = buildReport, structureErrors = structureErrors, markedAsFinal = markedAsFinal,
                        authors = group.authors.map { author -> SubmissionExport.Author(author.userId, author.name) },
                        submissionReport = submissionReport,
                        junitReports = junitReports, jacocoReports = jacocoReports, submissionMode = submissionMode
                    )
                    submissionsExport.add(submissionExport)
                }
            }

            if (assignment.submissionMethod == SubmissionMethod.GIT) {
                val gitSubmissions = gitSubmissionRepository.findByAssignmentIdAndConnected(assignmentId, connected = true)
                gitSubmissions.forEach {
                    with(it) {
                        val gitSubmissionExport = GitSubmissionExport(
                            assignmentId = assignmentId, submitterUserId = submitterUserId,
                            createDate = createDate, connected = connected, lastCommitDate = lastCommitDate,
                            gitRepositoryUrl = gitRepositoryUrl, gitRepositoryPubKey = gitRepositoryPubKey,
                            gitRepositoryPrivKey = gitRepositoryPrivKey,
                            authors = group.authors.map { author ->
                                GitSubmissionExport.Author(
                                    author.userId,
                                    author.name
                                )
                            }
                        )

                        gitSubmissionsExport.add(gitSubmissionExport)
                    }
                }
            }
        }

        val fileName = "${assignment.id}_${Date().formatJustDate()}"
        val tempFolder = Files.createTempDirectory(fileName).toFile()
        val submissionsJsonFile = File(tempFolder, EXPORTED_SUBMISSIONS_JSON_FILENAME)
        val gitSubmissionsJsonFile = File(tempFolder, EXPORTED_GIT_SUBMISSIONS_JSON_FILENAME)
        val assignmentJsonFile = File(tempFolder, EXPORTED_ASSIGNMENT_JSON_FILENAME)
        val originalSubmissionsFolder = File(tempFolder, EXPORTED_ORIGINAL_SUBMISSIONS_FOLDER)
        originalSubmissionsFolder.mkdirs()

        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

        try {
            mapper.writeValue(assignmentJsonFile, assignment)
            if (includeSubmissions) {
                mapper.writeValue(submissionsJsonFile, submissionsExport)
                if (!gitSubmissionsExport.isEmpty()) {
                    mapper.writeValue(gitSubmissionsJsonFile, gitSubmissionsExport)
                }
            }

            exportOriginalSubmissionFilesTo(assignment, originalSubmissionsFolder)

            val zipFile = zipService.createZipFromFolder(tempFolder.name, tempFolder)
            LOG.info("Created ${zipFile.absolutePath} with submissions from ${assignment.id}")

            // put the result in the pending tasks so that the others can check it later
            pendingTasks.put(taskId, Pair(fileName, zipFile))
        } finally {
            tempFolder.delete()
        }
    }

    fun exportOriginalSubmissionFilesTo(assignment: Assignment, destinationFolder: File) {

        if (assignment.submissionMethod == SubmissionMethod.UPLOAD) {

            val submissions = submissionRepository.findByAssignmentId(assignment.id)
            submissions.forEachIndexed { index, it ->
                with(it) {
                    if (submissionId != null && submissionFolder != null) {
                        val projectFolderFrom = File(uploadSubmissionsRootLocation, submissionFolder)
                        val projectFolderTo = File(destinationFolder, submissionFolder.removeSuffix(submissionId))
                        projectFolderTo.mkdirs()

                        // for every folder, there is a corresponding zip file with the same name
                        val projectFileFrom = File("${projectFolderFrom.absolutePath}.zip")

                        if (!projectFileFrom.exists()) {
                            LOG.warn("Did not found original file for submission $id - ${projectFileFrom.absolutePath}")
                        }

                        FileUtils.copyFileToDirectory(projectFileFrom, projectFolderTo)
                        LOG.info("Copied ${projectFileFrom.absolutePath} to ${projectFolderTo.absolutePath} (${index + 1}/${submissions.size})")
                    }
                }
            }

        } else if (assignment.submissionMethod == SubmissionMethod.GIT) {

            val gitSubmissions = gitSubmissionRepository.findByAssignmentIdAndConnected(assignment.id, connected = true)
            gitSubmissions.forEachIndexed { index, it ->
                val repositoryFolderFrom = File(gitSubmissionsRootLocation, it.getFolderRelativeToStorageRoot())
                val repositoryFolderTo = File(destinationFolder, it.getParentFolderRelativeToStorageRoot())
                repositoryFolderTo.mkdirs()

                if (!repositoryFolderFrom.exists()) {
                    LOG.warn("Did not found original file for submission $assignment.id - ${repositoryFolderFrom.absolutePath}")
                }

                FileUtils.copyDirectoryToDirectory(repositoryFolderFrom, repositoryFolderTo)
                LOG.info("Copied ${repositoryFolderFrom.absolutePath} to ${repositoryFolderTo.absolutePath} (${index + 1}/${gitSubmissions.size})")
            }

        } else {
            throw Exception("Invalid submission method for assignment ${assignment.id}")
        }
    }

    fun importAssignment(mapper: ObjectMapper, assignmentJSONFile: File, submissionsJSONFile: File,
                         gitSubmissionsJSONFile: File,
                         originalSubmissionsFolder: File,
                         principal: Principal): AssignmentImportResult {

        val (assignmentId, errorMessage) = createAssignmentFromImportedFile(mapper, assignmentJSONFile, principal)

        if (errorMessage != null) {
            return AssignmentImportResult("error", errorMessage, "redirect:/assignment/import")
        } else {
            LOG.info("Imported $assignmentId")
        }

        if (submissionsJSONFile.exists()) {
            val errorMessage2 = importSubmissionsFromImportedFile(mapper, submissionsJSONFile)
            if (errorMessage2 != null) {
                return AssignmentImportResult("error", errorMessage2, "redirect:/assignment/import")
            }

            if (gitSubmissionsJSONFile.exists()) {
                val errorMessage3 = importGitSubmissionsFromImportedFile(mapper, gitSubmissionsJSONFile)
                if (errorMessage3 != null) {
                    return AssignmentImportResult("error", errorMessage3, "redirect:/assignment/import")
                }
            }

            // import all the original submission files
            if (originalSubmissionsFolder.exists()) {
                val assignment = assignmentRepository.findById(assignmentId)
                    .orElseThrow { EntityNotFoundException("Assignment ${assignmentId} not found") }
                when (assignment.submissionMethod) {
                    SubmissionMethod.UPLOAD -> FileUtils.copyDirectory(originalSubmissionsFolder, File(uploadSubmissionsRootLocation))
                    SubmissionMethod.GIT -> FileUtils.copyDirectory(originalSubmissionsFolder, File(gitSubmissionsRootLocation))
                }
            }
            return AssignmentImportResult("message", "Imported successfully ${assignmentId} and all its submissions",
                "redirect:/report/${assignmentId}")
        } else {
            return AssignmentImportResult("message", "Imported successfully ${assignmentId}. Submissions were not imported",
                "redirect:/assignment/info/${assignmentId}")
        }

    }

    fun importSubmissionsFromImportedFile(mapper: ObjectMapper,
                                          submissionsJSONFile: File): String? {

        val submissions = mapper.readValue(submissionsJSONFile, object : TypeReference<List<SubmissionExport>?>() {})

        if (submissions.isNullOrEmpty()) {
            return "Error: File doesn't contain submissions"
        }

        // find the assignmentId and make sure it exists
        val assignmentId = submissions[0].assignmentId
        if (assignmentRepository.findById(assignmentId).isEmpty) {
            return "Error: You are importing submissions to an assignment ($assignmentId) that doesn't exist. " +
                    "First, please create that assignment."

        }

        // make sure there are no submissions for this assignment
        val count = submissionRepository.countByAssignmentIdAndStatusNot(assignmentId, SubmissionStatus.DELETED.code)
        if (count > 0) {
            return "Error: You are importing submissions to an assignment ($assignmentId) that already has $count submissions. " +
                    "First, please make sure the assignment is empty."
        }

        submissions.forEachIndexed { index, it ->
            val authorDetailsList = it.authors.map { a -> AuthorDetails(a.name, a.userId) }
            val group = projectGroupService.getOrCreateProjectGroup(authorDetailsList)

            val buildReport: BuildReport? =
                if (it.buildReport != null) {
                    val buildReport = BuildReport(buildReport = it.buildReport!!)
                    buildReportRepository.save(buildReport)
                    buildReport
                } else {
                    null
                }

            val submission = Submission(
                submissionId = it.submissionId, submissionDate = it.submissionDate,
                status = it.status, statusDate = it.statusDate, assignmentId = it.assignmentId,
                assignmentGitHash = it.assignmentGitHash,
                submitterUserId = it.submitterUserId,
                submissionFolder = it.submissionFolder,
                gitSubmissionId = it.gitSubmissionId,
                buildReport = buildReport,
                structureErrors = it.structureErrors,
                markedAsFinal = it.markedAsFinal,
                submissionMode = it.submissionMode
            )

            submission.group = group
            submissionRepository.save(submission)

            val reportElements: List<SubmissionReport> = it.submissionReport.map { r ->
                val reportDB = SubmissionReport(
                    submissionId = submission.id, reportKey = r.key,
                    reportValue = r.value, reportProgress = r.progress, reportGoal = r.goal
                )
                submissionReportRepository.save(reportDB)
                reportDB
            }

            submission.reportElements = reportElements
            submissionRepository.save(submission)

            it.junitReports?.forEach { r ->
                jUnitReportRepository.save(JUnitReport(submissionId = submission.id, fileName = r.filename,
                    xmlReport = r.xmlReport))
            }

            it.jacocoReports?.forEach { r ->
                jacocoReportRepository.save(JacocoReport(submissionId = submission.id, fileName = r.filename,
                    csvReport = r.csvReport))
            }

            // update assignment metrics
            val assignment = assignmentRepository.getReferenceById(assignmentId)
            assignment.numSubmissions = submissionRepository.countByAssignmentIdAndStatusNot(assignment.id, SubmissionStatus.DELETED.code).toInt()
            if (assignment.numSubmissions > 0) {
                assignment.lastSubmissionDate =
                    submissionRepository.findFirstByAssignmentIdOrderBySubmissionDateDesc(assignment.id).submissionDate
            }
            assignment.numUniqueSubmitters = submissionRepository.findUniqueSubmittersByAssignmentId(assignment.id).toInt()
            assignmentRepository.save(assignment)

            LOG.info("Imported submission $submission.id ($index/${submissions.size})")
        }

        return null
    }

    /**
     * @return a Pair where the first item is the assignmentId and the second is null
     * if the import succeeded or an error message it it failed
     */
    fun createAssignmentFromImportedFile(mapper: ObjectMapper,
                                         assignmentJSONFile: File,
                                         principal: Principal): Pair<String,String?> {

        val newAssignment = mapper.readValue(assignmentJSONFile, Assignment::class.java)

        // check if already exists an assignment with this id
        if (assignmentRepository.findById(newAssignment.id).orElse(null) != null) {
            return Pair(newAssignment.id, "Error: There is already an assignment with this id (${newAssignment.id})")
        }

        if (assignmentRepository.findByGitRepositoryFolder(newAssignment.gitRepositoryFolder) != null) {
            return Pair(newAssignment.id, "Error: There is already an assignment with this git repository folder")
        }

        newAssignment.ownerUserId = principal.realName()  // new assignment is now owned by who uploads

        val gitRepository = newAssignment.gitRepositoryUrl
        try {
            cloneAssignment(newAssignment, gitRepository)
        } catch (e: Exception) {

            if (githubToken != "no-token") {  // "no-token" is the default value
                LOG.info(
                    "Error cloning ${gitRepository} - ${e}. Maybe the SSH key was removed. Let's try setting the key" +
                            "using github API"
                )

                val github = GitHub.connectUsingOAuth(githubToken)
                val (username, reponame) = gitClient.getGitRepoInfo(newAssignment.gitRepositoryUrl)
                val repository = github.getRepository("$username/$reponame")
                val key = repository.addDeployKey("Drop Project (import)", newAssignment.gitRepositoryPubKey, true)
                LOG.info("Deploy Key Added: ${key.id}")

                // let's try to clone again
                try {
                    cloneAssignment(newAssignment, gitRepository)
                } catch (e: Exception) {
                    LOG.info("Error cloning (after setting key) ${gitRepository} - ${e}")
                    return Pair(newAssignment.id, "Error cloning ${gitRepository} - ${e.message}")
                }

            } else {
                LOG.info("Error cloning ${gitRepository} - ${e}")
                return Pair(newAssignment.id, "Error cloning ${gitRepository} - ${e.message}")
            }
        }

        assignmentRepository.save(newAssignment)

        // revalidate the assignment
        val report = assignmentTeacherFiles.checkAssignmentFiles(newAssignment, principal)

        // store the report in the DB (first, clear the previous report)
        assignmentReportRepository.deleteByAssignmentId(newAssignment.id)
        report.forEach {
            assignmentReportRepository.save(
                AssignmentReport(
                    assignmentId = newAssignment.id, type = it.type,
                    message = it.message, description = it.description
                )
            )
        }

        return Pair(newAssignment.id, null)
    }

    private fun cloneAssignment(newAssignment: Assignment, gitRepository: String) {
        val directory = File(assignmentsRootLocation, newAssignment.gitRepositoryFolder)
        gitClient.clone(gitRepository, directory, newAssignment.gitRepositoryPrivKey!!.toByteArray())
        LOG.info("[${newAssignment.id}] Successfuly cloned ${gitRepository} to ${directory}")

        // update hash
        val git = Git.open(File(assignmentsRootLocation, newAssignment.gitRepositoryFolder))
        newAssignment.gitCurrentHash = gitClient.getLastCommitInfo(git)?.sha1
    }

    fun importGitSubmissionsFromImportedFile(mapper: ObjectMapper,
                                             submissionsJSONFile: File): String? {

        val gitSubmissions = mapper.readValue(submissionsJSONFile, object : TypeReference<List<GitSubmissionExport>?>() {})

        if (gitSubmissions.isNullOrEmpty()) {
            return "Error: File doesn't contain git submissions"
        }

        gitSubmissions.forEachIndexed { index, it ->
            val authorDetailsList = it.authors.map { a -> AuthorDetails(a.name, a.userId) }
            val group = projectGroupService.getOrCreateProjectGroup(authorDetailsList)
            val submissions = submissionRepository.findByGroupAndAssignmentIdOrderBySubmissionDateDescStatusDateDesc(group, it.assignmentId)

            val gitSubmission = GitSubmission(
                assignmentId = it.assignmentId, submitterUserId = it.submitterUserId,
                createDate = it.createDate, connected = it.connected, lastCommitDate = it.lastCommitDate,
                gitRepositoryUrl = it.gitRepositoryUrl, gitRepositoryPubKey = it.gitRepositoryPubKey,
                gitRepositoryPrivKey = it.gitRepositoryPrivKey)

            gitSubmission.group = group
            if (!submissions.isEmpty()) {
                gitSubmission.lastSubmissionId = submissions[0].id
            }
            gitSubmissionRepository.save(gitSubmission)

            // update FK on all submissions by this group
            submissions.forEach {
                it.gitSubmissionId = gitSubmission.id
                submissionRepository.save(it)
            }

            LOG.info("Imported git submission $gitSubmission.id ($index/${submissions.size})")
        }

        return null
    }

    /**
     * Associates the given tagName with the given assignment. If the tag is already associated, nothing happens
     * (i.e., there are no duplicate tags)
     */
    fun addTagToAssignment(assignment: Assignment, tagName: String) {
        var assignmentTag = assignmentTagRepository.findByName(tagName.trim().lowercase(Locale.getDefault()))

        if (assignmentTag == null) {
            assignmentTag = AssignmentTag(name = tagName.trim().lowercase(Locale.getDefault()))
            assignmentTagRepository.save(assignmentTag)
        }
        // attach via ManyToMany; Set prevents duplicates
        assignment.tags.add(assignmentTag)
        assignmentRepository.save(assignment)
    }

    /**
     * Clears all the tags of this assignment
     *
     * @param clearOrphans if true, checks if there is no remaining assignments with each cleared tag
     * and removes them from the global tags table
     */
    fun clearAllTags(assignment: Assignment, clearOrphans: Boolean = false) {
        // keep previous tag ids if we might remove orphans later
        val previousTagIds: List<Long> = if (clearOrphans) assignment.tags.map { it.id } else emptyList()

        // Clear via the relationship; Hibernate will delete join rows
        assignment.tags.clear()
        assignmentRepository.save(assignment)

        previousTagIds.forEach { tagId ->
            if (assignmentRepository.countByTags_Id(tagId) == 0L) {
                assignmentTagRepository.deleteById(tagId)
            }
        }

    }

    /**
     * Checks if a certain user can access a certain [Assignment]. Only relevant for Assignments that have access
     * control lists.
     *
     * @param assignmentId is a String identifying the relevant Assignment
     * @param principalName is a String identifyng the user trying to access the Assignment
     * @throws If the user is not allowed to access the Assignment, an [AccessDeniedException] will be thrown.
     */
    fun checkAssignees(assignmentId: String, principalName: String) {
        if (assigneeRepository.existsByAssignmentId(assignmentId)) {
            // if it enters here, it means this assignment has a white list
            // let's check if the current user belongs to the white list
            if (!assigneeRepository.existsByAssignmentIdAndAuthorUserId(assignmentId, principalName)) {
                throw AccessDeniedException("${principalName} is not allowed to view this assignment")
            }
        }
    }
}
