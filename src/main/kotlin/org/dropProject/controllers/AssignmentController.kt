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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.apache.commons.io.FileUtils
import org.dropProject.PendingTasks
import org.dropProject.dao.*
import org.dropProject.data.*
import org.dropProject.extensions.realName
import org.dropProject.forms.AssignmentForm
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.*
import org.dropProject.services.*
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.RefNotAdvertisedException
import org.hibernate.Hibernate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.ui.ModelMap
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.Principal
import java.time.ZoneId
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.validation.Valid

/**
 * AssignmentController contains MVC controller functions that handle requests related with [Assignment]s
 * (for example, assignment creation, edition, etc.)
 */
@Controller
@RequestMapping("/assignment")
class AssignmentController(
        val authorRepository: AuthorRepository,
        val assignmentRepository: AssignmentRepository,
        val assignmentReportRepository: AssignmentReportRepository,
        val assignmentTagRepository: AssignmentTagRepository,
        val assignmentTestMethodRepository: AssignmentTestMethodRepository,
        val assigneeRepository: AssigneeRepository,
        val assignmentACLRepository: AssignmentACLRepository,
        val submissionRepository: SubmissionRepository,
        val submissionReportRepository: SubmissionReportRepository,
        val gitSubmissionRepository: GitSubmissionRepository,
        val buildReportRepository: BuildReportRepository,
        val jUnitReportRepository: JUnitReportRepository,
        val gitClient: GitClient,
        val assignmentTeacherFiles: AssignmentTeacherFiles,
        val submissionService: SubmissionService,
        val assignmentService: AssignmentService,
        val zipService: ZipService,
        val cacheManager: CacheManager,
        val projectGroupService: ProjectGroupService,
        val pendingTasks: PendingTasks) {

    @Value("\${assignments.rootLocation}")
    val assignmentsRootLocation: String = ""

    @Value("\${mavenizedProjects.rootLocation}")
    val mavenizedProjectsRootLocation: String = ""

    @Value("\${storage.rootLocation}/upload")
    val uploadSubmissionsRootLocation: String = "submissions/upload"

    @Value("\${storage.rootLocation}/git")
    val gitSubmissionsRootLocation: String = "submissions/git"

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    /**
     * Controller that handles HTTP GET requests for the [Assignment] creation form.
     * @param model is a [ModelMap] that will be populated with information to use in a View
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/new"], method = [(RequestMethod.GET)])
    fun getNewAssignmentForm(model: ModelMap): String {
        model["assignmentForm"] = AssignmentForm()
        model["allTags"] = assignmentTagRepository.findAll()
                .map { "'" + it.name + "'" }
                .joinToString(separator = ",", prefix = "[", postfix = "]")
        return "assignment-form"
    }

    /**
     * Controller that handles HTTP POST requests for the [Assignment] creation form.
     *
     * @param assignmentForm is an [AssignmentForm]
     * @param bindingResult is a [BindingResult]
     * @param redirectAttributes is a [RedirectAttributes]
     * @param principal is a [Principal] representing the user making the request
     *
     * @return is a String with the name of the relevant View
     */
    @RequestMapping(value = ["/new"], method = [(RequestMethod.POST)])
    @Transactional  // because of assignment.tags
    fun createOrEditAssignment(@Valid @ModelAttribute("assignmentForm") assignmentForm: AssignmentForm,
                               bindingResult: BindingResult,
                               redirectAttributes: RedirectAttributes,
                               principal: Principal): String {

        if (bindingResult.hasErrors()) {
            return "assignment-form"
        }

        var mustSetupGitConnection = false

        if (assignmentForm.acceptsStudentTests &&
                (assignmentForm.minStudentTests == null || assignmentForm.minStudentTests!! < 1)) {
            LOG.warn("Error: You must require at least one student test")
            bindingResult.rejectValue("acceptsStudentTests", "acceptsStudentTests.atLeastOne", "Error: You must require at least one student test")
            return "assignment-form"
        }

        if (!assignmentForm.acceptsStudentTests && assignmentForm.minStudentTests != null) {
            LOG.warn("If you require ${assignmentForm.minStudentTests} student tests, you must check 'Accepts student tests'")
            bindingResult.rejectValue("acceptsStudentTests", "acceptsStudentTests.mustCheck",
                    "Error: If you require ${assignmentForm.minStudentTests} student tests, you must check 'Accepts student tests'")
            return "assignment-form"
        }

        if (!assignmentForm.acceptsStudentTests && assignmentForm.calculateStudentTestsCoverage) {
            LOG.warn("If you want to calculate coverage of student tests, you must check 'Accepts student tests'")
            bindingResult.rejectValue("acceptsStudentTests", "acceptsStudentTests.mustCheck",
                    "Error: If you want to calculate coverage of student tests, you must check 'Accepts student tests'")
            return "assignment-form"
        }

        var assignment: Assignment
        if (!assignmentForm.editMode) {   // create

            if (assignmentForm.acl?.split(",")?.contains(principal.realName()) == true) {
                LOG.warn("Assignment ACL should not include the owner")
                bindingResult.rejectValue("acl", "acl.includeOwner",
                        "Error: You don't need to give autorization to yourself, only other teachers")
                return "assignment-form"
            }

            // check if it already exists an assignment with this id
            if (assignmentRepository.existsById(assignmentForm.assignmentId!!)) {
                LOG.warn("An assignment already exists with this ID: ${assignmentForm.assignmentId}")
                bindingResult.rejectValue("assignmentId", "assignment.duplicate", "Error: An assignment already exists with this ID")
                return "assignment-form"
            }

            // TODO: verify if there is another assignment connected to this git repository

            val gitRepository = assignmentForm.gitRepositoryUrl!!
            if (!gitRepository.startsWith("git@")) {
                LOG.warn("Invalid git repository url: ${assignmentForm.gitRepositoryUrl}")
                bindingResult.rejectValue("gitRepositoryUrl", "repository.notSSh", "Error: Only SSH style urls are accepted (must start with 'git@')")
                return "assignment-form"
            }

            // check if we can connect to given git repository
            try {
                val directory = File(assignmentsRootLocation, assignmentForm.assignmentId)
                gitClient.clone(gitRepository, directory)
                LOG.info("[${assignmentForm.assignmentId}] Successfuly cloned ${gitRepository} to ${directory}")
            } catch (e: Exception) {
                LOG.error("[${assignmentForm.assignmentId}] Error cloning ${gitRepository} - ${e}")
                if (e.message.orEmpty().contains("Invalid remote: origin") || e.message.orEmpty().contains("Auth fail")) {
                    // probably will need authentication
                    mustSetupGitConnection = true
                    LOG.info("[${assignmentForm.assignmentId}] will redirect to setup-git")
//                    bindingResult.rejectValue("gitRepositoryUrl", "repository.invalid", "Error: Git repository is invalid or inexistent")
                } else {
                    LOG.warn("[${assignmentForm.assignmentId}] Cloning error is neither 'Invalid remote: origin' " +
                            "or 'Auth fail' : [${e.message.orEmpty()}]")
                    bindingResult.rejectValue("gitRepositoryUrl", "repository.genericError", "Error cloning git repository. " +
                            "Are you sure the url is right?")
                    return "assignment-form"
                }
            }

            val newAssignment = createAssignmentBasedOnForm(assignmentForm, principal)

            assignmentRepository.save(newAssignment)

            assignment = newAssignment

        } else {   // update

            val existingAssignment = assignmentRepository.getOne(assignmentForm.assignmentId)
                    ?: throw IllegalArgumentException("Trying to update an inexistent assignment")

            if (existingAssignment.gitRepositoryUrl != assignmentForm.gitRepositoryUrl) {
                LOG.warn("[${assignmentForm.assignmentId}] Git repository cannot be changed")
                bindingResult.rejectValue("gitRepositoryUrl", "repository.not-updateable", "Error: Git repository cannot be changed.")
                return "assignment-form"
            }

            val acl = assignmentACLRepository.findByAssignmentId(existingAssignment.id)
            if (principal.realName() != existingAssignment.ownerUserId && acl.find { it -> it.userId == principal.realName() } == null) {
                LOG.warn("[${assignmentForm.assignmentId}][${principal.realName()}] Assignments can only be changed " +
                        "by their ownerUserId (${existingAssignment.ownerUserId}) or authorized teachers")
                throw IllegalAccessError("Assignments can only be changed by their owner or authorized teachers")
            }

            if (assignmentForm.acl?.split(",")?.contains(existingAssignment.ownerUserId) == true) {
                LOG.warn("Assignment ACL should not include the owner")
                bindingResult.rejectValue("acl", "acl.includeOwner",
                        "Error: You don't need to include the assignment owner, only other teachers")
                return "assignment-form"
            }

            // TODO: check again for assignment integrity

            assignmentService.updateAssignment(existingAssignment, assignmentForm)

            assignmentRepository.save(existingAssignment)

            assignment = existingAssignment

            // TODO: Need to rebuild?
        }

        if (!(assignmentForm.acl.isNullOrBlank())) {
            val userIds = assignmentForm.acl!!.split(",")

            // first delete existing to prevent duplicates
            assignmentACLRepository.deleteByAssignmentId(assignmentForm.assignmentId!!)

            for (userId in userIds) {
                val trimmedUserId = userId.trim()
                assignmentACLRepository.save(AssignmentACL(assignmentId = assignmentForm.assignmentId!!, userId = trimmedUserId))
            }
        }

        // first delete all assignees to prevent duplicates
        if (assignmentForm.assignmentId != null) {
            assigneeRepository.deleteByAssignmentId(assignmentForm.assignmentId!!)
        }
        val assigneesStr = assignmentForm.assignees?.split(",").orEmpty().map { it -> it.trim() }
        for (assigneeStr in assigneesStr) {
            if (!assigneeStr.isBlank()) {
                assigneeRepository.save(Assignee(assignmentId = assignment.id, authorUserId = assigneeStr))
            }
        }

        if (mustSetupGitConnection) {
            return "redirect:/assignment/setup-git/${assignmentForm.assignmentId}"
        } else {
            redirectAttributes.addFlashAttribute("message", "Assignment was successfully ${if (assignmentForm.editMode) "updated" else "created"}")
            return "redirect:/assignment/info/${assignmentForm.assignmentId}"
        }
    }

    /**
     * Creates a new [Assignment] based on the contents of an [AssignmentForm].
     * @param assignmentForm, the AssignmentForm from which the Assignment contents will be copied
     * @param principal is a [Principal] representing the user making the request
     * @return the created Assignment
     */
    private fun createAssignmentBasedOnForm(assignmentForm: AssignmentForm, principal: Principal): Assignment {
        val newAssignment = Assignment(id = assignmentForm.assignmentId!!, name = assignmentForm.assignmentName!!,
                packageName = assignmentForm.assignmentPackage, language = assignmentForm.language!!,
                dueDate = if (assignmentForm.dueDate != null) java.sql.Timestamp.valueOf(assignmentForm.dueDate) else null,
                acceptsStudentTests = assignmentForm.acceptsStudentTests,
                minStudentTests = assignmentForm.minStudentTests,
                calculateStudentTestsCoverage = assignmentForm.calculateStudentTestsCoverage,
                cooloffPeriod = assignmentForm.cooloffPeriod,
                maxMemoryMb = assignmentForm.maxMemoryMb, submissionMethod = assignmentForm.submissionMethod!!,
                gitRepositoryUrl = assignmentForm.gitRepositoryUrl!!, ownerUserId = principal.realName(),
                gitRepositoryFolder = assignmentForm.assignmentId!!, showLeaderBoard = assignmentForm.leaderboardType != null,
                hiddenTestsVisibility = assignmentForm.hiddenTestsVisibility,
                leaderboardType = assignmentForm.leaderboardType)

        // associate tags
        val tagNames = assignmentForm.assignmentTags?.toLowerCase()?.split(",")
        tagNames?.forEach {
            newAssignment.tags.add(assignmentTagRepository.findByName(it.trim().toLowerCase())
                    ?: AssignmentTag(name = it.trim().toLowerCase()))
        }
        return newAssignment
    }

    /**
     * Handles requests for for an [Assignment]'s "Info" page.
     *
     * @param assignmentId is a String identifying the relevant Assignment
     * @param model is a [ModelMap] that will be populated with information to use in a View
     * @param principal is a [Principal] representing the user making the request
     *
     * @return a String with the name of the relevant View
     */
    @RequestMapping(value = ["/info/{assignmentId}"], method = [(RequestMethod.GET)])
    @Transactional(readOnly = true)  // because of assignment.tags forced loading
    fun getAssignmentDetail(@PathVariable assignmentId: String, model: ModelMap, principal: Principal): String {

        val assignment = assignmentRepository.getOne(assignmentId)
        Hibernate.initialize(assignment.tags)
        val assignees = assigneeRepository.findByAssignmentIdOrderByAuthorUserId(assignmentId)
        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)
        val assignmentReports = assignmentReportRepository.findByAssignmentId(assignmentId)

        if (principal.realName() != assignment.ownerUserId && acl.find { it -> it.userId == principal.realName() } == null) {
            throw IllegalAccessError("Assignments can only be accessed by their owner or authorized teachers")
        }

        model["assignment"] = assignment
        model["assignees"] = assignees
        model["acl"] = acl
        model["tests"] = assignmentTestMethodRepository.findByAssignmentId(assignmentId)
        model["report"] = assignmentReports
        model["reportMsg"] = if (assignmentReports.any { it.type != AssignmentValidator.InfoType.INFO }) {
            "Assignment has errors! You have to fix them before activating it."
        } else {
            "Good job! Assignment has no errors and is ready to be activated."
        }

        // check if it has been setup for git connection and if there is a repository folder
        if (assignment.gitRepositoryPrivKey != null && File(assignmentsRootLocation, assignment.gitRepositoryFolder).exists()) {

            // get git info
            val git = Git.open(File(assignmentsRootLocation, assignment.gitRepositoryFolder))
            val lastCommitInfo = gitClient.getLastCommitInfo(git)

            model["lastCommitInfoStr"] = if (lastCommitInfo != null) lastCommitInfo.toString() else "No commits"
        }

        return "assignment-detail";
    }

    /**
     * Controller that handles HTTP GET requests for the [Assignment] edition form.
     *
     * @param assignmentId is a String representing the relevant Assignment
     * @param model is a [ModelMap] that will be populated with information to use in a View
     * @param principal is a [Principal] representing the user making the request
     *
     * @return a String with the name of the relevant View
     */
    @RequestMapping(value = ["/edit/{assignmentId}"], method = [(RequestMethod.GET)])
    @Transactional(readOnly = true)  // because of assignment.tags
    fun getEditAssignmentForm(@PathVariable assignmentId: String, model: ModelMap, principal: Principal): String {

        val assignment = assignmentRepository.getOne(assignmentId)
        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)

        if (principal.realName() != assignment.ownerUserId && acl.find { it -> it.userId == principal.realName() } == null) {
            throw IllegalAccessError("Assignments can only be changed by their owner or authorized teachers")
        }

        val assignmentForm = createAssignmentFormBasedOnAssignment(assignment, acl)

        assignmentForm.editMode = true

        model["assignmentForm"] = assignmentForm
        model["allTags"] = assignmentTagRepository.findAll()
                .map { "'" + it.name + "'" }
                .joinToString(separator = ",", prefix = "[", postfix = "]")

        return "assignment-form";
    }

    /**
     * Creates an [AssignmentForm] based on the [Assignment] object.
     * @param assignment is the Assignment with the information to place in the form
     * @param acl is a List of [AssignmentACL], containing the user's that have access to the repository
     * @return The created AssignmentForm
     */
    private fun createAssignmentFormBasedOnAssignment(assignment: Assignment, acl: List<AssignmentACL>): AssignmentForm {
        val assignmentForm = AssignmentForm(assignmentId = assignment.id,
                assignmentName = assignment.name,
                assignmentTags = if (assignment.tags.isEmpty()) null else assignment.tags.joinToString { t -> t.name },
                assignmentPackage = assignment.packageName,
                language = assignment.language,
                dueDate = assignment.dueDate?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime(),
                submissionMethod = assignment.submissionMethod,
                gitRepositoryUrl = assignment.gitRepositoryUrl,
                acceptsStudentTests = assignment.acceptsStudentTests,
                minStudentTests = assignment.minStudentTests,
                calculateStudentTestsCoverage = assignment.calculateStudentTestsCoverage,
                cooloffPeriod = assignment.cooloffPeriod,
                hiddenTestsVisibility = assignment.hiddenTestsVisibility,
                maxMemoryMb = assignment.maxMemoryMb,
                leaderboardType = assignment.leaderboardType
        )

        val assignees = assigneeRepository.findByAssignmentIdOrderByAuthorUserId(assignment.id)
        if (!assignees.isEmpty()) {
            val assigneesStr = assignees.map { it -> it.authorUserId }.joinToString(",\n")
            assignmentForm.assignees = assigneesStr
        }

        if (!acl.isEmpty()) {
            val otherTeachersStr = acl.map { it -> it.userId }.joinToString(",\n")
            assignmentForm.acl = otherTeachersStr
        }
        return assignmentForm
    }

    /**
     * Controller that handles requests for the refreshing of the Assignment's configuration from its Git repository.
     *
     * @param assignmentId is a String, identifying the relevant assignment
     * @param principal is a [Principal] representing the user making the request
     *
     * @return a ResponseEntity<String>
     */
    @RequestMapping(value = ["/refresh-git/{assignmentId}"], method = [(RequestMethod.POST)])
    fun refreshAssignmentGitRepository(@PathVariable assignmentId: String,
                                       principal: Principal): ResponseEntity<String> {

        // check that it exists
        val assignment = assignmentRepository.getOne(assignmentId)
        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)

        if (principal.realName() != assignment.ownerUserId && acl.find { it -> it.userId == principal.realName() } == null) {
            throw IllegalAccessError("Assignments can only be refreshed by their owner or authorized teachers")
        }

        try {
            LOG.info("Pulling git repository for ${assignmentId}")
            gitClient.pull(File(assignmentsRootLocation, assignment.gitRepositoryFolder), assignment.gitRepositoryPrivKey!!.toByteArray())

            // remove the reportId from all git submissions (if there are any) to signal the student that he should
            // generate a report again
            val gitSubmissionsForThisAssignment = gitSubmissionRepository.findByAssignmentId(assignmentId)
            for (gitSubmission in gitSubmissionsForThisAssignment) {
                gitSubmission.lastSubmissionId = null
                gitSubmissionRepository.save(gitSubmission)
            }

            if (!gitSubmissionsForThisAssignment.isEmpty()) {
                LOG.info("Reset reportId for ${gitSubmissionsForThisAssignment.size} git submissions")
            }

            // revalidate the assignment
            val report = assignmentTeacherFiles.checkAssignmentFiles(assignment, principal)

            // store the report in the DB (first, clear the previous report)
            assignmentReportRepository.deleteByAssignmentId(assignmentId)
            report.forEach {
                assignmentReportRepository.save(AssignmentReport(assignmentId = assignmentId, type = it.type,
                        message = it.message, description = it.description))
            }

        } catch (re: RefNotAdvertisedException) {
            LOG.warn("Couldn't pull git repository for ${assignmentId}: head is invalid")
            return ResponseEntity("{ \"error\": \"Error pulling from ${assignment.gitRepositoryUrl}. Probably you don't have any commits yet.\"}", HttpStatus.INTERNAL_SERVER_ERROR)
        } catch (e: Exception) {
            LOG.warn("Couldn't pull git repository for ${assignmentId}", e)
            return ResponseEntity("{ \"error\": \"Error pulling from ${assignment.gitRepositoryUrl}\"}", HttpStatus.INTERNAL_SERVER_ERROR)
        }

        return ResponseEntity("{ \"success\": \"true\"}", HttpStatus.OK);
    }

    /**
     * Controller that handles requests for the creation of the connection between the [Assignment] and the Git repository
     * that contains its configuration.
     *
     * @param assignmentId is a String, identifying the relevant Assignment
     * @param model is a [ModelMap] that will be populated with information to use in a View
     * @param principal is a [Principal] representing the user making the request
     *
     * @return a String with the name of the relevant View
     */
    @RequestMapping(value = ["/setup-git/{assignmentId}"], method = [(RequestMethod.GET)])
    fun setupAssignmentToGitRepository(@PathVariable assignmentId: String, model: ModelMap, principal: Principal): String {

        val assignment = assignmentRepository.getOne(assignmentId)

        if (principal.realName() != assignment.ownerUserId) {
            throw IllegalAccessError("Assignments can only be changed by their owner")
        }

        if (assignment.gitRepositoryPubKey == null) {

            // generate key pair
            val (privKey, pubKey) = gitClient.generateKeyPair()

            assignment.gitRepositoryPrivKey = String(privKey)
            assignment.gitRepositoryPubKey = String(pubKey)
            assignmentRepository.save(assignment)
        }

        if (assignment.gitRepositoryUrl.orEmpty().contains("github")) {
            val (username, reponame) = gitClient.getGitRepoInfo(assignment.gitRepositoryUrl)
            model["repositorySettingsUrl"] = "https://github.com/${username}/${reponame}/settings/keys"
        }

        model["assignment"] = assignment

        return "setup-git"
    }

    /**
     * Controller to handle requests related with connecting an [Assigment] with a git repository. This is needed
     * to obtain the information that is defined by code (instructions, unit tests, etc).
     * @param assignmentId is a String representing the relevant Assignment
     * @param redirectAttributes is a RedirectAttributes
     * @param model is a [ModelMap] that will be populated with information to use in a View
     * @param principal is a [Principal] representing the user making the request
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/setup-git/{assignmentId}"], method = [(RequestMethod.POST)])
    fun connectAssignmentToGitRepository(@PathVariable assignmentId: String, redirectAttributes: RedirectAttributes,
                                         model: ModelMap, principal: Principal): String {

        val assignment = assignmentRepository.getOne(assignmentId)

        if (principal.realName() != assignment.ownerUserId) {
            throw IllegalAccessError("Assignments can only be changed by their owner")
        }

        if (assignment.gitRepositoryPrivKey == null) {
            LOG.warn("gitRepositoryUrl is null???")
            redirectAttributes.addFlashAttribute("error", "Something went wrong with the credentials generation. Please try again")
            return "redirect:/assignment/setup-git/${assignment.id}"
        }

        run {
            val assignmentFolder = File(assignmentsRootLocation, assignment.gitRepositoryFolder)
            if (assignmentFolder.exists()) {
                assignmentFolder.deleteRecursively()
            }
        }

        val gitRepository = assignment.gitRepositoryUrl
        try {
            val directory = File(assignmentsRootLocation, assignment.gitRepositoryFolder)
            gitClient.clone(gitRepository, directory, assignment.gitRepositoryPrivKey!!.toByteArray())
            LOG.info("[${assignmentId}] Successfuly cloned ${gitRepository} to ${directory}")
        } catch (e: Exception) {
            LOG.info("Error cloning ${gitRepository} - ${e}")
            model["error"] = "Error cloning ${gitRepository} - ${e.message}"
            model["assignment"] = assignment
            return "setup-git"
        }

        // check that the assignment repository is a valid assignment structure
        val report = assignmentTeacherFiles.checkAssignmentFiles(assignment, principal)

        // store the report in the DB (first, clear the previous report)
        assignmentReportRepository.deleteByAssignmentId(assignmentId)
        report.forEach {
            assignmentReportRepository.save(AssignmentReport(assignmentId = assignmentId, type = it.type,
                    message = it.message, description = it.description))
        }

        if (report.any { it.type == AssignmentValidator.InfoType.ERROR }) {
            assignmentRepository.save(assignment)  // assignment.buildResult was updated

            redirectAttributes.addFlashAttribute("error", "Assignment has problems. Please check the 'Validation Report'")
            LOG.info("Assignment has problems. Please check the 'Validation Report'")
            return "redirect:/assignment/info/${assignment.id}"
        }

        redirectAttributes.addFlashAttribute("message", "Assignment was successfully created and connected to git repository")
        return "redirect:/assignment/info/${assignment.id}"
    }

    /**
     * Controller to handle the page that lists the [Assignment]s to which the logged-in
     * user has access.
     * @param tags is a String containing the names of multiple tags. Each tag name is separated by a comma. Only the
     * assignments that have all the tags will be placed in the model.
     * @param model is a [ModelMap] that will be populated with information to use in a View
     * @param principal is a [Principal] representing the user making the request
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/my"], method = [(RequestMethod.GET)])
    fun listMyAssignments(@RequestParam(name = "tags", required = false) tags: String?,
                          model: ModelMap, principal: Principal): String {

        listMyFilteredAssignments(principal, tags, model, archived = false)
        return "teacher-assignments-list"
    }

    /**
     * Controller to handle the page that lists the **archived** assignments to which the logged-in
     * teacher has access.
     * @param tags is a String containing the names of multiple tags. Each tag name is separated by a comma. Only the
     * assignments that have all the tags will be placed in the model.
     * @param model is a [ModelMap] that will be populated with information to use in a View
     * @param principal is a [Principal] representing the user making the request
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/archived"], method = [(RequestMethod.GET)])
    fun listMyArchivedAssignments(@RequestParam(name = "tags", required = false) tags: String?,
                                  model: ModelMap, principal: Principal): String {

        listMyFilteredAssignments(principal, tags, model, archived = true)
        return "teacher-assignments-list"
    }

    /**
     * Controller to handle the deletion of an [Assignment].
     * @param assignmentId is a String representing the relevant Assignment
     * @param redirectAttributes is a RedirectAttributes
     * @param principal is a [Principal] representing the user making the request
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/delete/{assignmentId}"], method = [(RequestMethod.POST)])
    fun deleteAssignment(@PathVariable assignmentId: String, redirectAttributes: RedirectAttributes,
                         principal: Principal): String {

        val assignment = assignmentRepository.getOne(assignmentId)

        if (principal.realName() != assignment.ownerUserId) {
            throw IllegalAccessError("Assignments can only be changed by their owner")
        }

        if (submissionRepository.countByAssignmentId(assignment.id).toInt() > 0) {
            redirectAttributes.addFlashAttribute("error", "Assignment can't be deleted because it has submissions")
            return "redirect:/assignment/my"
        }

        assignmentRepository.deleteById(assignmentId)
        assignmentACLRepository.deleteByAssignmentId(assignmentId)
        assignmentReportRepository.deleteByAssignmentId(assignmentId)

        val rootFolder = File(assignmentsRootLocation, assignment.gitRepositoryFolder)
        if (!rootFolder.delete()) {
            LOG.warn("Unable to delete ${rootFolder.absolutePath}")
        }
        LOG.info("Removed assignment ${assignment.id}")

        redirectAttributes.addFlashAttribute("message", "Assignment was successfully deleted")
        return "redirect:/assignment/my"
    }

    /**
     * Controller that allows toggling the status of an [Assignment] between "active" and "inactive".
     * @param assignmentId is a String representing the relevant Assignment
     * @param redirectAttributes is a RedirectAttributes
     * @param principal is a [Principal] representing the user making the request
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/toggle-status/{assignmentId}"], method = [(RequestMethod.GET), (RequestMethod.POST)])
    fun toggleAssignmentStatus(@PathVariable assignmentId: String, redirectAttributes: RedirectAttributes,
                               principal: Principal): String {

        val assignment = assignmentRepository.getOne(assignmentId)
        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)

        if (principal.realName() != assignment.ownerUserId && acl.find { it -> it.userId == principal.realName() } == null) {
            throw IllegalAccessError("Assignments can only be changed by their owner or authorized teachers")
        }

        if (assignment.active != true) {

            // check if it has been setup for git connection and if there is a repository folder
            if (!File(assignmentsRootLocation, assignment.gitRepositoryFolder).exists()) {
                redirectAttributes.addFlashAttribute("error", "Can't mark assignment as active since it is not connected to a git repository.")
                return "redirect:/assignment/my"
            }

            val report = assignmentTeacherFiles.checkAssignmentFiles(assignment, principal)

            // store the report in the DB (first, clear the previous report)
            assignmentReportRepository.deleteByAssignmentId(assignmentId)
            report.forEach {
                assignmentReportRepository.save(AssignmentReport(assignmentId = assignmentId, type = it.type,
                        message = it.message, description = it.description))
            }

            if (report.any { it.type == AssignmentValidator.InfoType.ERROR }) {  // TODO: Should it be warnings also??
                assignmentRepository.save(assignment)  // assignment.buildResult was updated

                redirectAttributes.addFlashAttribute("error", "Assignment has problems. Please check the 'Validation Report'")
                LOG.info("Assignment has problems. Please check the 'Validation Report'")
                return "redirect:/assignment/info/${assignmentId}"
            }
        }

        assignment.active = !assignment.active
        assignmentRepository.save(assignment)

        redirectAttributes.addFlashAttribute("message", "Assignment was marked ${if (assignment.active) "active" else "inactive"}")
        return "redirect:/assignment/my"
    }

    /**
     * Controller that allows the archiving of an [Assignment].
     * @param assignmentId is a String representing the relevant Assignment
     * @param redirectAttributes is a RedirectAttributes
     * @param principal is a [Principal] representing the user making the request
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/archive/{assignmentId}"], method = [(RequestMethod.POST)])
    fun archiveAssignment(@PathVariable assignmentId: String,
                          redirectAttributes: RedirectAttributes,
                          principal: Principal): String {

        // check that it exists
        val assignment = assignmentRepository.getOne(assignmentId)
        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)

        if (principal.realName() != assignment.ownerUserId && acl.find { it -> it.userId == principal.realName() } == null) {
            throw IllegalAccessError("Assignments can only be archived by their owner or authorized teachers")
        }

        assignment.archived = true
        assignmentRepository.save(assignment)

        // evict the "archiveAssignmentsCache" cache
        cacheManager.getCache("archivedAssignmentsCache").clear()

        redirectAttributes.addFlashAttribute("message", "Assignment was archived. You can now find it in the Archived assignments page")
        return "redirect:/assignment/my"

    }

    /**
     * Controller that allows marking the latest submission of each group as final.
     * Note that the latest submission might not be the best one.
     * @param assignmentId is a String representing the relevant Assignment
     * @param redirectAttributes is a RedirectAttributes
     * @param principal is a [Principal] representing the user making the request
     * @return A String with the name of the relevant View
     */
    @RequestMapping(value = ["/markAllAsFinal/{assignmentId}"], method = [(RequestMethod.POST)])
    fun markAllSubmissionsAsFinal(@PathVariable assignmentId: String,
                                  redirectAttributes: RedirectAttributes,
                                  principal: Principal): String {

        val assignment = assignmentRepository.getOne(assignmentId)
        val acl = assignmentACLRepository.findByAssignmentId(assignment.id)

        if (principal.realName() != assignment.ownerUserId && acl.find { it -> it.userId == principal.realName() } == null) {
            throw IllegalAccessError("Submissions can only be marked as final by the assignment owner or authorized teachers")
        }

        val submissionInfoList = submissionService.getSubmissionsList(assignment)
        submissionInfoList.forEach {
            val lastSubmission = it.lastSubmission
            if (!lastSubmission.markedAsFinal) {
                submissionService.markAsFinal(lastSubmission)
                submissionRepository.save(lastSubmission)
            }
        }

        LOG.info("[${principal.realName()}] marked all ${assignmentId} submissions as final (${submissionInfoList.count()} submissions)")

        redirectAttributes.addFlashAttribute("message", "All submissions for ${assignmentId} marked as final. " +
                "Notice that these may not be their best submissions, just the last ones. You may now review each one individually.")
        return "redirect:/report/${assignmentId}"
    }

    /**
     * Controller that handles the exportation of an assignment and (optionally) its submissions.
     * @param assignmentId is a String, identifying the relevant Assignment
     * @return A ResponseEntity<String>
     */
    @RequestMapping(value = ["/export/{assignmentId}"], method = [(RequestMethod.GET)])
    fun startAssignmentExport(@PathVariable assignmentId: String,
                         @RequestParam(name="includeSubmissions", required = false) includeSubmissions: Boolean = false,
                         principal: Principal): String {

        val assignment = assignmentRepository.findById(assignmentId).orElse(null) ?:
            throw IllegalArgumentException("assignment ${assignmentId} is not registered")

        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)

        if (principal.realName() != assignment.ownerUserId && acl.find { it.userId == principal.realName() } == null) {
            throw IllegalAccessError("Exporting assignments is restricted to their owner or authorized teachers")
        }

        val taskId = "${System.currentTimeMillis()}"

        // this will run asynchronously (except for tests)
        LOG.info("Started async export for assignment ${assignmentId} (taskId: $taskId)")
        assignmentService.exportAssignment(assignmentId, includeSubmissions, taskId)

        if (pendingTasks.get(taskId) != null) {
            return "redirect:/assignment/export-result/${taskId}"
        }

        return "redirect:/assignment/export-status/${taskId}"
    }

    /**
     * Checks the status of a given export. This is called from a page in "polling mode" - refreshing periodically
     */
    @RequestMapping(value = ["/export-status/{taskId}"], method = [(RequestMethod.GET)])
    fun getAssignmentExportStatus(@PathVariable taskId: String, model: ModelMap) : String {

        if (pendingTasks.get(taskId) == null) {
            // task hasn't finished
            model["autoRefresh"] = true
            model["message"] = "Export in progress... Please wait"
            return "export-status"
        } else {
            // task has finished - redirect to the page that will download the file
            model["autoRefresh"] = false
            model["message"] = "Export successful"
            model["redirect"] = "assignment/export-result/${taskId}"
            return "export-status"
        }
    }

    @RequestMapping(value = ["/export-result/{taskId}"], method = [(RequestMethod.GET)],
        produces = [org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE])
    @ResponseBody
    fun getAssignmentExportFile(@PathVariable taskId: String,
                              response: HttpServletResponse): FileSystemResource {

        val (filename, zipFile) = pendingTasks.get(taskId) as Pair<String,File>
        response.setHeader("Content-Disposition", "attachment; filename=${filename}.dp")
        return FileSystemResource(zipFile)

        // TODO: delete the zipFile
    }

    /**
     * Controller that responds with the page to import assignments via upload
     *
     * @return the view name
     */
    @RequestMapping(value = ["/import"], method = [(RequestMethod.GET)])
    fun showImportAssignmentPage(): String {
        return "teacher-import-assignment"
    }

    /**
     * Controller that handles the import of an Assignment and (possibly) its submissions through a previously exported
     * .dp file
     *
     * @param file is a [MultipartFile]
     * @param principal is a [Principal] representing the user making the request
     * @param request is an HttpServletRequest
     *
     * @return the view name
     */
    @RequestMapping(value = ["/import"], method = [(RequestMethod.POST)])
    fun importAssignment(@RequestParam("file") file: MultipartFile,
                         principal: Principal,
                         redirectAttributes: RedirectAttributes,
                         request: HttpServletRequest): String {

        if (!file.originalFilename.endsWith(".dp", ignoreCase = true)) {
            redirectAttributes.addFlashAttribute("error", "Error: File must be .dp")
            return "redirect:/assignment/import"
        }

        LOG.info("[${principal.realName()}] uploaded ${file.originalFilename}")

        val tempFolder = Files.createTempDirectory("import").toFile()
        val destinationFile = File(tempFolder, "${System.currentTimeMillis()}-${file.originalFilename}.zip")
        Files.copy(file.inputStream, destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

        val destinationFolder = zipService.unzip(destinationFile.toPath(), "extracted")
        val assignmentJSONFile = File(destinationFolder, EXPORTED_ASSIGNMENT_JSON_FILENAME)
        val submissionsJSONFile = File(destinationFolder, EXPORTED_SUBMISSIONS_JSON_FILENAME)
        val gitSubmissionsJSONFile = File(destinationFolder, EXPORTED_GIT_SUBMISSIONS_JSON_FILENAME)
        val originalSubmissionsFolder = File(destinationFolder, EXPORTED_ORIGINAL_SUBMISSIONS_FOLDER)

        if (!assignmentJSONFile.exists()) {
            redirectAttributes.addFlashAttribute("error", "Error: File is not valid (missing assignment.json)")
            return "redirect:/assignment/import"
        }

        val mapper = ObjectMapper().registerModule(KotlinModule())

        val (assignmentId, errorMessage) = assignmentService.createAssignmentFromImportedFile(mapper, assignmentJSONFile, principal)
        if (errorMessage != null) {
            redirectAttributes.addFlashAttribute("error", errorMessage)
            return "redirect:/assignment/import"
        } else {
            LOG.info("Imported $assignmentId")
        }

        if (submissionsJSONFile.exists()) {
            val errorMessage2 = assignmentService.importSubmissionsFromImportedFile(mapper, submissionsJSONFile)
            if (errorMessage2 != null) {
                redirectAttributes.addFlashAttribute("error", errorMessage2)
                return "redirect:/assignment/import"
            }

            if (gitSubmissionsJSONFile.exists()) {
                val errorMessage3 = assignmentService.importGitSubmissionsFromImportedFile(mapper, gitSubmissionsJSONFile)
                if (errorMessage3 != null) {
                    redirectAttributes.addFlashAttribute("error", errorMessage3)
                    return "redirect:/assignment/import"
                }
            }

            // import all the original submission files
            if (originalSubmissionsFolder.exists()) {
                val assignment = assignmentRepository.getOne(assignmentId)
                when (assignment.submissionMethod) {
                    SubmissionMethod.UPLOAD -> FileUtils.copyDirectory(originalSubmissionsFolder, File(uploadSubmissionsRootLocation))
                    SubmissionMethod.GIT -> FileUtils.copyDirectory(originalSubmissionsFolder, File(gitSubmissionsRootLocation))
                }
            }

            redirectAttributes.addFlashAttribute("message", "Imported successfully ${assignmentId} and all its submissions")
            return "redirect:/report/${assignmentId}"
        } else {
            redirectAttributes.addFlashAttribute("message", "Imported successfully ${assignmentId}. Submissions were not imported")
            return "redirect:/assignment/info/${assignmentId}"
        }
    }




    /**
     * Collects [Assignment]s that have certain [tags] into the [model].
     * @param model is a [ModelMap] that will be populated with information to use in a View.
     * @param tags is a String containing the names of multiple tags. Each tag name is separated by a comma. Only the
     * assignments that have all the tags will be placed in the model.
     */
    private fun listMyFilteredAssignments(principal: Principal, tags: String?, model: ModelMap, archived: Boolean) {
        var assignments = assignmentService.getMyAssignments(principal, archived)

        if (tags != null) {
            val tagsDB = tags.split(",").map { assignmentTagRepository.findByName(it) }
            assignments = assignments.filter { it.tags.intersect(tagsDB).size == tagsDB.size }
        }

        model["assignments"] = assignments
        model["archived"] = false
        model["allTags"] = assignmentTagRepository.findAll()
                .map { it.selected = tags?.split(",")?.contains(it.name) ?: false; it }
                .sortedBy { it.name }
    }



}