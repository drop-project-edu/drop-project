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

import jakarta.persistence.EntityNotFoundException
import org.apache.commons.io.FileUtils
import org.dropProject.Constants
import org.dropProject.controllers.InvalidProjectGroupException
import org.dropProject.controllers.InvalidProjectStructureException
import org.dropProject.controllers.UploadController
import org.dropProject.dao.*
import org.dropProject.data.AuthorDetails
import org.dropProject.data.SubmissionInfo
import org.dropProject.data.SubmissionResult
import org.dropProject.data.TestType
import org.dropProject.extensions.existsCaseSensitive
import org.dropProject.extensions.realName
import org.dropProject.extensions.sanitize
import org.dropProject.forms.SubmissionMethod
import org.dropProject.forms.UploadForm
import org.dropProject.repository.*
import org.dropProject.storage.StorageService
import org.mozilla.universalchardet.UniversalDetector
import org.slf4j.LoggerFactory
import org.dropProject.config.DropProjectProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.MessageSource
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.validation.BindingResult
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.UnsupportedCharsetException
import java.nio.file.Paths
import java.security.Principal
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.Executor
import jakarta.servlet.http.HttpServletRequest


/**
 * Contains functionality related with Submissions (for example, get submissions from the database).
 */
@Service
@EnableAsync
class SubmissionService(
    val submissionRepository: SubmissionRepository,
    val gitSubmissionRepository: GitSubmissionRepository,
    val submissionReportRepository: SubmissionReportRepository,
    val assignmentTeacherFiles: AssignmentTeacherFiles,
    val buildReportBuilder: BuildReportBuilder,
    val storageService: StorageService,
    val projectGroupRepository: ProjectGroupRepository,
    val projectGroupService: ProjectGroupService,
    val i18n: MessageSource,
    val buildWorker: BuildWorker,
    val asyncExecutor: Executor,
    val zipService: ZipService,
    val assignmentRepository: AssignmentRepository,
    val dropProjectProperties: DropProjectProperties
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    @Value("\${delete.original.projectFolder:true}")
    val deleteOriginalProjectFolder : Boolean = true

    @Value("\${spring.web.locale}")
    val currentLocale : Locale = Locale.getDefault()

    /**
     * Returns all the SubmissionInfo objects related with [assignment].
     * @param assignment is the target Assignment
     * @param retrieveReport if true, retrieves the build report associated with each submission
     * @return an ArrayList with SubmissionInfo objects
     */
    fun getSubmissionsList(assignment: Assignment, retrieveReport: Boolean = true): ArrayList<SubmissionInfo> {

        val submissions = submissionRepository
            .findByAssignmentId(assignment.id)
            .filter { it.getStatus() != SubmissionStatus.DELETED }

        val submissionsByGroup = submissions.groupBy { it.group }

        val submissionInfoList = ArrayList<SubmissionInfo>()
        for ((group, submissionList) in submissionsByGroup) {
            val sortedSubmissionList =
                submissionList.sortedWith(compareByDescending<Submission> { it.submissionDate }
                    .thenByDescending { it.statusDate })

            // check if some submission has been marked as final. in that case, that goes into the start of the list
            var lastSubmission = sortedSubmissionList[0]
            for (submission in sortedSubmissionList) {
                if (submission.markedAsFinal) {
                    lastSubmission = submission
                    break
                }
            }

            if (retrieveReport) {
                lastSubmission.buildReport?.let {
                        buildReportDB ->
                    val reportElements = submissionReportRepository.findBySubmissionId(lastSubmission.id)
                    lastSubmission.reportElements = reportElements

                    val mavenizedProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(lastSubmission,
                        lastSubmission.getStatus() == SubmissionStatus.VALIDATED_REBUILT)
                    val buildReport = buildReportBuilder.build(buildReportDB.buildReport.split("\n"),
                        mavenizedProjectFolder.absolutePath, assignment, lastSubmission)
                    lastSubmission.ellapsed = buildReport.elapsedTimeJUnit()
                    lastSubmission.teacherTests = buildReport.junitSummaryAsObject(TestType.TEACHER)
                    lastSubmission.hiddenTests = buildReport.junitSummaryAsObject(TestType.HIDDEN)
                    if (buildReport.jacocoResults.isNotEmpty()) {
                        lastSubmission.coverage = buildReport.jacocoResults[0].lineCoveragePercent
                    }

                    lastSubmission.testResults = buildReport.testResults()
                }
            }

            submissionInfoList.add(SubmissionInfo(group, lastSubmission, sortedSubmissionList))
        }

        return submissionInfoList
    }

    /**
     * Marks a [Submission] as final, and all other submissions for the same group and assignment as not final
     * @param submission is the Submission to mark as final
     */
    fun markAsFinal(submission: Submission) {
        val otherSubmissions = submissionRepository.findByGroupAndAssignmentIdOrderBySubmissionDateDescStatusDateDesc(submission.group, submission.assignmentId)
        for (otherSubmission in otherSubmissions) {
            otherSubmission.markedAsFinal = false
            submissionRepository.save(otherSubmission)
        }

        submission.markedAsFinal = true
    }

    /**
     * Handles all the student submission process through upload
     */
    fun uploadSubmission(
        bindingResult: BindingResult,
        uploadForm: UploadForm,
        request: HttpServletRequest,
        principal: Principal,
        file: MultipartFile,
        assignmentRepository: AssignmentRepository,  // have to pass this repository to avoid circular references
        assignmentService: AssignmentService,  // have to pass this repository to avoid circular references
        submissionMode: SubmissionMode
    ): ResponseEntity<SubmissionResult> {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.internalServerError().body(SubmissionResult(error="Internal error"))
        }

        if (uploadForm.assignmentId == null) {
            throw IllegalArgumentException("assignmentId is null")
        }

        val assignmentId =
            uploadForm.assignmentId ?: throw IllegalArgumentException("Upload form is missing the assignmentId")

        val assignment = assignmentRepository.findById(assignmentId).orElse(null)
            ?: throw IllegalArgumentException("assignment ${assignmentId} is not registered")

        if (assignment.submissionMethod != SubmissionMethod.UPLOAD) {
            throw IllegalArgumentException("this assignment doesnt accept upload submissions")
        }

        // TODO: Validate assignment due date

        if (!request.isUserInRole("TEACHER")) {

            if (!assignment.active) {
                throw AccessDeniedException("Submissions are not open to this assignment")
            }

            assignmentService.checkAssignees(uploadForm.assignmentId!!, principal.realName())
        }

        if (assignment.cooloffPeriod != null && !request.isUserInRole("TEACHER")) {
            val lastSubmission = getLastSubmission(principal, assignment.id)
            if (lastSubmission != null) {
                val nextSubmissionTime = calculateCoolOff(lastSubmission, assignment)
                if (nextSubmissionTime != null) {
                    val tRemaining = nextSubmissionTime.format(
                        DateTimeFormatter.ofPattern("HH:mm"))
                    LOG.warn("[${principal.realName()}] can't submit because he is in cool-off period")
                    throw AccessDeniedException("[${principal.realName()}] can't submit because he is in cool-off period, submit again at $tRemaining")
                }
            }
        }

        val originalFilename = file.originalFilename ?: throw IllegalArgumentException("Missing originalFilename")

        if (!originalFilename.endsWith(".zip", ignoreCase = true)) {
            return ResponseEntity.internalServerError().body(SubmissionResult(error="O ficheiro tem que ser um .zip")) // TODO language
        }

        LOG.info("[${principal.realName()}] uploaded ${originalFilename}")
        val projectFolder: File? = storageService.store(file, assignment.id)

        if (projectFolder != null) {
            val authors = getProjectAuthors(File(projectFolder, "AUTHORS.txt"))
            LOG.info("[${authors.joinToString(separator = "|")}] Received ${originalFilename}")

            // check if the principal is one of group elements
            if (authors.filter { it.number == principal.realName() }.isEmpty()) {
                throw InvalidProjectStructureException(i18n.getMessage("student.submit.notAGroupElement", null, currentLocale)
                )
            }

            // check if the group complies with possible group restrictions
            if (assignment.projectGroupRestrictions != null) {
                val restrictions = assignment.projectGroupRestrictions!!
                if (authors.size !in restrictions.minGroupSize .. (restrictions.maxGroupSize ?: 50) &&
                    !restrictions.exceptionsAsList().contains(principal.realName())) {
                    throw InvalidProjectGroupException(i18n.getMessage("student.submit.invalidGroup",
                        arrayOf(restrictions.minGroupSize, restrictions.maxGroupSize ?: 50), currentLocale))
                }
            }

            val group = projectGroupService.getOrCreateProjectGroup(authors)

            // verify that there is not another submission with the Submitted status
            val existingSubmissions = submissionRepository
                .findByGroupAndAssignmentIdOrderBySubmissionDateDescStatusDateDesc(group, assignment.id)
                .filter { it.getStatus() != SubmissionStatus.DELETED }
            for (submission in existingSubmissions) {
                if (submission.getStatus() == SubmissionStatus.SUBMITTED) {
                    LOG.info("[${authors.joinToString(separator = "|")}] tried to submit before the previous one has been validated")
                    return ResponseEntity.internalServerError()
                        .body(SubmissionResult(error=i18n.getMessage("student.submit.pending", null, currentLocale)))
                }
            }

            val submission = Submission(
                submissionId = projectFolder.name, submissionDate = Date(),
                status = SubmissionStatus.SUBMITTED.code, statusDate = Date(), assignmentId = assignment.id,
                assignmentGitHash = assignment.gitCurrentHash, submitterUserId = principal.realName(),
                submissionFolder = projectFolder.relativeTo(storageService.rootFolder()).path,
                submissionMode = submissionMode
            )
            submission.group = group
            saveSubmissionAndUpdateAssignmentMetrics(submission)

            buildSubmission(
                projectFolder,
                assignment,
                authors.joinToString(separator = "|"),
                submission,
                asyncExecutor,
                principal = principal
            )

            return ResponseEntity.ok(SubmissionResult(submissionId = submission.id))
        }

        return ResponseEntity.internalServerError().body(SubmissionResult(error=i18n.getMessage("student.submit.fileError", null, currentLocale)))

    }

    /**
     * Searches for the last [Submission] performed in an [Assignment] by a certain user or by a member of the respective
     * [ProjectGroup].
     *
     * @param principal is a [Principal] representing the user whose last submission is being searched
     * @param assignmentId is a String representing the relevant Assignment
     *
     * @return a Submission
     */
    fun getLastSubmission(principal: Principal, assignmentId: String): Submission? {
        val groupsToWhichThisStudentBelongs = projectGroupRepository.getGroupsForAuthor(principal.realName())
        var lastSubmission: Submission? = null
        // TODO: This is ugly - should rethink data model for groups
        for (group in groupsToWhichThisStudentBelongs) {
            val lastSubmissionForThisGroup = submissionRepository
                .findFirstByGroupAndAssignmentIdOrderBySubmissionDateDescStatusDateDesc(group, assignmentId)
            if (lastSubmission == null ||
                (lastSubmissionForThisGroup != null &&
                        lastSubmission.submissionDate.before(lastSubmissionForThisGroup.submissionDate))) {
                lastSubmission = lastSubmissionForThisGroup
            }
        }
        return lastSubmission
    }

    // returns the date when the next submission can be made or null if it's not in cool-off period
    fun calculateCoolOff(lastSubmission: Submission, assignment: Assignment) : LocalDateTime? {
        val lastSubmissionDate = Timestamp(lastSubmission.submissionDate.time).toLocalDateTime()
        val now = LocalDateTime.now()
        val delta = ChronoUnit.MINUTES.between(lastSubmissionDate, now)

        val reportElements = submissionReportRepository.findBySubmissionId(lastSubmission.id)
        val cooloffPeriod =
            if (reportElements.any {
                    (it.reportValue == "NOK" &&
                            (it.reportKey == Indicator.PROJECT_STRUCTURE.code ||
                                    it.reportKey == Indicator.COMPILATION.code)) } ) {
                Math.min(Constants.COOLOFF_FOR_STRUCTURE_OR_COMPILATION, assignment.cooloffPeriod!!)
            } else {
                assignment.cooloffPeriod!!
            }

        if (delta < cooloffPeriod) {
            return lastSubmissionDate.plusMinutes(cooloffPeriod.toLong())
        }

        return null
    }

    fun getProjectAuthors(authorsFile: File) : List<AuthorDetails> {

        // check for AUTHORS.txt file
        if (!authorsFile.existsCaseSensitive()) {
            throw InvalidProjectStructureException("O projecto não contém o ficheiro AUTHORS.txt na raiz")  // TODO language
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
                .filter { line -> line.isNotBlank() }
                .map { line -> line.split(";") }
                .forEach { parts -> run {

                    if (parts[0].isBlank()) {
                        throw InvalidProjectStructureException("O número de aluno tem que estar preenchido para todos os elementos do grupo")
                    }

                    if (parts[1][0].isDigit() || parts[1].split(" ").size <= 1) {
                        throw InvalidProjectStructureException("Cada linha tem que ter o formato NUMERO_ALUNO;NOME_ALUNO. " +
                                "O nome do aluno deve incluir o primeiro e último nome.")  // TODO language
                    }

                    authors.add(AuthorDetails(parts[1].trim(), parts[0].sanitize()))
                    authorIDs.add(parts[0])
                } }

            // check for duplicate authors
            if (authorIDs.size < authors.size) {
                throw InvalidProjectStructureException("O ficheiro AUTHORS.txt não está correcto. " +
                        "Contém autores duplicados.")  // TODO language
            }

        } catch (e: Exception) {
            when(e) {
                is InvalidProjectStructureException -> throw  e
                else -> {
                    LOG.debug("Error parsing AUTHORS.txt", e)
                    authors.clear()
                }
            }
        }

        if (authors.isEmpty()) {
            throw InvalidProjectStructureException("O ficheiro AUTHORS.txt não está correcto. " +
                    "Tem que conter uma linha por aluno, tendo cada linha o número e o nome separados por ';'")  // TODO language
        } else {
            return authors
        }
    }

    @Throws(IOException::class)
    private fun guessCharset(inputStream: InputStream): Charset {

        val buf = ByteArray(4096)
        val detector = UniversalDetector(null)

        var nread: Int
        while ((inputStream.read(buf).also { nread = it }) > 0 && !detector.isDone) {
            detector.handleData(buf, 0, nread)
        }
        detector.dataEnd()

        if (detector.detectedCharset != null) {
            try {
                return Charset.forName(detector.detectedCharset)
            } catch (e: UnsupportedCharsetException) {
                LOG.warn("Unsupported Charset: ${e.charsetName}. Falling back to default")
                return Charset.defaultCharset()
            }
        }

        return Charset.defaultCharset()
    }

    /**
     * Builds and tests a [Submission].
     *
     * @property projectFolder is a File
     * @property assignment is the [Assignment] for which the submission is being made
     * @property authorsStr is a String
     * @property submission is a Submission
     * @property asyncExecutor is an Executor
     * @property teacherRebuid is a Boolean, indicating if this "build" is being requested by a teacher
     * @property principal is a [Principal] representing the user making the request
     */
    fun buildSubmission(projectFolder: File, assignment: Assignment,
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
            LOG.info("[${authorsStr}] Mavenized to folder ${mavenizedProjectFolder}")

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
            erros.add("O projecto não contém uma pasta 'src' na raiz") // TODO language
        }

        val packageName = assignment.packageName.orEmpty().replace(".","/")

        if (!File(projectFolder, "src/${packageName}").existsCaseSensitive()) {
            erros.add("O projecto não contém uma pasta 'src/${packageName}'") // TODO language
        }

        val mainFile = if (assignment.language == Language.JAVA) "Main.java" else "Main.kt"
        if (!File(projectFolder, "src/${packageName}/${mainFile}").existsCaseSensitive()) {
            erros.add("O projecto não contém o ficheiro ${mainFile} na pasta 'src/${packageName}'") // TODO language
        }

        if (File(projectFolder, "src")
                .walkTopDown()
                .find { it.name.startsWith("TestTeacher") } != null) {
            erros.add("O projecto contém ficheiros cujo nome começa por 'TestTeacher'") // TODO language
        }

        val readme = File(projectFolder, "README.md")
        if (readme.exists() && !readme.isFile) {
            erros.add("O projecto contém uma pasta README.md mas devia ser um ficheiro") // TODO language
        }

        if (File(projectFolder, "src")
                .walkTopDown()
                .filter { it.extension in listOf("java", "kt") }
                .filter { !it.name.startsWith("Test") }
                .any { containsSearchString(it, "org.junit") }) {
            erros.add("As classes de teste devem começar com a palavra Test (exemplo: TestCar)") // TODO language
        }

        return erros
    }

    /**
     * Transforms a student's submission/code from its original structure to a structure that respects Maven's
     * expected format.
     * @param projectFolder is a file
     * @param submission is a Submission
     * @param teacherRebuild is a Boolean
     * @return File
     */
    private fun mavenize(projectFolder: File, submission: Submission, assignment: Assignment, teacherRebuild: Boolean = false): File {
        val mavenizedProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission, teacherRebuild)

        mavenizedProjectFolder.deleteRecursively()

        val folder = if (assignment.language == Language.JAVA) "java" else "kotlin"

        // first copy the project files submitted by the students
        FileUtils.copyDirectory(File(projectFolder, "src"), File(mavenizedProjectFolder, "src/main/${folder}")) {
            it.isDirectory || (it.isFile() && !it.name.startsWith("Test")) // exclude TestXXX classes
        }
        if (assignment.acceptsStudentTests) {
            FileUtils.copyDirectory(File(projectFolder, "src"), File(mavenizedProjectFolder, "src/test/${folder}")) {
                it.isDirectory || (it.isFile() && it.name.startsWith("Test")) // include TestXXX classes
            }
        }

        val testFilesFolder = File(projectFolder, "test-files")
        if (testFilesFolder.exists()) {
            FileUtils.copyDirectory(File(projectFolder, "test-files"), File(mavenizedProjectFolder, "test-files"))
        }
        FileUtils.copyFile(File(projectFolder, "AUTHORS.txt"),File(mavenizedProjectFolder, "AUTHORS.txt"))

        // next, copy the project files submitted by the teachers (will override eventually the student files)
        assignmentTeacherFiles.copyTeacherFilesTo(assignment, mavenizedProjectFolder)

        // if the students have a README file (either .md or .txt), copy it over the teacher's README
        if (File(projectFolder, "README.md").exists()) {
            FileUtils.copyFile(File(projectFolder, "README.md"), File(mavenizedProjectFolder, "README.md"))
        } else if (File(projectFolder, "README.txt").exists()) {
            FileUtils.copyFile(File(projectFolder, "README.txt"), File(mavenizedProjectFolder, "README.txt"))
        }

        // if the students have images in the root folder, copy them as well
        projectFolder.
        listFiles { file -> file.extension in listOf("png", "jpg", "jpeg", "gif") }
            ?.forEach { FileUtils.copyFile(it, File(mavenizedProjectFolder, it.name))
            }

        if (submission.gitSubmissionId == null && deleteOriginalProjectFolder) {  // don't delete git submissions
            FileUtils.deleteDirectory(projectFolder)  // TODO: This seems duplicate with the lines below...
        }

        // finally remove the original project folder (the zip file is still kept)
        if (!(assignment.id.startsWith("testJavaProj") ||
                    assignment.id.startsWith("sample") ||
                    assignment.id.startsWith("testKotlinProj") ||  // exclude projects used for automatic tests
                    submission.gitSubmissionId != null)) {   // exclude git submissions
            projectFolder.deleteRecursively()
        }

        return mavenizedProjectFolder
    }

    fun deleteMavenizedFolderFor(submissions: List<Submission>) {
        for (submission in submissions) {
            val mavenizedProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission,
                submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT)
            if (mavenizedProjectFolder.deleteRecursively()) {
                LOG.info("Removed mavenized project folder (${submission.submissionId}): ${mavenizedProjectFolder}")
            } else {
                LOG.info("Error removing mavenized project folder (${submission.submissionId}): ${mavenizedProjectFolder}")
            }
        }
    }

    fun getOriginalProjectFolder(submission: Submission): File {
        val projectFolder =
            if (submission.submissionId != null) {  // submission through upload

                val projectFolder = storageService.retrieveProjectFolder(submission)
                    ?: throw IllegalArgumentException("projectFolder for ${submission.submissionId} doesn't exist")

                LOG.info("Retrieved project folder: ${projectFolder.absolutePath}")

                if (!projectFolder.exists()) {
                    // let's check if there is a zip file with this project
                    val projectZipFile = File("${projectFolder.absolutePath}.zip")
                    if (projectZipFile.exists()) {
                        zipService.unzip(Paths.get(projectZipFile.path), projectFolder.name)
                    }
                }

                projectFolder

            } else if (submission.gitSubmissionId != null) {   // submission through git
                val gitSubmissionId = submission.gitSubmissionId ?: throw RuntimeException("Not possible")
                val gitSubmission = gitSubmissionRepository.findById(gitSubmissionId).orElse(null) ?:
                throw UploadController.SubmissionNotFoundException(submission.gitSubmissionId!!)

                File(dropProjectProperties.storage.gitLocation, gitSubmission.getFolderRelativeToStorageRoot())

            } else {
                throw IllegalStateException("submission ${submission.id} has both submissionId and gitSubmissionId equal to null")
            }
        return projectFolder
    }

    fun fillIndicatorsFor(submissions: List<Submission>) {
        for (submission in submissions) {
            val assignment = assignmentRepository.findById(submission.assignmentId)
                .orElseThrow { EntityNotFoundException("Assignment ${submission.assignmentId} not found") }
            submission.reportElements = submissionReportRepository.findBySubmissionId(submission.id)
            submission.overdue = assignment.overdue(submission)
            submission.buildReport?.let {
                    buildReportDB ->
                val mavenizedProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission,
                    submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT)
                val buildReport = buildReportBuilder.build(buildReportDB.buildReport.split("\n"),
                    mavenizedProjectFolder.absolutePath, assignment, submission)
                submission.ellapsed = buildReport.elapsedTimeJUnit()
                submission.teacherTests = buildReport.junitSummaryAsObject()
            }
        }
    }

    // fast way of checking if a file contains a string without having to read the whole file into memory
    private fun containsSearchString(file: File, searchString: String): Boolean {
        return file.useLines { lines ->
            lines.any { it.contains(searchString) }
        }
    }

    /**
     * This method should be used instead of submissionRepository.save(submission) because it not only
     * saves the submission but also updates metrics in the assignment
     */
    fun saveSubmissionAndUpdateAssignmentMetrics(submission: Submission): Submission {

        val isNewSubmission = submission.id == 0L

        val savedSubmission = submissionRepository.save(submission)

        // only update assignment metrics if it is a new submission of if it was deleted
        if (isNewSubmission || submission.getStatus() != SubmissionStatus.DELETED) {
            val assignment = assignmentRepository.getReferenceById(submission.assignmentId)

            if (submission.getStatus() != SubmissionStatus.DELETED) {
                assignment.numSubmissions++
            } else {
                assignment.numSubmissions--
            }
            assignment.lastSubmissionDate = savedSubmission.submissionDate
            assignment.numUniqueSubmitters = submissionRepository.findUniqueSubmittersByAssignmentId(assignment.id).toInt()
            assignmentRepository.save(assignment)
        }

        return savedSubmission
    }
}
