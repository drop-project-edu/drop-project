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
package org.dropproject.services

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import jakarta.persistence.EntityNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.apache.commons.io.FileUtils
import org.dropproject.Constants
import org.dropproject.config.PendingExport
import org.dropproject.config.PendingMultipleExports
import org.dropproject.config.PendingTasks
import org.dropproject.controllers.InvalidProjectGroupException
import org.dropproject.dao.*
import org.dropproject.dao.BuildReport
import org.dropproject.data.*
import org.dropproject.extensions.formatJustDate
import org.dropproject.extensions.realName
import org.dropproject.forms.AssignmentForm
import org.dropproject.forms.SubmissionMethod
import org.dropproject.repository.*
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.RefNotAdvertisedException
import org.kohsuke.github.GitHub
import org.slf4j.LoggerFactory
import org.dropproject.config.DropProjectProperties
import org.dropproject.security.RequiresAssignmentOwnerOrACL
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Async
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionAspectSupport
import org.springframework.ui.ModelMap
import java.io.File
import java.nio.file.Files
import java.security.Principal
import java.util.*

data class AssignmentImportResult(val type: String, val message: String, val redirectUrl: String)

/**
 * A problem that was found while validating an [AssignmentForm]. [field] is the name of the offending form field
 * and [code] the message code, so that the web layer can turn this into a BindingResult rejection, while the
 * callers that have no form to go back to (e.g. the MCP tools) can just report [message].
 */
data class AssignmentFormError(val field: String, val code: String, val message: String)

/**
 * The outcome of connecting an [Assignment] to its git repository, or of refreshing it from there. [error] is null
 * if the repository was successfully cloned or pulled, and [validationFailed] tells whether the assignment files
 * that came with it have problems that prevent the assignment from being used by students.
 */
data class AssignmentGitConnectionResult(val error: String?, val validationFailed: Boolean = false)

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
    val assignmentTeacherFiles: AssignmentTeacherFiles,
    val dropProjectProperties: DropProjectProperties,
    val cooloffOverrideService: CooloffOverrideService,
    val plagiarismService: PlagiarismService
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

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
    @RequiresAssignmentOwnerOrACL
    fun getAllSubmissionsForAssignment(assignmentId: String, principal: Principal, model: ModelMap,
                                       request: HttpServletRequest, includeTestDetails: Boolean = false,
                                       mode: String) {
        val assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow { EntityNotFoundException("Assignment $assignmentId not found") }

        model["assignment"] = assignment

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
     * Validates the [Assignment]'s files, replacing the [AssignmentReport] that was previously stored in the DB
     * with the result of this new validation.
     *
     * @param assignment is the Assignment to validate
     * @param principal is a [Principal] representing the user making the request
     * @return true if the validation found errors (in which case the assignment shouldn't be used by students)
     */
    fun validateAndStoreReport(assignment: Assignment, principal: Principal?): Boolean {
        val report = assignmentTeacherFiles.checkAssignmentFiles(assignment, principal)

        // store the report in the DB (first, clear the previous report)
        assignmentReportRepository.deleteByAssignmentId(assignment.id)
        report.forEach {
            assignmentReportRepository.save(AssignmentReport(assignmentId = assignment.id, type = it.type,
                message = it.message, description = it.description))
        }

        return report.any { it.type == AssignmentValidator.InfoType.ERROR }
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
        existingAssignment.submissionStructure = assignmentForm.submissionStructure
        existingAssignment.dueDate = if (assignmentForm.dueDate != null) java.sql.Timestamp.valueOf(assignmentForm.dueDate) else null
        existingAssignment.submissionMethod = assignmentForm.submissionMethod!!
        existingAssignment.acceptsStudentTests = assignmentForm.acceptsStudentTests
        existingAssignment.minStudentTests = assignmentForm.minStudentTests
        existingAssignment.calculateStudentTestsCoverage = assignmentForm.calculateStudentTestsCoverage
        existingAssignment.coverageVisibleToStudents = assignmentForm.coverageVisibleToStudents
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
     * Validates the rules of an [AssignmentForm] that the bean validation annotations of the form itself can't
     * express, because they involve more than one field or need to look into the database.
     *
     * The cross-field rules are all evaluated together, so that the web form can show every problem at once. The
     * rules that only apply to the creation of a new assignment are only evaluated when none of the previous ones
     * failed, and the first one that fails is the only one reported, since each of them makes the next meaningless.
     *
     * @param assignmentForm is the [AssignmentForm] to validate
     * @param principal is a [Principal] representing the user making the request
     * @return the problems that were found, or an empty list if the form is valid
     */
    fun validateAssignmentForm(assignmentForm: AssignmentForm, principal: Principal): List<AssignmentFormError> {

        val errors = mutableListOf<AssignmentFormError>()

        if (assignmentForm.acceptsStudentTests &&
            (assignmentForm.minStudentTests == null || assignmentForm.minStudentTests!! < 1)) {
            errors.add(AssignmentFormError("acceptsStudentTests", "acceptsStudentTests.atLeastOne",
                "Error: You must require at least one student test"))
        }

        if (!assignmentForm.acceptsStudentTests && assignmentForm.minStudentTests != null) {
            errors.add(AssignmentFormError("acceptsStudentTests", "acceptsStudentTests.mustCheck",
                "Error: If you require ${assignmentForm.minStudentTests} student tests, you must check 'Accepts student tests'"))
        }

        if (!assignmentForm.acceptsStudentTests && assignmentForm.calculateStudentTestsCoverage) {
            errors.add(AssignmentFormError("acceptsStudentTests", "acceptsStudentTests.mustCheck",
                "Error: If you want to calculate coverage of student tests, you must check 'Accepts student tests'"))
        }

        if (assignmentForm.minGroupSize != null && assignmentForm.minGroupSize!! < 1) {
            errors.add(AssignmentFormError("minGroupSize", "minGroupSize.greaterThan1",
                "Error: Min group size must be >= 1"))
        }

        if (assignmentForm.maxGroupSize != null && assignmentForm.minGroupSize == null) {
            errors.add(AssignmentFormError("minGroupSize", "minGroupSize.mustExist",
                "Error: If you fill in the max group size, you must also fill in the min group size"))
        }

        if (assignmentForm.minGroupSize != null && assignmentForm.maxGroupSize != null &&
            assignmentForm.minGroupSize!! > assignmentForm.maxGroupSize!!) {
            errors.add(AssignmentFormError("minGroupSize", "minGroupSize.maxGreaterThanMin",
                "Error: Max must be greater or equal to min"))
        }

        if (!assignmentForm.exceptions.isNullOrBlank() && assignmentForm.minGroupSize == null) {
            errors.add(AssignmentFormError("exceptions", "exceptions.minSizeNotSet",
                "Error: Exceptions to group size should only be filled in when you set the min group size"))
        }

        if (assignmentForm.visibility == AssignmentVisibility.PRIVATE && assignmentForm.assignees.isNullOrEmpty()) {
            errors.add(AssignmentFormError("assignees", "assignees.mustBeFilled",
                "Error: For PRIVATE assignments, you have to fill in the authorized submitters"))
        }

        if (errors.isEmpty() && !assignmentForm.editMode) {
            validateNewAssignmentForm(assignmentForm, principal)?.let { errors.add(it) }
        }

        errors.forEach { LOG.warn(it.message) }

        return errors
    }

    /**
     * Validates the rules that only apply to the creation of a new [Assignment], returning the first problem that
     * was found. The fields that the bean validation of the form is responsible for are skipped when they are
     * missing, since this may be called before those errors were reported to the user.
     */
    private fun validateNewAssignmentForm(assignmentForm: AssignmentForm, principal: Principal): AssignmentFormError? {

        if (assignmentForm.acl?.split(",")?.contains(principal.realName()) == true) {
            return AssignmentFormError("acl", "acl.includeOwner",
                "Error: You don't need to give autorization to yourself, only other teachers")
        }

        val assignmentId = assignmentForm.assignmentId ?: return null

        // check if it already exists an assignment with this id
        if (assignmentRepository.existsById(assignmentId)) {
            return AssignmentFormError("assignmentId", "assignment.duplicate",
                "Error: An assignment already exists with this ID")
        }

        // verify if there is another (still existing) assignment connected to this git repository folder.
        // Normally impossible for a brand new assignment (gitRepositoryFolder is always == assignmentId, and
        // we already checked above that no assignment exists with this id), but an imported assignment can
        // have a gitRepositoryFolder that doesn't match its own id, so this guards against that collision
        if (assignmentRepository.findByGitRepositoryFolder(assignmentId) != null) {
            return AssignmentFormError("assignmentId", "assignment.duplicateFolder",
                "Error: There is already an assignment using this git repository folder")
        }

        val gitRepositoryUrl = assignmentForm.gitRepositoryUrl ?: return null

        if (!gitRepositoryUrl.startsWith("git@")) {
            return AssignmentFormError("gitRepositoryUrl", "repository.notSSh",
                "Error: Only SSH style urls are accepted (must start with 'git@')")
        }

        return null
    }

    /**
     * Creates a new [Assignment] based on the contents of an [AssignmentForm], together with its group
     * restrictions and its tags.
     *
     * The returned assignment is not saved and is not yet connected to its git repository, which is the
     * responsibility of the caller (see [connectAssignmentToGitRepository]).
     *
     * @param assignmentForm, the AssignmentForm from which the Assignment contents will be copied
     * @param principal is a [Principal] representing the user making the request
     * @return the created Assignment
     */
    fun buildAssignmentFromForm(assignmentForm: AssignmentForm, principal: Principal): Assignment {
        val newAssignment = Assignment(id = assignmentForm.assignmentId!!, name = assignmentForm.assignmentName!!,
            packageName = assignmentForm.assignmentPackage, language = assignmentForm.language!!,
            submissionStructure = assignmentForm.submissionStructure,
            dueDate = if (assignmentForm.dueDate != null) java.sql.Timestamp.valueOf(assignmentForm.dueDate) else null,
            acceptsStudentTests = assignmentForm.acceptsStudentTests,
            minStudentTests = assignmentForm.minStudentTests,
            calculateStudentTestsCoverage = assignmentForm.calculateStudentTestsCoverage,
            coverageVisibleToStudents = assignmentForm.coverageVisibleToStudents,
            mandatoryTestsSuffix = assignmentForm.mandatoryTestsSuffix,
            cooloffPeriod = assignmentForm.cooloffPeriod,
            maxMemoryMb = assignmentForm.maxMemoryMb, submissionMethod = assignmentForm.submissionMethod!!,
            gitRepositoryUrl = assignmentForm.gitRepositoryUrl!!, ownerUserId = principal.realName(),
            gitRepositoryFolder = assignmentForm.assignmentId!!, showLeaderBoard = assignmentForm.leaderboardType != null,
            hiddenTestsVisibility = assignmentForm.hiddenTestsVisibility,
            leaderboardType = assignmentForm.leaderboardType,
            visibility = assignmentForm.visibility)

        // we only need to check minGroupSize since maxGroupSize and exceptions depend on this field
        if (assignmentForm.minGroupSize != null) {
            val projectGroupRestrictions = ProjectGroupRestrictions(minGroupSize = assignmentForm.minGroupSize!!,
                maxGroupSize = assignmentForm.maxGroupSize,
                exceptions = assignmentForm.exceptions)
            projectGroupRestrictionsRepository.save(projectGroupRestrictions)
            newAssignment.projectGroupRestrictions = projectGroupRestrictions
        }

        // associate tags
        val tagNames = assignmentForm.assignmentTags?.lowercase(Locale.getDefault())?.split(",")
        tagNames?.forEach {
            addTagToAssignment(newAssignment, it)
        }
        return newAssignment
    }

    /**
     * Replaces the ACL and the assignees of [assignment] with the ones described in [assignmentForm].
     *
     * @return the problem that was found with the ACL, or null if it was applied successfully. Note that the
     * assignees are only applied when the ACL is valid.
     */
    fun updateAssignmentACLAndAssignees(assignment: Assignment, assignmentForm: AssignmentForm): AssignmentFormError? {

        if (!(assignmentForm.acl.isNullOrBlank())) {
            val userIds = assignmentForm.acl!!.split(",")

            // Validate each userId
            for (userId in userIds) {
                val trimmedUserId = userId.trim()
                if (trimmedUserId.contains(" ") || trimmedUserId.contains(";")) {
                    return AssignmentFormError("acl", "acl.invalidFormat",
                        "Error: User IDs must be comma-separated. '$trimmedUserId' contains invalid characters (spaces or semicolons).")
                }
            }

            // first delete existing to prevent duplicates
            assignmentACLRepository.deleteByAssignmentId(assignment.id)

            for (userId in userIds) {
                assignmentACLRepository.save(AssignmentACL(assignmentId = assignment.id, userId = userId.trim()))
            }
        }

        // first delete all assignees to prevent duplicates
        assigneeRepository.deleteByAssignmentId(assignment.id)
        assigneeRepository.flush()  // due to some weird issue, I have to flush (see: https://github.com/spring-projects/spring-data-jpa/issues/1100)

        val assigneesStr = assignmentForm.assignees?.split(",").orEmpty().map { it -> it.trim() }
        for (assigneeStr in assigneesStr) {
            if (!assigneeStr.isBlank()) {
                assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = assigneeStr))
            }
        }

        return null
    }

    /**
     * Generates a new ssh key pair for [assignment] and stores it, replacing any key pair that it already had.
     *
     * Drop Project only ever reads the assignment's repository, so the public key is meant to be installed on it
     * as a read-only deploy key. The private key never leaves the server.
     *
     * @param assignment is the [Assignment] that will be connected to a git repository
     * @return the public key that must be installed on the repository
     */
    fun generateGitConnectionKeyPair(assignment: Assignment): String {
        val (privKey, pubKey) = gitClient.generateKeyPair()

        assignment.gitRepositoryPrivKey = String(privKey)
        assignment.gitRepositoryPubKey = String(pubKey)
        assignmentRepository.save(assignment)

        return assignment.gitRepositoryPubKey!!
    }

    /**
     * Clones the git repository of [assignment] using the private key that was generated for it, and validates the
     * assignment files that were just cloned.
     *
     * This is only expected to succeed after the matching public key was installed as a deploy key on the
     * repository, so it is safe to call again after that was done.
     *
     * @param assignment is the [Assignment] to connect
     * @param principal is a [Principal] representing the user making the request
     * @return the outcome of the connection
     */
    fun connectAssignmentToGitRepository(assignment: Assignment, principal: Principal): AssignmentGitConnectionResult {

        if (assignment.gitRepositoryPrivKey == null) {
            LOG.warn("[${assignment.id}] Trying to connect to git without a private key")
            return AssignmentGitConnectionResult(
                error = "Something went wrong with the credentials generation. Please try again")
        }

        val directory = File(dropProjectProperties.assignments.rootLocation, assignment.gitRepositoryFolder)
        if (directory.exists()) {
            directory.deleteRecursively()
        }

        val gitRepository = assignment.gitRepositoryUrl
        try {
            gitClient.clone(gitRepository, directory, assignment.gitRepositoryPrivKey!!.toByteArray()).use { }
            LOG.info("[${assignment.id}] Successfuly cloned ${gitRepository} to ${directory}")

            // update hash
            Git.open(directory).use { git ->
                assignment.gitCurrentHash = gitClient.getLastCommitInfo(git)?.sha1
            }
            assignmentRepository.save(assignment)
        } catch (e: Exception) {
            LOG.info("Error cloning ${gitRepository} - ${e}")
            return AssignmentGitConnectionResult(
                error = "Error cloning ${gitRepository} - ${e.message}. Are you sure you added the public key to the repository?")
        }

        // check that the assignment repository is a valid assignment structure
        if (validateAndStoreReport(assignment, principal)) {
            assignmentRepository.save(assignment)  // assignment.buildResult was updated
            LOG.info("[${assignment.id}] Assignment has problems. Please check the 'Validation Report'")
            return AssignmentGitConnectionResult(error = null, validationFailed = true)
        }

        return AssignmentGitConnectionResult(error = null)
    }

    /**
     * Pulls the git repository of [assignment], so that Drop Project picks up the changes that the teacher pushed
     * to it, and validates the assignment files again.
     *
     * @param assignment is the [Assignment] to refresh
     * @param principal is a [Principal] representing the user making the request
     * @return the outcome of the refresh
     */
    fun refreshAssignmentFromGitRepository(assignment: Assignment, principal: Principal): AssignmentGitConnectionResult {

        if (assignment.gitRepositoryPrivKey == null) {
            LOG.warn("Unable to pull git repository for ${assignment.id} because private key is null")
            return AssignmentGitConnectionResult(error = "Error pulling from ${assignment.gitRepositoryUrl}")
        }

        val directory = File(dropProjectProperties.assignments.rootLocation, assignment.gitRepositoryFolder)
        try {
            LOG.info("Pulling git repository for ${assignment.id}")
            gitClient.pull(directory, assignment.gitRepositoryPrivKey!!.toByteArray())

            // update hash
            val git = Git.open(directory)
            assignment.gitCurrentHash = gitClient.getLastCommitInfo(git)?.sha1

            // remove the reportId from all git submissions (if there are any) to signal the student that he should
            // generate a report again
            val gitSubmissionsForThisAssignment = gitSubmissionRepository.findByAssignmentId(assignment.id)
            for (gitSubmission in gitSubmissionsForThisAssignment) {
                gitSubmission.lastSubmissionId = null
                gitSubmissionRepository.save(gitSubmission)
            }

            if (!gitSubmissionsForThisAssignment.isEmpty()) {
                LOG.info("Reset reportId for ${gitSubmissionsForThisAssignment.size} git submissions")
            }

            // revalidate the assignment
            val validationFailed = validateAndStoreReport(assignment, principal)

            return AssignmentGitConnectionResult(error = null, validationFailed = validationFailed)

        } catch (re: RefNotAdvertisedException) {
            LOG.warn("Couldn't pull git repository for ${assignment.id}: head is invalid")
            return AssignmentGitConnectionResult(
                error = "Error pulling from ${assignment.gitRepositoryUrl}. Probably you don't have any commits yet.")
        } catch (e: Exception) {
            LOG.warn("Couldn't pull git repository for ${assignment.id}", e)
            return AssignmentGitConnectionResult(error = "Error pulling from ${assignment.gitRepositoryUrl}")
        }
    }

    /**
     * Removes everything related to an [Assignment] from the database. It doesn't remove any files - that's the
     * responsibility of the caller, which should only do it after this function returns, since it may rollback.
     *
     * Everything is done in a single transaction, so that the assignment is either completely removed or not
     * removed at all. This also keeps the assignment attached to the same persistence context throughout, which
     * matters because [clearAllTags] deletes the tags that are left orphan: on a detached assignment, the next
     * save() would try to reconcile the stale collection against tags that no longer exist.
     *
     * @param assignment is the [Assignment] to remove
     * @param forceDelete if true, also removes all the assignment's submissions
     */
    @Transactional
    fun deleteAssignment(assignment: Assignment, forceDelete: Boolean) {

        val assignmentId = assignment.id

        if (forceDelete) {
            LOG.info("Removing all submissions (db) related to ${assignmentId}")

            val submissions = submissionRepository.findByAssignmentId(assignmentId)
            val count = submissions.size
            for ((idx, submission) in submissions.withIndex()) {
                LOG.info("Removing everything related to submission ${submission.id} from DB ($idx/$count)")

                // these tolerate rows that are not there (e.g. orphaned reports), deleting nothing
                submissionReportRepository.deleteBySubmissionId(submission.id)
                jUnitReportRepository.deleteBySubmissionId(submission.id)
                jacocoReportRepository.deleteBySubmissionId(submission.id)
                // the build report is not deleted here since build_report_id is a foreign key with cascade delete

                if (submission.submissionId == null) {  // submission by git
                    val gitSubmissionId = submission.gitSubmissionId
                    if (gitSubmissionId != null) {
                        gitSubmissionRepository.deleteById(gitSubmissionId)
                    } else {
                        // inconsistent data - don't let it stop the removal of the remaining submissions
                        LOG.warn("Submission ${submission.id} is a git submission but has no gitSubmissionId. Moving on...")
                    }
                }
            }

            LOG.info("Removing all the submissions from DB")
            submissionRepository.deleteAllByAssignmentId(assignmentId)
        }

        clearAllTags(assignment, clearOrphans = true)

        assignmentACLRepository.deleteByAssignmentId(assignmentId)
        assignmentReportRepository.deleteByAssignmentId(assignmentId)
        plagiarismService.deleteChecks(assignmentId)
        assignmentRepository.deleteById(assignmentId)
        assigneeRepository.deleteByAssignmentId(assignmentId)
    }

    /**
     * Handles the exportation of an assignment and (optionally) its submissions.
     *
     * NOTE: If you change the name of this method, update MyAsyncUncaughtExceptionHandler
     */
    @Async
    @Transactional
    fun exportAssignment(assignmentId: String, includeSubmissions: Boolean, taskId: String) {
        pendingTasks.put(taskId, createExport(assignmentId, includeSubmissions))
    }

    /**
     * Handles the exportation of several assignments at once, producing one .dp file per assignment.
     *
     * NOTE: If you change the name of this method, update MyAsyncUncaughtExceptionHandler
     */
    @Async
    @Transactional
    fun exportAssignments(assignmentIds: List<String>, includeSubmissions: Boolean, taskId: String) {
        val exports = assignmentIds.map { createExport(it, includeSubmissions) }
        pendingTasks.put(taskId, PendingMultipleExports(exports))
    }

    /**
     * Creates the .dp file of a single assignment and (optionally) of its submissions.
     *
     * @return the name to give to the downloaded file and the file itself
     */
    private fun createExport(assignmentId: String, includeSubmissions: Boolean): PendingExport {

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

            return PendingExport(fileName, zipFile)
        } finally {
            tempFolder.deleteRecursively()
        }
    }

    fun exportOriginalSubmissionFilesTo(assignment: Assignment, destinationFolder: File) {

        if (assignment.submissionMethod == SubmissionMethod.UPLOAD) {

            val submissions = submissionRepository.findByAssignmentId(assignment.id)
            submissions.forEachIndexed { index, it ->
                with(it) {
                    if (submissionId != null && submissionFolder != null) {
                        val projectFolderFrom = File(dropProjectProperties.storage.uploadLocation, submissionFolder)
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
                val repositoryFolderFrom = File(dropProjectProperties.storage.gitLocation, it.getFolderRelativeToStorageRoot())
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

    /**
     * Imports an assignment and, if the file contains them, its submissions.
     *
     * The import is atomic: if any of the steps fails, the assignment and the submissions that were already imported
     * are undone (the database changes are rolled back and the cloned repository is deleted), so that the same file
     * can be imported again after the cause of the failure is solved.
     */
    @Transactional
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

        val assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow { EntityNotFoundException("Assignment ${assignmentId} not found") }

        val result = try {
            importSubmissionsOfImportedAssignment(mapper, submissionsJSONFile, gitSubmissionsJSONFile,
                originalSubmissionsFolder, assignment)
        } catch (e: Exception) {
            // the rollback of the transaction doesn't delete the folder where the repository was cloned
            deleteClonedRepository(assignment)
            throw e
        }

        if (result.type == "error") {
            undoImport(assignment)
        }

        return result
    }

    private fun importSubmissionsOfImportedAssignment(mapper: ObjectMapper, submissionsJSONFile: File,
                                                      gitSubmissionsJSONFile: File,
                                                      originalSubmissionsFolder: File,
                                                      assignment: Assignment): AssignmentImportResult {

        if (!submissionsJSONFile.exists()) {
            return AssignmentImportResult("message", "Imported successfully ${assignment.id}. Submissions were not imported",
                "redirect:/assignment/info/${assignment.id}")
        }

        val errorMessage = importSubmissionsFromImportedFile(mapper, submissionsJSONFile, assignment.id)
        if (errorMessage != null) {
            return AssignmentImportResult("error", errorMessage, "redirect:/assignment/import")
        }

        if (gitSubmissionsJSONFile.exists()) {
            val gitErrorMessage = importGitSubmissionsFromImportedFile(mapper, gitSubmissionsJSONFile, assignment.id)
            if (gitErrorMessage != null) {
                return AssignmentImportResult("error", gitErrorMessage, "redirect:/assignment/import")
            }
        }

        // import all the original submission files
        if (originalSubmissionsFolder.exists()) {
            when (assignment.submissionMethod) {
                SubmissionMethod.UPLOAD -> FileUtils.copyDirectory(originalSubmissionsFolder, File(dropProjectProperties.storage.uploadLocation))
                SubmissionMethod.GIT -> FileUtils.copyDirectory(originalSubmissionsFolder, File(dropProjectProperties.storage.gitLocation))
            }
        }

        return AssignmentImportResult("message", "Imported successfully ${assignment.id} and all its submissions",
            "redirect:/report/${assignment.id}")
    }

    /**
     * Undoes an import that failed after the assignment was already created.
     */
    private fun undoImport(assignment: Assignment) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
        deleteClonedRepository(assignment)
        LOG.info("Undone the import of ${assignment.id}")
    }

    /**
     * Deletes the folder into which the assignment's git repository was cloned.
     */
    private fun deleteClonedRepository(assignment: Assignment) {
        val directory = File(dropProjectProperties.assignments.rootLocation, assignment.gitRepositoryFolder)
        if (directory.exists()) {
            FileUtils.deleteDirectory(directory)
        }
    }

    /**
     * Imports the submissions contained in [submissionsJSONFile] into the assignment identified by [assignmentId],
     * which is the assignment that was just imported.
     */
    fun importSubmissionsFromImportedFile(mapper: ObjectMapper,
                                          submissionsJSONFile: File,
                                          assignmentId: String): String? {

        val submissions = mapper.readValue(submissionsJSONFile, object : TypeReference<List<SubmissionExport>?>() {})

        if (submissions.isNullOrEmpty()) {
            return "Error: File doesn't contain submissions"
        }

        // the submissions must belong to the assignment that was imported from this same file. Otherwise, this
        // would be a way of injecting submissions into any other empty assignment of this server, even if it
        // belongs to another teacher
        val otherAssignmentId = submissions.find { it.assignmentId != assignmentId }?.assignmentId
        if (otherAssignmentId != null) {
            return "Error: This file contains submissions of another assignment ($otherAssignmentId). " +
                    "Please import a .dp file that was exported by Drop Project."
        }

        // make sure the assignment exists
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

        // the ids of the tags belong to the exporting server, where they may identify other tags (or none at all),
        // so only their names are kept, to reattach them, further down, to the tags of this server
        val importedTagNames = newAssignment.tags.map { it.name }
        newAssignment.tags = mutableSetOf()

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

            if (dropProjectProperties.github.token != "no-token") {  // "no-token" is the default value
                LOG.info(
                    "Error cloning ${gitRepository} - ${e}. Maybe the SSH key was removed. Let's try setting the key" +
                            "using github API"
                )

                val github = GitHub.connectUsingOAuth(dropProjectProperties.github.token)
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

        try {
            // the id of the group restrictions belongs to the exporting server, where it may identify the
            // restrictions of another assignment (or none at all), so they are recreated here as a new row
            newAssignment.projectGroupRestrictions = newAssignment.projectGroupRestrictions?.let {
                projectGroupRestrictionsRepository.save(it.copy(id = 0))
            }

            assignmentRepository.save(newAssignment)

            // creates the tags that don't exist yet in this server and reuses the ones that do
            importedTagNames.forEach { addTagToAssignment(newAssignment, it) }

            // revalidate the assignment
            validateAndStoreReport(newAssignment, principal)
        } catch (e: Exception) {
            // the rollback of the transaction doesn't delete the folder where the repository was just cloned
            deleteClonedRepository(newAssignment)
            throw e
        }

        return Pair(newAssignment.id, null)
    }

    private fun cloneAssignment(newAssignment: Assignment, gitRepository: String) {
        val directory = File(dropProjectProperties.assignments.rootLocation, newAssignment.gitRepositoryFolder)
        gitClient.clone(gitRepository, directory, newAssignment.gitRepositoryPrivKey!!.toByteArray())
        LOG.info("[${newAssignment.id}] Successfuly cloned ${gitRepository} to ${directory}")

        // update hash
        val git = Git.open(File(dropProjectProperties.assignments.rootLocation, newAssignment.gitRepositoryFolder))
        newAssignment.gitCurrentHash = gitClient.getLastCommitInfo(git)?.sha1
    }

    /**
     * Imports the git submissions contained in [submissionsJSONFile] into the assignment identified by
     * [assignmentId], which is the assignment that was just imported.
     */
    fun importGitSubmissionsFromImportedFile(mapper: ObjectMapper,
                                             submissionsJSONFile: File,
                                             assignmentId: String): String? {

        val gitSubmissions = mapper.readValue(submissionsJSONFile, object : TypeReference<List<GitSubmissionExport>?>() {})

        if (gitSubmissions.isNullOrEmpty()) {
            return "Error: File doesn't contain git submissions"
        }

        // just like the submissions, the git submissions must belong to the assignment that was imported
        val otherAssignmentId = gitSubmissions.find { it.assignmentId != assignmentId }?.assignmentId
        if (otherAssignmentId != null) {
            return "Error: This file contains git submissions of another assignment ($otherAssignmentId). " +
                    "Please import a .dp file that was exported by Drop Project."
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

    fun isAuthorizedTeacher(assignment: Assignment, principalName: String, request: HttpServletRequest): Boolean {
        return request.isUserInRole("TEACHER") &&
                (assignment.ownerUserId == principalName ||
                        assignmentACLRepository.existsByAssignmentIdAndUserId(assignment.id, principalName))
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
            // let's check if the current user belongs to the white list or is exempt from group size
            // restriction, in which is case is also automatically allowed
            if (!isAssigneeOrException(assignmentId, principalName)) {
                throw AccessDeniedException("${principalName} is not allowed to view this assignment")
            }
        }
    }

    /**
     * Checks if all members of a group are in the assignment's whitelist.
     * @param assignmentId is a String identifying the assignment
     * @param groupMembers is a List of author IDs (student numbers) representing the group members
     * @param i18n is the MessageSource for internationalization
     * @param currentLocale is the current Locale for message formatting
     * @param isTeacher is a Boolean indicating if the submitter is a teacher
     * @throws InvalidProjectGroupException if any group member is not in the whitelist
     */
    fun checkGroupMembersInWhitelist(assignmentId: String, groupMembers: List<String>,
                                     i18n: org.springframework.context.MessageSource,
                                     currentLocale: java.util.Locale,
                                     isTeacher: Boolean = false) {
        if (isTeacher) {
            return
        }

        if (assigneeRepository.existsByAssignmentId(assignmentId)) {
            // if it enters here, it means this assignment has a white list
            // let's check if all group members belong to the white list or are exempt from group size
            // restriction, in which is case is also automatically allowed
            for (memberId in groupMembers) {
                if (!isAssigneeOrException(assignmentId, memberId)) {
                    throw InvalidProjectGroupException(i18n.getMessage("student.submit.groupMemberNotInWhitelist",
                        arrayOf(memberId), currentLocale))
                }
            }
        }
    }

    private fun isAssigneeOrException(assignmentId: String, userId: String): Boolean {
        if (assigneeRepository.existsByAssignmentIdAndAuthorUserId(assignmentId, userId)) {
            return true
        }

        val exceptions = assignmentRepository.findById(assignmentId).orElse(null)
            ?.projectGroupRestrictions?.exceptionsAsList() ?: emptyList()
        return exceptions.contains(userId)
    }

    /**
     * Gets comprehensive assignment detail information including assignees, ACL, tests, reports, and git info.
     * This method extracts the business logic from AssignmentController.getAssignmentDetail() for reuse.
     * 
     * @param assignmentId the ID of the assignment to retrieve
     * @param principal the user making the request
     * @param isAdmin whether the user has admin privileges
     * @return AssignmentDetailResponse containing all assignment detail data
     * @throws EntityNotFoundException if the assignment is not found
     * @throws org.springframework.security.access.AccessDeniedException if the user is not authorized to access the assignment
     */
    @RequiresAssignmentOwnerOrACL
    @Transactional(readOnly = true)  // because of assignment.tags forced loading
    fun getAssignmentDetailData(assignmentId: String, principal: Principal, isAdmin: Boolean): AssignmentDetailResponse {
        val assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow { EntityNotFoundException("Assignment $assignmentId not found") }

        val assignees = assigneeRepository.findByAssignmentIdOrderByAuthorUserId(assignmentId)
        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)
        val assignmentReports = assignmentReportRepository.findByAssignmentId(assignmentId)

        val tests = assignmentTestMethodRepository.findByAssignmentId(assignmentId)
        
        val reportMessage = if (assignmentReports.any { it.type != AssignmentValidator.InfoType.INFO }) {
            "Assignment has errors! You have to fix them before activating it."
        } else {
            "Good job! Assignment has no errors and is ready to be activated."
        }

        // Git information (if available)
        var lastCommitInfo: String? = null
        var sshKeyFingerprint: String? = null
        
        if (assignment.gitRepositoryPrivKey != null && File(dropProjectProperties.assignments.rootLocation, assignment.gitRepositoryFolder).exists()) {
            val git = Git.open(File(dropProjectProperties.assignments.rootLocation, assignment.gitRepositoryFolder))
            val lastCommitInfoObj = gitClient.getLastCommitInfo(git)
            lastCommitInfo = lastCommitInfoObj?.toString() ?: "No commits"
            sshKeyFingerprint = assignment.gitRepositoryPubKey?.let { gitClient.computeSshFingerprint(it) }
        }

        // fetch instructions
        assignment.instructions = assignmentTeacherFiles.getInstructions(assignment)

        // Get cooloff override information
        val cooloffOverride = if (assignment.cooloffPeriod != null) {
            val overrideInfo = cooloffOverrideService.getOverrideInfo(assignmentId)
            if (overrideInfo != null) {
                CooloffOverrideDisplay(
                    isDisabled = true,
                    disabledBy = overrideInfo.teacherId,
                    remainingMinutes = cooloffOverrideService.getRemainingMinutes(assignmentId),
                    expiryTime = overrideInfo.expiryTime
                )
            } else {
                CooloffOverrideDisplay(false, null, null, null)
            }
        } else null

        return AssignmentDetailResponse(
            assignment = assignment,
            assignees = assignees,
            acl = acl,
            tags = assignment.tagsStr,
            tests = tests,
            reports = assignmentReports,
            reportMessage = reportMessage,
            lastCommitInfo = lastCommitInfo,
            sshKeyFingerprint = sshKeyFingerprint,
            isAdmin = isAdmin,
            cooloffOverride = cooloffOverride
        )
    }
}
