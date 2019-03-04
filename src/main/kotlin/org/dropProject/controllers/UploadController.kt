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

import org.apache.commons.io.FileUtils
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
import java.util.logging.Logger
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.EnableAsync
import org.dropProject.services.BuildWorker
import org.apache.any23.encoding.TikaEncodingDetector
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.RefNotAdvertisedException
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.security.access.AccessDeniedException
import org.dropProject.dao.*
import org.dropProject.extensions.existsCaseSensitive
import org.dropProject.extensions.sanitize
import org.dropProject.forms.SubmissionMethod
import org.dropProject.services.AssignmentTeacherFiles
import org.dropProject.repository.*
import org.dropProject.services.GitClient
import org.dropProject.services.GitSubmissionService
import org.dropProject.storage.unzip
import java.io.IOException
import java.io.InputStream
import java.lang.IllegalStateException
import java.nio.charset.Charset
import java.nio.charset.UnsupportedCharsetException
import java.nio.file.Paths
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executor
import java.util.logging.Level
import javax.servlet.http.HttpServletRequest


@Controller
@EnableAsync
class UploadController(
        val storageService: StorageService,
        val buildWorker: BuildWorker,
        val authorRepository: AuthorRepository,
        val projectGroupRepository: ProjectGroupRepository,
        val submissionRepository: SubmissionRepository,
        val gitSubmissionRepository: GitSubmissionRepository,
        val submissionReportRepository: SubmissionReportRepository,
        val assignmentRepository: AssignmentRepository,
        val assignmentACLRepository: AssignmentACLRepository,
        val assigneeRepository: AssigneeRepository,
        val asyncExecutor: Executor,
        val assignmentTeacherFiles: AssignmentTeacherFiles,
        val gitClient: GitClient,
        val gitSubmissionService: GitSubmissionService
        ) {

    @Value("\${storage.rootLocation}/git")
    val gitSubmissionsRootLocation : String = "submissions/git"

    @Value("\${mavenizedProjects.rootLocation}")
    val mavenizedProjectsRootLocation : String = ""

    @Value("\${delete.original.projectFolder:true}")
    val deleteOriginalProjectFolder : Boolean = true

    val LOG = Logger.getLogger(this.javaClass.name)

    init {
        storageService.init()
    }

    @RequestMapping(value = ["/"], method = [(RequestMethod.GET)])
    fun getUploadForm(model: ModelMap, principal: Principal, request: HttpServletRequest): String {

        val assignments = ArrayList<Assignment>()

        val assignees = assigneeRepository.findByAuthorUserId(principal.name)
        for (assignee in assignees) {
            val assignment = assignmentRepository.getOne(assignee.assignmentId)
            if (assignment.active == true) {
                assignments.add(assignment)
            }
        }

        if (request.isUserInRole("TEACHER")) {
            val assignmentsIOwn = assignmentRepository.findByOwnerUserId(principal.name)

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



    @RequestMapping(value = ["/upload/{assignmentId}"], method = [(RequestMethod.GET)])
    fun getUploadForm(model: ModelMap, principal: Principal,
                      @PathVariable("assignmentId") assignmentId: String,
                      request: HttpServletRequest): String {

        val assignment = assignmentRepository.findOne(assignmentId) ?: throw AssignmentNotFoundException(assignmentId)

        if (!request.isUserInRole("TEACHER")) {

            if (!assignment.active) {
                throw org.springframework.security.access.AccessDeniedException("Submissions are not open to this assignment")
            }

            checkAssignees(assignmentId, principal.name)

        } else {

            if (!assignment.active) {
                val acl = assignmentACLRepository.findByAssignmentId(assignmentId)
                if (principal.name != assignment.ownerUserId && acl.find { it -> it.userId == principal.name } == null) {
                    throw IllegalAccessError("Assignments can only be accessed by their owner or authorized teachers")
                }
            }
        }

        model["assignment"] = assignment
        model["numSubmissions"] = submissionRepository.countBySubmitterUserIdAndAssignmentId(principal.name, assignment.id)
        model["instructionsFragment"] = assignmentTeacherFiles.getHtmlInstructionsFragment(assignment)
        model["packageTree"] = assignmentTeacherFiles.buildPackageTree(
                assignment.packageName, assignment.language, assignment.acceptsStudentTests)

        if (assignment.cooloffPeriod != null) {
            val lastSubmission = getLastSubmission(principal, assignmentId)
            if (lastSubmission != null) {
                val nextSubmissionTime = calculateCoolOff(lastSubmission, assignment)
                if (nextSubmissionTime != null) {
                    model["coolOffEnd"] = nextSubmissionTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                    LOG.info("[${principal.name}] can't submit because he is in cool-off period")
                }
            }
        }

        if (assignment.submissionMethod == SubmissionMethod.UPLOAD) {
            model["uploadForm"] = UploadForm(assignment.id)
            return "student-upload-form"
        } else {

            val gitSubmission =
                    gitSubmissionRepository.findBySubmitterUserIdAndAssignmentId(principal.name, assignmentId)
                    ?:
                    gitSubmissionService.findGitSubmissionBy(principal.name, assignmentId) // check if it belongs to a group who has already a git submission

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



    @RequestMapping(value = ["/upload"], method = [(RequestMethod.POST)])
    fun upload(@Valid @ModelAttribute("uploadForm") uploadForm: UploadForm,
               bindingResult: BindingResult,
               @RequestParam("file") file: MultipartFile,
               principal: Principal,
               request: HttpServletRequest): ResponseEntity<String> {

        if (bindingResult.hasErrors()) {
            return ResponseEntity("{\"error\": \"Erro interno\"}", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        if (uploadForm.assignmentId == null) {
            throw IllegalArgumentException("assignmentId is null")
        }

        val assignment = assignmentRepository.findOne(uploadForm.assignmentId) ?:
                throw IllegalArgumentException("assignment ${uploadForm.assignmentId} is not registered")

        if (assignment.submissionMethod != SubmissionMethod.UPLOAD) {
            throw IllegalArgumentException("this assignment doesnt accept upload submissions")
        }

        // TODO: Validate assignment due date

        if (!request.isUserInRole("TEACHER")) {

            if (!assignment.active) {
                throw org.springframework.security.access.AccessDeniedException("Submissions are not open to this assignment")
            }

            checkAssignees(uploadForm.assignmentId!!, principal.name)
        }

        if (assignment.cooloffPeriod != null) {
            val lastSubmission = getLastSubmission(principal, assignment.id)
            if (lastSubmission != null) {
                val nextSubmissionTime = calculateCoolOff(lastSubmission, assignment)
                if (nextSubmissionTime != null) {
                    LOG.warning("[${principal.name}] can't submit because he is in cool-off period")
                    throw org.springframework.security.access.AccessDeniedException("[${principal.name}] can't submit because he is in cool-off period")
                }
            }
        }

        if (!file.originalFilename.endsWith(".zip", ignoreCase = true)) {
            return ResponseEntity("{\"error\": \"O ficheiro tem que ser um .zip\"}", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        LOG.info("[${principal.name}] uploaded ${file.originalFilename}")
        val projectFolder : File? = storageService.store(file)

        if (projectFolder != null) {
            val authors = getProjectAuthors(projectFolder)
            LOG.info("[${authors.joinToString(separator = "|")}] Received ${file.originalFilename}")

            // check if the principal is one of group elements
            if (authors.filter { it.number == principal.name }.isEmpty()) {
                throw InvalidProjectStructureException("O utilizador que está a submeter tem que ser um dos elementos do grupo.")
            }

            val group = getOrCreateProjectGroup(authors)

            // verify that there is not another submission with the Submitted status
            val existingSubmissions = submissionRepository.findByGroupAndAssignmentIdOrderBySubmissionDateDescStatusDateDesc(group, assignment.id)
            for (submission in existingSubmissions) {
                if (submission.getStatus() == SubmissionStatus.SUBMITTED) {
                    LOG.info("[${authors.joinToString(separator = "|")}] tried to submit before the previous one has been validated")
                    return ResponseEntity("{\"error\": \"A submissão anterior ainda não foi validada. Aguarde pela geração do relatório para voltar a submeter.\"}", HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }

            val submission = Submission(submissionId = projectFolder.name, submissionDate = Date(),
                    status = SubmissionStatus.SUBMITTED.code, statusDate = Date(), assignmentId = assignment.id, submitterUserId = principal.name)
            submission.group = group
            submissionRepository.save(submission)

            buildSubmission(projectFolder, assignment, authors.joinToString(separator = "|"), submission, asyncExecutor, principal = principal)

            return ResponseEntity("{ \"submissionId\": \"${submission.id}\"}", HttpStatus.OK);
        }

        return ResponseEntity("{\"error\": \"Não foi possível processar o ficheiro\"}", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private fun getOrCreateProjectGroup(authors: List<AuthorDetails>): ProjectGroup {
        val group = searchExistingProjectGroupOrCreateNew(authors)
        if (group.authors.isEmpty()) { // new group
            projectGroupRepository.save(group)

            for (authorDetails in authors) {
                val authorDB = Author(name = authorDetails.name, userId = authorDetails.number)
                authorDB.group = group
                authorRepository.save(authorDB)
            }
            LOG.info("New group created with students ${authors.joinToString(separator = "|")}")
        } else {
            LOG.info("Group already exists for students ${authors.joinToString(separator = "|")}")
        }
        return group
    }

    private fun buildSubmission(projectFolder: File, assignment: Assignment,
                                authorsStr: String,
                                submission: Submission,
                                asyncExecutor: Executor,
                                teacherRebuild: Boolean = false,
                                principal: Principal?) {
        val projectStructureErrors = checkProjectStructure(projectFolder, assignment)
        if (!projectStructureErrors.isEmpty()) {
            LOG.info("[${authorsStr}] Project Structure NOK")
            submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                    reportKey = Indicator.PROJECT_STRUCTURE.code, reportValue = "NOK"))
            submission.structureErrors = projectStructureErrors.joinToString(separator = ";")
            submission.setStatus(SubmissionStatus.VALIDATED)
            submissionRepository.save(submission)
        } else {
            LOG.info("[${authorsStr}] Project Structure OK")
            submissionReportRepository.save(SubmissionReport(submissionId = submission.id,
                    reportKey = Indicator.PROJECT_STRUCTURE.code, reportValue = "OK"))

            val mavenizedProjectFolder = mavenize(projectFolder, submission, assignment, teacherRebuild)
            LOG.info("[${authorsStr}] Mavenized OK")

            if (asyncExecutor is ThreadPoolTaskScheduler) {
                LOG.info("asyncExecutor.activeCount = ${asyncExecutor.activeCount}")
            }

            if (teacherRebuild) {
                submission.setStatus(SubmissionStatus.REBUILDING, dontUpdateStatusDate = true)
                submissionRepository.save(submission)
            }

            buildWorker.checkProject(mavenizedProjectFolder, authorsStr, submission, rebuildByTeacher = teacherRebuild,
            principalName = principal?.name)
        }
    }


    private fun checkProjectStructure(projectFolder: File, assignment: Assignment): List<String> {
        val erros = ArrayList<String>()
        if (!File(projectFolder, "src").existsCaseSensitive()) {
            erros.add("O projecto não contém uma pasta 'src' na raiz")
        }

        val packageName = assignment.packageName.orEmpty().replace(".","/")

        if (!File(projectFolder, "src/${packageName}").existsCaseSensitive()) {
            erros.add("O projecto não contém uma pasta 'src/${packageName}'")
        }

        val mainFile = if (assignment.language == Language.JAVA) "Main.java" else "Main.kt"
        if (!File(projectFolder, "src/${packageName}/${mainFile}").existsCaseSensitive()) {
            erros.add("O projecto não contém o ficheiro ${mainFile} na pasta 'src/${packageName}'")
        }

        if (File(projectFolder, "src")
                .walkTopDown()
                .find { it.name.startsWith("TestTeacher") } != null) {
            erros.add("O projecto contém ficheiros cujo nome começa por 'TestTeacher'")
        }

        return erros
    }

    private fun searchExistingProjectGroupOrCreateNew(authors: List<AuthorDetails>): ProjectGroup {
        val groups = projectGroupRepository.getGroupsForAuthor(authors.first().number)
        for (group in groups) {
            if (group.authors.size == authors.size &&
                    group.authors.map { it -> it.userId }.containsAll(authors.map { it -> it.number })) {
                // it's the same group
                return group
            }
        }
        return ProjectGroup()
    }

    private fun mavenize(projectFolder: File, submission: Submission, assignment: Assignment, teacherRebuild: Boolean = false): File {
        val mavenizedProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission, teacherRebuild)

        mavenizedProjectFolder.deleteRecursively()

        val folder = if (assignment.language == Language.JAVA) "java" else "kotlin"

        // first copy the project files submitted by the students
        FileUtils.copyDirectory(File(projectFolder, "src"), File(mavenizedProjectFolder, "src/main/${folder}")) {
            it.isDirectory || (it.isFile() && !it.name.startsWith("Test")) // exclude TestXXX classes
        }
        FileUtils.copyDirectory(File(projectFolder, "src"), File(mavenizedProjectFolder, "src/test/${folder}")) {
            it.isDirectory || (it.isFile() && it.name.startsWith("Test")) // include TestXXX classes
        }

        val testFilesFolder = File(projectFolder, "test-files")
        if (testFilesFolder.exists()) {
            FileUtils.copyDirectory(File(projectFolder, "test-files"), File(mavenizedProjectFolder, "test-files"))
        }
        FileUtils.copyFile(File(projectFolder, "AUTHORS.txt"),File(mavenizedProjectFolder, "AUTHORS.txt"))
        if (submission.gitSubmissionId == null && deleteOriginalProjectFolder) {  // don't delete git submissions
            FileUtils.deleteDirectory(projectFolder)  // TODO: This seems duplicate with the lines below...
        }

        // next, copy the project files submitted by the teachers (will override eventually the student files)
        assignmentTeacherFiles.copyTeacherFilesTo(assignment, mavenizedProjectFolder)

        // finally remove the original project folder (the zip file is still kept)
        if (!(assignment.id.startsWith("testJavaProj") ||
                        assignment.id.startsWith("sample") ||
                        assignment.id.startsWith("testKotlinProj") ||  // exclude projects used for automatic tests
                        submission.gitSubmissionId != null)) {   // exclude git submissions
            projectFolder.deleteRecursively()
        }

        return mavenizedProjectFolder
    }

    private fun getProjectAuthors(projectFolder: File) : List<AuthorDetails> {
        // check for AUTHORS.txt file
        val authorsFile = File(projectFolder, "AUTHORS.txt")
        if (!authorsFile.existsCaseSensitive()) {
            throw InvalidProjectStructureException("O projecto não contém o ficheiro AUTHORS.txt na raiz")
        }

        // check the encoding of AUTHORS.txt
        val charset = try { guessCharset(authorsFile.inputStream()) } catch (ie: IOException) { Charset.defaultCharset() }
        if (!charset.equals(Charset.defaultCharset())) {
            LOG.info("AUTHORS.txt is not in the default charset (${Charset.defaultCharset()}): ${charset}")
        }

        // TODO check that AUTHORS.txt includes the number and name of the students
        val authors = ArrayList<AuthorDetails>()
        val authorIDs = HashSet<String>()
        try {
            authorsFile.readLines(charset = charset)
                    .map { line -> line.split(";") }
                    .forEach { parts -> run {
                        if (parts[1][0].isDigit() || parts[1].split(" ").size <= 1) {
                            throw InvalidProjectStructureException("Cada linha tem que ter o formato NUMERO_ALUNO;NOME_ALUNO. " +
                                    "O nome do aluno deve incluir o primeiro e último nome.")
                        }

                        authors.add(AuthorDetails(parts[1], parts[0].sanitize()))
                        authorIDs.add(parts[0])
                    } }

            // check for duplicate authors
            if (authorIDs.size < authors.size) {
                throw InvalidProjectStructureException("O ficheiro AUTHORS.txt não está correcto. " +
                "Contém autores duplicados.")
            }

        } catch (e: Exception) {
            when(e) {
                is InvalidProjectStructureException -> throw  e
                else -> {
                    LOG.log(Level.FINE, "Error parsing AUTHORS.txt", e)
                    authors.clear()
                }
            }
        }

        if (authors.isEmpty()) {
            throw InvalidProjectStructureException("O ficheiro AUTHORS.txt não está correcto. " +
                    "Tem que conter uma linha por aluno, tendo cada linha o número e o nome separados por ';'")
        } else {
            return authors
        }
    }

    @RequestMapping(value = ["/rebuild/{submissionId}"], method = [(RequestMethod.POST)])
    fun rebuild(@PathVariable submissionId: Long,
                principal: Principal) : String {

        LOG.info("Rebuilding ${submissionId}")

        val submission = submissionRepository.findOne(submissionId)
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
                principalName = principal.name)

        return "redirect:/buildReport/${submissionId}";
    }

    // reaplies the assignment files, which may have changed since the submission
    @RequestMapping(value = ["/rebuildFull/{submissionId}"], method = [(RequestMethod.POST)])
    fun rebuildFull(@PathVariable submissionId: Long,
                principal: Principal) : String {

        LOG.info("Rebuilding full ${submissionId}")

        val submission = submissionRepository.findOne(submissionId) ?: throw SubmissionNotFoundException(submissionId)

        val assignment = assignmentRepository.getOne(submission.assignmentId)

        // create another submission that is a clone of this one, to preserve the original submission
        val rebuiltSubmission = Submission(submissionId = submission.submissionId,
                gitSubmissionId = submission.gitSubmissionId,
                submissionDate = submission.submissionDate,
                submitterUserId = submission.submitterUserId,
                assignmentId = submission.assignmentId,
                status = SubmissionStatus.SUBMITTED_FOR_REBUILD.code,
                statusDate = Date())
        rebuiltSubmission.group = submission.group

        val projectFolder =
            if (submission.submissionId != null) {  // submission through upload

                val projectFolder = storageService.retrieveProjectFolder(rebuiltSubmission.submissionId)
                        ?: throw IllegalArgumentException("projectFolder for ${rebuiltSubmission.submissionId} doesn't exist")

                LOG.info("Retrieved project folder: ${projectFolder.absolutePath}")

                if (!projectFolder.exists()) {
                    // let's check if there is a zip file with this project
                    val projectZipFile = File("${projectFolder.absolutePath}.zip")
                    if (projectZipFile.exists()) {
                        unzip(Paths.get(projectZipFile.path), projectFolder.name)
                    }
                }

                projectFolder

            } else if (submission.gitSubmissionId != null) {   // submission through git
                val gitSubmission = gitSubmissionRepository.findOne(submission.gitSubmissionId) ?:
                                        throw SubmissionNotFoundException(submission.gitSubmissionId)

                File(gitSubmissionsRootLocation, gitSubmission.getFolderRelativeToStorageRoot())

            } else {
                throw IllegalStateException("submission ${submission.id} has both submissionId and gitSubmissionId equal to null")
            }

        val authors = getProjectAuthors(projectFolder)

        submissionRepository.save(rebuiltSubmission)
        buildSubmission(projectFolder, assignment, authors.joinToString(separator = "|"), rebuiltSubmission,
                asyncExecutor, teacherRebuild = true, principal = principal)

        return "redirect:/buildReport/${rebuiltSubmission.id}";
    }

    @RequestMapping(value = ["/markAsFinal/{submissionId}"], method = [(RequestMethod.POST)])
    fun markAsFinal(@PathVariable submissionId: Long,
                    @RequestParam(name="redirectToSubmissionsList", required = false, defaultValue = "false")
                    redirectToSubmissionsList: Boolean,
                    principal: Principal) : String {

        val submission = submissionRepository.findOne(submissionId)
        val assignment = assignmentRepository.findOne(submission.assignmentId)

        val acl = assignmentACLRepository.findByAssignmentId(assignment.id)
        if (principal.name != assignment.ownerUserId && acl.find { it -> it.userId == principal.name } == null) {
            throw IllegalAccessError("Submissions can only be marked as final by the assignment owner or authorized teachers")
        }


        if (submission.markedAsFinal) {
            submission.markedAsFinal = false
            LOG.info("Unmarking as final: ${submissionId}")

        } else {

            LOG.info("Marking as final: ${submissionId}")

            // find all other submissions from this group and assignment
            val otherSubmissions = submissionRepository.findByGroupAndAssignmentIdOrderBySubmissionDateDescStatusDateDesc(submission.group, submission.assignmentId)
            for (otherSubmission in otherSubmissions) {
                otherSubmission.markedAsFinal = false
                submissionRepository.save(submission)
            }

            submission.markedAsFinal = true
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

        for (submission in nonFinalSubmissions) {
            val mavenizedProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission,
                    submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT)
            if (mavenizedProjectFolder.deleteRecursively()) {
                LOG.info("Removed mavenized project folder (${submission.submissionId}): ${mavenizedProjectFolder}")
            } else {
                LOG.info("Error removing mavenized project folder (${submission.submissionId}): ${mavenizedProjectFolder}")
            }
        }

        // TODO: Should show a toast saying how many files were deleted
        return "redirect:/report/${assignmentId}";
    }

    @RequestMapping(value = ["/student/setup-git"], method = [(RequestMethod.POST)])
    fun setupStudentSubmissionUsingGitRepository(@RequestParam("assignmentId") assignmentId: String,
                                                 @RequestParam("gitRepositoryUrl") gitRepositoryUrl: String?,
                                                 model: ModelMap, principal: Principal,
                                                 request: HttpServletRequest): String {

        val assignment = assignmentRepository.getOne(assignmentId)

        if (!request.isUserInRole("TEACHER")) {

            if (!assignment.active) {
                throw org.springframework.security.access.AccessDeniedException("Submissions are not open to this assignment")
            }

            checkAssignees(assignmentId, principal.name)
        }

        model["assignment"] = assignment
        model["numSubmissions"] = submissionRepository.countBySubmitterUserIdAndAssignmentId(principal.name, assignment.id)
        model["instructionsFragment"] = assignmentTeacherFiles.getHtmlInstructionsFragment(assignment)
        model["packageTree"] = assignmentTeacherFiles.buildPackageTree(
                assignment.packageName, assignment.language, assignment.acceptsStudentTests)

        if (gitRepositoryUrl.isNullOrBlank()) {
            model["gitRepoErrorMsg"] = "Tens que preencher o endereço do repositório"
            return "student-git-form"
        }

        if (!gitClient.checkValidSSHGithubURL(gitRepositoryUrl)) {
            model["gitRepoErrorMsg"] = "O endereço do repositório não está no formato correcto"
            return "student-git-form"
        }


        var gitSubmission =
                gitSubmissionRepository.findBySubmitterUserIdAndAssignmentId(principal.name, assignmentId)
                ?:
                gitSubmissionService.findGitSubmissionBy(principal.name, assignmentId) // check if it belongs to a group who has already a git submission


        if (gitSubmission == null || gitSubmission.gitRepositoryPubKey == null) {

            // generate key pair
            val (privKey, pubKey) = gitClient.generateKeyPair()

            gitSubmission = GitSubmission(assignmentId = assignmentId,
                    submitterUserId = principal.name, gitRepositoryUrl = gitRepositoryUrl)
            gitSubmission.gitRepositoryPrivKey = String(privKey)
            gitSubmission.gitRepositoryPubKey = String(pubKey)
            gitSubmissionRepository.save(gitSubmission)
        }

        if (gitSubmission.gitRepositoryUrl.orEmpty().contains("github")) {
            val (username, reponame) = gitClient.getGitRepoInfo(gitSubmission.gitRepositoryUrl)
            model["repositorySettingsUrl"] = "https://github.com/${username}/${reponame}/settings/keys"
        }

        model["gitSubmission"] = gitSubmission

        return "student-setup-git"
    }

    @RequestMapping(value = ["/student/setup-git-2/{gitSubmissionId}"], method = [(RequestMethod.POST)])
    fun connectAssignmentToGitRepository(@PathVariable gitSubmissionId: String, redirectAttributes: RedirectAttributes,
                                         model: ModelMap, principal: Principal): String {

        val gitSubmission = gitSubmissionRepository.getOne(gitSubmissionId.toLong())
        val assignment = assignmentRepository.getOne(gitSubmission.assignmentId)

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
                val authors = getProjectAuthors(projectFolder)
                LOG.info("[${authors.joinToString(separator = "|")}] Connected DP to ${gitSubmission.gitRepositoryUrl}")

                // check if the principal is one of group elements
                if (authors.filter { it.number == principal.name }.isEmpty()) {
                    throw InvalidProjectStructureException("O utilizador que está a submeter tem que ser um dos elementos do grupo.")
                }

                val group = getOrCreateProjectGroup(authors)
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
                LOG.info("Error cloning ${gitRepository} - ${e}")
                model["error"] = "Error cloning ${gitRepository} - ${e.message}"
                model["gitSubmission"] = gitSubmission
                return "student-setup-git"
            }
        }

        redirectAttributes.addFlashAttribute("message", "Ligado com sucesso ao repositório git")
        return "redirect:/upload/${assignment.id}"
    }

    @RequestMapping(value = ["/git-submission/refresh-git/{submissionId}"], method = [(RequestMethod.POST)])
    fun refreshAssignmentGitRepository(@PathVariable submissionId: String,
                                       principal: Principal): ResponseEntity<String> {

        // check that it exists
        val gitSubmission = gitSubmissionRepository.getOne(submissionId.toLong())

        if (!gitSubmission.group.contains(principal.name)) {
            throw IllegalAccessError("Submissions can only be refreshed by their owners")
        }

        try {
            LOG.info("Pulling git repository for ${submissionId}")
            val git = gitClient.pull(File(gitSubmissionsRootLocation, gitSubmission.getFolderRelativeToStorageRoot()),
                    gitSubmission.gitRepositoryPrivKey!!.toByteArray())
            val lastCommitInfo = gitClient.getLastCommitInfo(git)

            if (lastCommitInfo?.date != gitSubmission.lastCommitDate) {
                gitSubmission.lastSubmissionId = null
                gitSubmissionRepository.save(gitSubmission)
            }

        } catch (re: RefNotAdvertisedException) {
            LOG.warning("Couldn't pull git repository for ${submissionId}: head is invalid")
            return ResponseEntity("{ \"error\": \"Error pulling from ${gitSubmission.gitRepositoryUrl}. Probably you don't have any commits yet.\"}", HttpStatus.INTERNAL_SERVER_ERROR)
        } catch (e: Exception) {
            LOG.warning("Couldn't pull git repository for ${submissionId}")
            return ResponseEntity("{ \"error\": \"Error pulling from ${gitSubmission.gitRepositoryUrl}\"}", HttpStatus.INTERNAL_SERVER_ERROR)
        }

        return ResponseEntity("{ \"success\": \"true\"}", HttpStatus.OK);
    }

    @RequestMapping(value = ["/git-submission/generate-report/{gitSubmissionId}"], method = [(RequestMethod.POST)])
    fun upload(@PathVariable gitSubmissionId: String,
               principal: Principal,
               request: HttpServletRequest): ResponseEntity<String> {

        val gitSubmission = gitSubmissionRepository.findOne(gitSubmissionId.toLong()) ?:
        throw IllegalArgumentException("git submission ${gitSubmissionId} is not registered")
        val assignment = assignmentRepository.findOne(gitSubmission.assignmentId)

        // TODO: Validate assignment due date

        if (!request.isUserInRole("TEACHER")) {

            if (!assignment.active) {
                throw org.springframework.security.access.AccessDeniedException("Submissions are not open to this assignment")
            }

            checkAssignees(assignment.id, principal.name)
        }

        if (assignment.cooloffPeriod != null) {
            val lastSubmission = getLastSubmission(principal, assignment.id)
            if (lastSubmission != null) {
                val nextSubmissionTime = calculateCoolOff(lastSubmission, assignment)
                if (nextSubmissionTime != null) {
                    LOG.warning("[${principal.name}] can't submit because he is in cool-off period")
                    throw org.springframework.security.access.AccessDeniedException("[${principal.name}] can't submit because he is in cool-off period")
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
                status = SubmissionStatus.SUBMITTED.code, statusDate = Date(), assignmentId = assignment.id, submitterUserId = principal.name)
        submission.group = gitSubmission.group
        submissionRepository.save(submission)

        buildSubmission(projectFolder, assignment, gitSubmission.group.authorsStr("|"), submission, asyncExecutor, principal = principal)

        return ResponseEntity("{ \"submissionId\": \"${submission.id}\"}", HttpStatus.OK);


    }

    @RequestMapping(value = ["/student/reset-git/{gitSubmissionId}"], method = [(RequestMethod.POST)])
    fun disconnectAssignmentToGitRepository(@PathVariable gitSubmissionId: String,
                                            redirectAttributes: RedirectAttributes,
                                            principal: Principal): String {

        LOG.info("[${principal.name}] Reset git connection")

        val gitSubmission = gitSubmissionRepository.getOne(gitSubmissionId.toLong())
        val assignment = assignmentRepository.getOne(gitSubmission.assignmentId)
        val repositoryUrl = gitSubmission.gitRepositoryUrl

        if (!principal.name.equals(gitSubmission.submitterUserId)) {
            redirectAttributes.addFlashAttribute("error", "Apenas o utilizador que fez a ligação (${gitSubmission.submitterUserId}) é que pode remover a ligação")
            return "redirect:/upload/${assignment.id}"
        }

        if (gitSubmission.connected) {
            val submissionFolder = File(gitSubmissionsRootLocation, gitSubmission.getFolderRelativeToStorageRoot())
            if (submissionFolder.exists()) {
                LOG.info("[${principal.name}] Removing ${submissionFolder.absolutePath}")
                submissionFolder.deleteRecursively()
            }
        }

        gitSubmissionService.deleteGitSubmission(gitSubmission)
        LOG.info("[${principal.name}] Removed submission from the DB")

        redirectAttributes.addFlashAttribute("message", "Desligado com sucesso do repositório ${repositoryUrl}")
        return "redirect:/upload/${assignment.id}"
    }

    private fun checkAssignees(assignmentId: String, principalName: String) {
        if (assigneeRepository.existsByAssignmentId(assignmentId)) {
            // if it enters here, it means this assignment has a white list
            // let's check if the current user belongs to the white list
            if (!assigneeRepository.existsByAssignmentIdAndAuthorUserId(assignmentId, principalName)) {
                throw AccessDeniedException("${principalName} is not allowed to view this assignment")
            }
        }
    }

    private fun getLastSubmission(principal: Principal, assignmentId: String) : Submission? {

        val groupsToWhichThisStudentBelongs = projectGroupRepository.getGroupsForAuthor(principal.name)

        // TODO: This is ugly - should rethink data model for groups
        for (group in groupsToWhichThisStudentBelongs) {

            val submissions = submissionRepository
                    .findByGroupAndAssignmentIdOrderBySubmissionDateDescStatusDateDesc(group, assignmentId)

            val lastSubmission =
                    if (submissions.isEmpty()) {
                        null
                    } else {
                        submissions[0]
                    }

            if (lastSubmission != null) {
                return lastSubmission
            }
        }

        return null
    }

    // returns the date when the next submission can be made or null if it's not in cool-off period
    private fun calculateCoolOff(lastSubmission: Submission, assignment: Assignment) : LocalDateTime? {
        val lastSubmissionDate = Timestamp(lastSubmission.submissionDate!!.time).toLocalDateTime()
        val now = LocalDateTime.now()
        val delta = ChronoUnit.MINUTES.between(lastSubmissionDate, now)
        if (delta < assignment.cooloffPeriod!!) {
            return lastSubmissionDate.plusMinutes(assignment.cooloffPeriod!!.toLong())
        }

        return null
    }


    @Throws(IOException::class)
    private fun guessCharset(inputStream: InputStream): Charset {
        try {
            return Charset.forName(TikaEncodingDetector().guessEncoding(inputStream))
        } catch (e: UnsupportedCharsetException) {
            LOG.warning("Unsupported Charset: ${e.charsetName}. Falling back to default")
            return Charset.defaultCharset()
        }
    }

    @ExceptionHandler(StorageException::class)
    fun handleStorageError(e: StorageException): ResponseEntity<String> {
        LOG.severe(e.message)
        return ResponseEntity("{\"error\": \"Falha a gravar ficheiro => ${e.message}\"}", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(InvalidProjectStructureException::class)
    fun handleStorageError(e: InvalidProjectStructureException): ResponseEntity<String> {
        LOG.warning(e.message)
        return ResponseEntity("{\"error\": \"${e.message}\"}", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Inexistent assignment")
    class AssignmentNotFoundException(assignmentId: String) : Exception("Inexistent assignment ${assignmentId}") {

        val LOG = Logger.getLogger(this.javaClass.name)

        init {
            LOG.warning("Inexistent assignment ${assignmentId}")
        }
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Inexistent submission")
    class SubmissionNotFoundException(submissionId: Long) : Exception("Inexistent submission ${submissionId}") {

        val LOG = Logger.getLogger(this.javaClass.name)

        init {
            LOG.warning("Inexistent submission ${submissionId}")
        }
    }

}
    
    
