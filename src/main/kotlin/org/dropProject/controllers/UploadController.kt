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

import org.springframework.stereotype.Controller
import org.springframework.ui.ModelMap
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import org.dropProject.forms.UploadForm
import org.dropProject.storage.StorageException
import org.dropProject.storage.StorageService
import java.io.File
import javax.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.dropProject.data.AuthorDetails
import java.security.Principal
import java.util.*
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.EnableAsync
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.RefNotAdvertisedException
import org.springframework.security.access.AccessDeniedException
import org.dropProject.dao.*
import org.dropProject.data.SubmissionResult
import org.dropProject.extensions.realName
import org.dropProject.forms.SubmissionMethod
import org.dropProject.repository.*
import org.dropProject.services.*
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executor
import javax.servlet.http.HttpServletRequest

/**
 * UploadController is an MVC controller class to handle requests related with the upload of submissions.
 */
@Controller
@EnableAsync
class UploadController(
        val storageService: StorageService,
        val buildWorker: BuildWorker,
        val projectGroupRepository: ProjectGroupRepository,
        val submissionRepository: SubmissionRepository,
        val gitSubmissionRepository: GitSubmissionRepository,
        val assignmentRepository: AssignmentRepository,
        val assignmentACLRepository: AssignmentACLRepository,
        val assigneeRepository: AssigneeRepository,
        val asyncExecutor: Executor,
        val assignmentTeacherFiles: AssignmentTeacherFiles,
        val assignmentService: AssignmentService,
        val gitClient: GitClient,
        val gitSubmissionService: GitSubmissionService,
        val submissionService: SubmissionService,
        val zipService: ZipService,
        val projectGroupService: ProjectGroupService,
        val i18n: MessageSource
        ) {

    @Value("\${storage.rootLocation}/git")
    val gitSubmissionsRootLocation : String = "submissions/git"

    @Value("\${mavenizedProjects.rootLocation}")
    val mavenizedProjectsRootLocation : String = ""

    @Value("\${spring.web.locale}")
    val currentLocale : Locale = Locale.getDefault()

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    init {
        storageService.init()
    }

    /**
     * Controller that handles related with the base URL.
     *
     * If the principal can only access one [Assignment], then that assignment's upload form will be displayed. Otherwise,
     * a list of assignments will be displayed.
     *
     * @param model is a [ModelMap] that will be populated with the information to use in a View
     * @param principal is a [Principal] representing the user making the request
     * @param request is a HttpServletRequest
     *
     * @return A String identifying the relevant View
     */
    @RequestMapping(value = ["/"], method = [(RequestMethod.GET)])
    fun getUploadForm(model: ModelMap, principal: Principal, request: HttpServletRequest): String {

        val assignments = ArrayList<Assignment>()

        val assignees = assigneeRepository.findByAuthorUserId(principal.realName())
        for (assignee in assignees) {
            val assignment = assignmentRepository.getById(assignee.assignmentId)
            if (assignment.active == true) {
                assignments.add(assignment)
            }
        }

        if (request.isUserInRole("TEACHER")) {
            val assignmentsIOwn = assignmentRepository.findByOwnerUserId(principal.realName())

            for (assignmentIOwn in assignmentsIOwn) {
                if (!assignmentIOwn.archived) {
                    assignments.add(assignmentIOwn)
                }
            }
        }

        if (assignments.size == 1) {
            // redirect to that assignment
            return "redirect:/upload/${assignments[0].id}"
        }

        model["assignments"] = assignments

        return "student-assignments-list"
    }


    /**
     * Controller that handles requests related with the [Assignment]'s upload page.
     *
     * @param model is a [ModelMap] that will be populated with the information to use in a View
     * @param principal is a [Principal] representing the user making the request
     * @param assignmentId is a String, identifying the relevant Assigment
     * @param request is an HttpServletRequest
     *
     * @return A String identifying the relevant View
     */
    @RequestMapping(value = ["/upload/{assignmentId}"], method = [(RequestMethod.GET)])
    fun getUploadForm(model: ModelMap, principal: Principal,
                      @PathVariable("assignmentId") assignmentId: String,
                      request: HttpServletRequest): String {

        val assignment = assignmentRepository.findById(assignmentId).orElse(null) ?: throw AssignmentNotFoundException(assignmentId)

        if (!request.isUserInRole("TEACHER")) {

            if (!assignment.active) {
                throw org.springframework.security.access.AccessDeniedException("Submissions are not open to this assignment")
            }

            assignmentService.checkAssignees(assignmentId, principal.realName())

        } else {

            if (!assignment.active) {
                val acl = assignmentACLRepository.findByAssignmentId(assignmentId)
                if (principal.realName() != assignment.ownerUserId && acl.find { it -> it.userId == principal.realName() } == null) {
                    throw IllegalAccessError("Assignments can only be accessed by their owner or authorized teachers")
                }
            }
        }

        model["assignment"] = assignment
        model["numSubmissions"] = submissionRepository.countBySubmitterUserIdAndAssignmentId(principal.realName(), assignment.id)
        model["instructionsFragment"] = assignmentTeacherFiles.getHtmlInstructionsFragment(assignment)
        model["packageTree"] = assignmentTeacherFiles.buildPackageTree(
                assignment.packageName, assignment.language, assignment.acceptsStudentTests)

        if (assignment.cooloffPeriod != null && !request.isUserInRole("TEACHER")) {
            val lastSubmission = submissionService.getLastSubmission(principal, assignmentId)
            if (lastSubmission != null) {
                val nextSubmissionTime = submissionService.calculateCoolOff(lastSubmission, assignment)
                if (nextSubmissionTime != null) {
                    model["coolOffEnd"] = nextSubmissionTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                    LOG.info("[${principal.realName()}] can't submit because he is in cool-off period")
                }
            }
        }

        if (assignment.submissionMethod == SubmissionMethod.UPLOAD) {

            val groups = projectGroupRepository.getGroupsForAuthor(principal.realName())
            val submission = submissionRepository.findFirstByGroupInAndAssignmentIdOrderBySubmissionDateDesc(groups, assignmentId)

            model["uploadForm"] = UploadForm(assignment.id)
            model["uploadSubmission"] = submission
            return "student-upload-form"
        } else {

            val gitSubmission =
                    gitSubmissionRepository.findBySubmitterUserIdAndAssignmentId(principal.realName(), assignmentId)
                    ?:
                    gitSubmissionService.findGitSubmissionBy(principal.realName(), assignmentId) // check if it belongs to a group who has already a git submission

            if (gitSubmission?.connected == true) {
                // get last commit info
                val git = Git.open(File(gitSubmissionsRootLocation, gitSubmission.getFolderRelativeToStorageRoot()))
                val lastCommitInfo = gitClient.getLastCommitInfo(git)
                model["lastCommitInfo"] = lastCommitInfo
            }

            model["gitSubmission"] = gitSubmission

            return "student-git-form"
        }


    }


    /**
     * Controller that handles requests for the actual file upload that delivers/submits the student's code.
     *
     * @param uploadForm is an [Uploadform]
     * @param bindingResult is a [BindingResult]
     * @param file is a [MultipartFile]
     * @param principal is a [Principal] representing the user making the request
     * @param request is an HttpServletRequest
     *
     * @return a ResponseEntity<String>
     */
    @RequestMapping(value = ["/upload"], method = [(RequestMethod.POST)])
    fun upload(@Valid @ModelAttribute("uploadForm") uploadForm: UploadForm,
               bindingResult: BindingResult,
               @RequestParam("file") file: MultipartFile,
               principal: Principal,
               request: HttpServletRequest): ResponseEntity<SubmissionResult> {

        return submissionService.uploadSubmission(bindingResult, uploadForm, request, principal, file,
            assignmentRepository, assignmentService)
    }

    /**
     * Controller that handles requests for the [Submission]'s rebuild process. The rebuild process is a process where by
     * a student's submission gets compiled and evaluated again. It can be useful, for example, in situations where an
     * error was detected in the teacher's tests and the teacher wants to apply corrected tests to the student's submission.
     *
     * @param submissionId is a Long, identifying the student's Submission
     * @param principal is a [Principal] representing the user making the request
     *
     * @return a String identifying the relevant View
     */
    @RequestMapping(value = ["/rebuild/{submissionId}"], method = [(RequestMethod.POST)])
    fun rebuild(@PathVariable submissionId: Long,
                principal: Principal) : String {

        LOG.info("Rebuilding ${submissionId}")

        val submission = submissionRepository.findById(submissionId).orElse(null)
        val mavenizedProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission, wasRebuilt = false)

        // TODO: This should go into submission
        var authors = ArrayList<AuthorDetails>()
        for (authorDB in submission.group.authors) {
            authors.add(AuthorDetails(name = authorDB.name, number = authorDB.userId,
                    submitter = submission.submitterUserId == authorDB.userId))
        }

        submission.setStatus(SubmissionStatus.REBUILDING, dontUpdateStatusDate = true)
        submissionRepository.save(submission)

        buildWorker.checkProject(mavenizedProjectFolder, authors.joinToString(separator = "|"), submission,
                dontChangeStatusDate = true,
                principalName = principal.realName())

        return "redirect:/buildReport/${submissionId}";
    }

    // reaplies the assignment files, which may have changed since the submission
    @RequestMapping(value = ["/rebuildFull/{submissionId}"], method = [(RequestMethod.POST)])
    fun rebuildFull(@PathVariable submissionId: Long,
                principal: Principal) : String {

        LOG.info("Rebuilding full ${submissionId}")

        val submission = submissionRepository.findById(submissionId).orElse(null) ?: throw SubmissionNotFoundException(submissionId)

        val assignment = assignmentRepository.getById(submission.assignmentId)

        // create another submission that is a clone of this one, to preserve the original submission
        val rebuiltSubmission = Submission(submissionId = submission.submissionId,
                gitSubmissionId = submission.gitSubmissionId,
                submissionDate = submission.submissionDate,
                submitterUserId = submission.submitterUserId,
                assignmentId = submission.assignmentId,
                assignmentGitHash = submission.assignmentGitHash,
                submissionFolder = submission.submissionFolder,
                status = SubmissionStatus.SUBMITTED_FOR_REBUILD.code,
                statusDate = Date())
        rebuiltSubmission.group = submission.group

        val projectFolder = submissionService.getOriginalProjectFolder(rebuiltSubmission)
        val authors = submissionService.getProjectAuthors(projectFolder)

        submissionRepository.save(rebuiltSubmission)
        submissionService.buildSubmission(projectFolder, assignment, authors.joinToString(separator = "|"), rebuiltSubmission,
                asyncExecutor, teacherRebuild = true, principal = principal)

        return "redirect:/buildReport/${rebuiltSubmission.id}";
    }

    /**
     * Controller that handles requests for marking a [Submission] as "final". Since, by design, students' can make
     * multiple submissions in DP, marking a submission as "final" is the way for the teacher to indicate that it's the
     * one that shall be considered when exporting the submissions' data (e.g. for grading purposes).
     *
     * Note that only one submission per [ProjectGroup] can be marked as final. This means that, when a certain submission
     * is marked as final, any previously "finalized" submission by the same group will not be final anymore.
     *
     * @param submissionId is a Long, identifying the student's Submission
     * @param redirectToSubmissionsList is a Boolean. If true, then after the marking process is done, the user will be
     * redirected to the group's submissions list. Otherwise, the redirection will be done to the the final submission's
     * build report.
     * @param principal is a [Principal] representing the user making the request
     *
     * @return a String identifying the relevant View
     */
    @RequestMapping(value = ["/markAsFinal/{submissionId}"], method = [(RequestMethod.POST)])
    fun markAsFinal(@PathVariable submissionId: Long,
                    @RequestParam(name="redirectToSubmissionsList", required = false, defaultValue = "false")
                    redirectToSubmissionsList: Boolean,
                    principal: Principal) : String {

        val submission = submissionRepository.findById(submissionId).orElse(null)
        val assignment = assignmentRepository.findById(submission.assignmentId).orElse(null)

        val acl = assignmentACLRepository.findByAssignmentId(assignment.id)
        if (principal.realName() != assignment.ownerUserId && acl.find { it -> it.userId == principal.realName() } == null) {
            throw IllegalAccessError("Submissions can only be marked as final by the assignment owner or authorized teachers")
        }

        if (submission.markedAsFinal) {
            submission.markedAsFinal = false
            LOG.info("Unmarking as final: ${submissionId}")

        } else {
            LOG.info("Marking as final: ${submissionId}")
            // marks this submission as final, and all other submissions for the same group and assignment as not final
            submissionService.markAsFinal(submission)
        }

        submissionRepository.save(submission)

        if (redirectToSubmissionsList) {
            return "redirect:/submissions/?assignmentId=${submission.assignmentId}&groupId=${submission.group.id}"
        } else {
            return "redirect:/buildReport/${submissionId}"
        }
    }

    // TODO: This should remove non-final submissions for groups where there is already a submission marked as final
    // removes all files related to non-final submissions
    @RequestMapping(value = ["/cleanup/{assignmentId}"], method = [(RequestMethod.POST)])
    fun cleanup(@PathVariable assignmentId: String, request: HttpServletRequest) : String {

        if (!request.isUserInRole("DROP_PROJECT_ADMIN")) {
            throw IllegalAccessError("Assignment can only be cleaned-up by admin")
        }

        LOG.info("Removing all non-final submission files related to ${assignmentId}")

        val nonFinalSubmissions = submissionRepository.findByAssignmentIdAndMarkedAsFinal(assignmentId, false)
        submissionService.deleteMavenizedFolderFor(nonFinalSubmissions)

        // TODO: Should show a toast saying how many files were deleted
        return "redirect:/report/${assignmentId}";
    }

    /**
     * Controller that handles requests for connecting a student's GitHub repository with an [Assignment] available in DP.
     *
     * This process works like a two step wizard. This is the first part. The second part is in "/student/setup-git-2".
     *
     * @param assignmentId is a String, identifying the relevant Assignment
     * @param gitRepositoryUrl is a String with the student's GitHub repository URL
     * @param model is a [ModelMap] that will be populated with the information to use in a View
     * @param principal is a [Principal] representing the user making the request
     *
     * @return A String identifying the relevant View
     */
    @RequestMapping(value = ["/student/setup-git"], method = [(RequestMethod.POST)])
    fun setupStudentSubmissionUsingGitRepository(@RequestParam("assignmentId") assignmentId: String,
                                                 @RequestParam("gitRepositoryUrl") gitRepositoryUrl: String?,
                                                 model: ModelMap, principal: Principal,
                                                 request: HttpServletRequest): String {

        val assignment = assignmentRepository.getById(assignmentId)

        if (!request.isUserInRole("TEACHER")) {

            if (!assignment.active) {
                throw AccessDeniedException("Submissions are not open to this assignment")
            }

            assignmentService.checkAssignees(assignmentId, principal.realName())
        }

        model["assignment"] = assignment
        model["numSubmissions"] = submissionRepository.countBySubmitterUserIdAndAssignmentId(principal.realName(), assignment.id)
        model["instructionsFragment"] = assignmentTeacherFiles.getHtmlInstructionsFragment(assignment)
        model["packageTree"] = assignmentTeacherFiles.buildPackageTree(
                assignment.packageName, assignment.language, assignment.acceptsStudentTests)

        if (gitRepositoryUrl.isNullOrBlank()) {
            model["gitRepoErrorMsg"] = i18n.getMessage("student.git.setup.must-fill-url", null, currentLocale)
            return "student-git-form"
        }

        if (!gitClient.checkValidSSHGithubURL(gitRepositoryUrl)) {
            model["gitRepoErrorMsg"] = i18n.getMessage("student.git.setup.invalid-url", null, currentLocale)
            return "student-git-form"
        }

        var gitSubmission =
                gitSubmissionRepository.findBySubmitterUserIdAndAssignmentId(principal.realName(), assignmentId)
                ?:
                gitSubmissionService.findGitSubmissionBy(principal.realName(), assignmentId) // check if it belongs to a group who has already a git submission

        // if there is a previous unconnected git connection, remove it
        if (gitSubmission != null && !gitSubmission.connected) {
            gitSubmissionRepository.delete(gitSubmission)
        }

        // generate key pair
        val (privKey, pubKey) = gitClient.generateKeyPair()

        gitSubmission = GitSubmission(assignmentId = assignmentId,
                    submitterUserId = principal.realName(), gitRepositoryUrl = gitRepositoryUrl)

        gitSubmission.gitRepositoryPrivKey = String(privKey)
        gitSubmission.gitRepositoryPubKey = String(pubKey)
        gitSubmissionRepository.save(gitSubmission)

        if (gitSubmission.gitRepositoryUrl.orEmpty().contains("github")) {
            val (username, reponame) = gitClient.getGitRepoInfo(gitSubmission.gitRepositoryUrl)
            model["repositorySettingsUrl"] = "https://github.com/${username}/${reponame}/settings/keys"
        }

        model["gitSubmission"] = gitSubmission

        return "student-setup-git"
    }

    /**
     * Controller that handles requests for connecting a student's GitHub repository with DP.
     *
     * This process works like a two step wizard. This is the second part. The first part is in "/student/setup-git".
     *
     * @param assignmentId
     * @param gitRepositoryUrl is a String
     * @param model is a [ModelMap] that will be populated with the information to use in a View
     * @param principal is a [Principal] representing the user making the request
     *
     * @return A String identifying the relevant View
     */
    @RequestMapping(value = ["/student/setup-git-2/{gitSubmissionId}"], method = [(RequestMethod.POST)])
    fun connectSubmissionToGitRepository(@PathVariable gitSubmissionId: String, redirectAttributes: RedirectAttributes,
                                         model: ModelMap, principal: Principal): String {

        val gitSubmission = gitSubmissionRepository.getById(gitSubmissionId.toLong())
        val assignment = assignmentRepository.getById(gitSubmission.assignmentId)

        if (!gitSubmission.connected) {

            run {
                val submissionFolder = File(gitSubmissionsRootLocation, gitSubmission.getFolderRelativeToStorageRoot())
                if (submissionFolder.exists()) {
                    submissionFolder.deleteRecursively()
                }
            }

            val gitRepository = gitSubmission.gitRepositoryUrl
            try {
                val projectFolder = File(gitSubmissionsRootLocation, gitSubmission.getFolderRelativeToStorageRoot())
                val git = gitClient.clone(gitRepository, projectFolder, gitSubmission.gitRepositoryPrivKey!!.toByteArray())
                LOG.info("[${gitSubmission}] Successfuly cloned ${gitRepository} to ${projectFolder}")

                // check that exists an AUTHORS.txt
                val authors = submissionService.getProjectAuthors(projectFolder)
                LOG.info("[${authors.joinToString(separator = "|")}] Connected DP to ${gitSubmission.gitRepositoryUrl}")

                // check if the principal is one of group elements
                if (authors.filter { it.number == principal.realName() }.isEmpty()) {
                    throw InvalidProjectStructureException("O utilizador que está a submeter tem que ser um dos elementos do grupo.")
                }

                val group = projectGroupService.getOrCreateProjectGroup(authors)
                gitSubmission.group = group

                val lastCommitInfo = gitClient.getLastCommitInfo(git)
                gitSubmission.lastCommitDate = lastCommitInfo?.date
                gitSubmission.connected = true
                gitSubmissionRepository.save(gitSubmission)

                // let's check if the other students of this group have "pending" gitsubmissions, i.e., gitsubmissions that are half-way
                // in that case, delete that submissions, since this one will now be the official for all the elements in the group
                for (student in authors) {
                    if (student.number != gitSubmission.submitterUserId) {
                        val gitSubmissionForOtherStudent =
                                gitSubmissionRepository.findBySubmitterUserIdAndAssignmentId(student.number, gitSubmission.assignmentId)

                        if (gitSubmissionForOtherStudent != null) {
                            if (gitSubmissionForOtherStudent.connected) {
                                throw IllegalArgumentException("One of the elements of the group had already a connected git submission")
                            } else {
                                gitSubmissionRepository.delete(gitSubmissionForOtherStudent)
                            }
                        }
                    }
                }

            } catch (ipse: InvalidProjectStructureException) {
                LOG.info("Invalid project structure: ${ipse.message}")
                model["error"] = "O projecto localizado no repositório ${gitRepository} tem uma estrutura inválida: ${ipse.message}"
                model["gitSubmission"] = gitSubmission
                return "student-setup-git"
            } catch (e: Exception) {
                LOG.info("Error cloning ${gitRepository} - ${e} - ${e.cause}")
                model["error"] = "Error cloning ${gitRepository} - ${e.message}"
                model["gitSubmission"] = gitSubmission
                return "student-setup-git"
            }
        }

        redirectAttributes.addFlashAttribute("message", "Ligado com sucesso ao repositório git")
        return "redirect:/upload/${assignment.id}"
    }

    /**
     * Controller that handles requests for the creation of a new submission via Git, by refreshing the contents
     * that are in the respective student's repository.
     *
     * @param submissionId is a String identifying the student's last Git submission
     * @param principal is a [Principal] representing the user making the requets
     *
     * @return a ResponseEntity<String>
     */
    @RequestMapping(value = ["/git-submission/refresh-git/{gitSubmissionId}"], method = [(RequestMethod.POST)])
    fun refreshSubmissionGitRepository(@PathVariable gitSubmissionId: String,
                                       principal: Principal): ResponseEntity<String> {

        // check that it exists
        val gitSubmission = gitSubmissionRepository.findById(gitSubmissionId.toLong()).orElse(null) ?:
            throw IllegalArgumentException("git submission ${gitSubmissionId} is not registered")

        if (!gitSubmission.group.contains(principal.realName())) {
            throw IllegalAccessError("Submissions can only be refreshed by their owners")
        }

        try {
            LOG.info("Pulling git repository for ${gitSubmissionId}")
            val git = gitClient.pull(File(gitSubmissionsRootLocation, gitSubmission.getFolderRelativeToStorageRoot()),
                    gitSubmission.gitRepositoryPrivKey!!.toByteArray())
            val lastCommitInfo = gitClient.getLastCommitInfo(git)

            if (lastCommitInfo?.date != gitSubmission.lastCommitDate) {
                gitSubmission.lastSubmissionId = null
                gitSubmissionRepository.save(gitSubmission)
            }

        } catch (re: RefNotAdvertisedException) {
            LOG.warn("Couldn't pull git repository for ${gitSubmissionId}: head is invalid")
            return ResponseEntity("{ \"error\": \"Error pulling from ${gitSubmission.gitRepositoryUrl}. Probably you don't have any commits yet.\"}", HttpStatus.INTERNAL_SERVER_ERROR)
        } catch (e: Exception) {
            LOG.warn("Couldn't pull git repository for ${gitSubmissionId}")
            return ResponseEntity("{ \"error\": \"Error pulling from ${gitSubmission.gitRepositoryUrl}\"}", HttpStatus.INTERNAL_SERVER_ERROR)
        }

        return ResponseEntity("{ \"success\": \"true\"}", HttpStatus.OK);
    }

    /**
     * Controller that handles requests for the generation of a [GitSubmission]'s build report.
     *
     * @param gitSubmissionId is a String identifying a [GitSubmission]
     * @param principal is a [Principal] representing the user making the request
     * @param request is a HttpServletRequest
     *
     * @return a ResponseEntity<String>
     */
    @RequestMapping(value = ["/git-submission/generate-report/{gitSubmissionId}"], method = [(RequestMethod.POST)])
    fun upload(@PathVariable gitSubmissionId: String,
               principal: Principal,
               request: HttpServletRequest): ResponseEntity<String> {

        val gitSubmission = gitSubmissionRepository.findById(gitSubmissionId.toLong()).orElse(null) ?:
        throw IllegalArgumentException("git submission ${gitSubmissionId} is not registered")
        val assignment = assignmentRepository.findById(gitSubmission.assignmentId).orElse(null)

        // TODO: Validate assignment due date

        if (!request.isUserInRole("TEACHER")) {

            if (!assignment.active) {
                throw org.springframework.security.access.AccessDeniedException("Submissions are not open to this assignment")
            }

            assignmentService.checkAssignees(assignment.id, principal.realName())
        }

        if (assignment.cooloffPeriod != null) {
            val lastSubmission = submissionService.getLastSubmission(principal, assignment.id)
            if (lastSubmission != null) {
                val nextSubmissionTime = submissionService.calculateCoolOff(lastSubmission, assignment)
                if (nextSubmissionTime != null) {
                    LOG.warn("[${principal.realName()}] can't submit because he is in cool-off period")
                    throw org.springframework.security.access.AccessDeniedException("[${principal.realName()}] can't submit because he is in cool-off period")
                }
            }
        }

        val projectFolder = File(gitSubmissionsRootLocation, gitSubmission.getFolderRelativeToStorageRoot())

        // verify that there is not another submission with the Submitted status
        val existingSubmissions = submissionRepository
                .findByGroupAndAssignmentIdOrderBySubmissionDateDescStatusDateDesc(gitSubmission.group, assignment.id)
        for (submission in existingSubmissions) {
            if (submission.getStatus() == SubmissionStatus.SUBMITTED) {
                LOG.info("[${gitSubmission.group.authorsNameStr()}] tried to submit before the previous one has been validated")
                return ResponseEntity("{\"error\": \"A submissão anterior ainda não foi validada. Aguarde pela geração do relatório para voltar a submeter.\"}", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        val submission = Submission(gitSubmissionId = gitSubmission.id, submissionDate = Date(),
                status = SubmissionStatus.SUBMITTED.code, statusDate = Date(), assignmentId = assignment.id,
                assignmentGitHash = assignment.gitCurrentHash, submitterUserId = principal.realName())
        submission.group = gitSubmission.group
        submissionRepository.save(submission)

        submissionService.buildSubmission(projectFolder, assignment, gitSubmission.group.authorsStr("|"), submission, asyncExecutor, principal = principal)

        return ResponseEntity("{ \"submissionId\": \"${submission.id}\"}", HttpStatus.OK)

    }

    /**
     * Controller that handles requests for the resetting of the connection between a GitHub and an Assignment.
     *
     * @param gitSubmissionId is a String identifying a [GitSubmission]
     * @param redirectAttributes is a RedirectAttributes
     * @param principal is [Principal] representing the user making the request
     *
     * @return a String with the name of the relevant View
     */
    @RequestMapping(value = ["/student/reset-git/{gitSubmissionId}"], method = [(RequestMethod.POST)])
    fun disconnectAssignmentToGitRepository(@PathVariable gitSubmissionId: String,
                                            redirectAttributes: RedirectAttributes,
                                            principal: Principal): String {

        LOG.info("[${principal.realName()}] Reset git connection")

        val gitSubmission = gitSubmissionRepository.getById(gitSubmissionId.toLong())
        val assignment = assignmentRepository.getById(gitSubmission.assignmentId)
        val repositoryUrl = gitSubmission.gitRepositoryUrl

        if (!principal.realName().equals(gitSubmission.submitterUserId)) {
            redirectAttributes.addFlashAttribute("error", "Apenas o utilizador que fez a ligação (${gitSubmission.submitterUserId}) é que pode remover a ligação")
            return "redirect:/upload/${assignment.id}"
        }

        if (gitSubmission.connected) {
            val submissionFolder = File(gitSubmissionsRootLocation, gitSubmission.getFolderRelativeToStorageRoot())
            if (submissionFolder.exists()) {
                LOG.info("[${principal.realName()}] Removing ${submissionFolder.absolutePath}")
                submissionFolder.deleteRecursively()
            }
        }

        gitSubmissionService.deleteGitSubmission(gitSubmission)
        LOG.info("[${principal.realName()}] Removed submission from the DB")

        redirectAttributes.addFlashAttribute("message", "Desligado com sucesso do repositório ${repositoryUrl}")
        return "redirect:/upload/${assignment.id}"
    }

    /**
     * Controller that handles requests for the deletion of a [Submission].
     *
     * @param submissionId is a Long, identifying the Submission to delete
     * @param principal is a [Principal] representing the user making the request
     * @return a String with the name of the relevant View
     */
    @RequestMapping(value = ["/delete/{submissionId}"], method = [(RequestMethod.POST)])
    fun deleteSubmission(@PathVariable submissionId: Long,
                         principal: Principal) : String {

        val submission = submissionRepository.findById(submissionId).orElse(null)
        val assignment = assignmentRepository.findById(submission.assignmentId).orElse(null)

        val acl = assignmentACLRepository.findByAssignmentId(assignment.id)
        if (principal.realName() != assignment.ownerUserId && acl.find { it -> it.userId == principal.realName() } == null) {
            throw IllegalAccessError("Submissions can only be deleted by the assignment owner or authorized teachers")
        }

        submission.setStatus(SubmissionStatus.DELETED)
        submissionRepository.save(submission)

        LOG.info("[${principal.realName()}] deleted submission $submissionId")

        return "redirect:/report/${assignment.id}"
    }

    @ExceptionHandler(StorageException::class)
    fun handleStorageError(e: StorageException): ResponseEntity<String> {
        LOG.error(e.message)
        return ResponseEntity("{\"error\": \"Falha a gravar ficheiro => ${e.message}\"}", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(InvalidProjectStructureException::class)
    fun handleStorageError(e: InvalidProjectStructureException): ResponseEntity<String> {
        LOG.warn(e.message)
        return ResponseEntity("{\"error\": \"${e.message}\"}", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Inexistent assignment")
    class AssignmentNotFoundException(assignmentId: String) : Exception("Inexistent assignment ${assignmentId}") {
        val LOG = LoggerFactory.getLogger(this.javaClass.name)

        init {
            LOG.warn("Inexistent assignment ${assignmentId}")
        }
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Inexistent submission")
    class SubmissionNotFoundException(submissionId: Long) : Exception("Inexistent submission ${submissionId}") {
        val LOG = LoggerFactory.getLogger(this.javaClass.name)

        init {
            LOG.warn("Inexistent submission ${submissionId}")
        }
    }

}
