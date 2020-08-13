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

import org.apache.commons.io.FileUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.dropProject.Constants
import org.dropProject.dao.*
import org.dropProject.repository.AssignmentTestMethodRepository
import org.dropProject.repository.BuildReportRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.security.Principal
import java.util.*

@Service
class AssignmentTeacherFiles(val buildWorker: BuildWorker,
                             val buildReportRepository: BuildReportRepository,
                             val assignmentTestMethodRepository: AssignmentTestMethodRepository,
                             val applicationContext: ApplicationContext) {

    @Value("\${assignments.rootLocation}")
    val assignmentsRootLocation : String = ""

    @Value("\${mavenizedProjects.rootLocation}")
    val mavenizedProjectsRootLocation : String = ""

    fun getHtmlInstructionsFragment(assignment: Assignment) : String {
        try {
            val fragment = File(assignmentsRootLocation, "${assignment.gitRepositoryFolder}/instructions.html").readText()
            return fragment
        } catch (e: FileNotFoundException) {
            return ""
        }
    }

    fun copyTeacherFilesTo(assignment: Assignment, mavenizedProjectFolder: File) {

        // TODO: should change artifactId in pom.xml with the group-id...

        val rootFolder = File(assignmentsRootLocation, assignment.gitRepositoryFolder)
        FileUtils.copyDirectory(rootFolder, mavenizedProjectFolder) {
            !it.absolutePath.startsWith("${rootFolder.absolutePath}/src/main") &&
                    !it.absolutePath.startsWith("${rootFolder.absolutePath}/.git") &&
                    !it.absolutePath.startsWith("${rootFolder.absolutePath}/target")
        }
    }

    fun buildPackageTree(packageName: String?, language: Language, hasStudentTests: Boolean = false): String {

        val packages = packageName.orEmpty().split(".")
        val mainFile = if (language == Language.JAVA) "Main.java" else "Main.kt"

        var packagesTree = "AUTHORS.txt  (contém linhas NUMERO_ALUNO;NOME_ALUNO, uma por aluno do grupo)" + System.lineSeparator()
        packagesTree += "+ src" + System.lineSeparator()
        var indent = 3
        for (packagePart in packages) {
            packagesTree += "|" + "-".repeat(indent) + " " + packagePart + System.lineSeparator()
            indent += 3
        }

        packagesTree += "|" + "-".repeat(indent) + " ${mainFile}" + System.lineSeparator()
        packagesTree += "|" + "-".repeat(indent) + " ...   (outros ficheiros com código do projecto)" + System.lineSeparator()
        if (hasStudentTests) {
            packagesTree += "+ test-files" + System.lineSeparator()
            packagesTree += "|--- somefile1.txt" + System.lineSeparator()
            packagesTree += "|--- ...   (outros ficheiros de input para os testes unitários)" + System.lineSeparator()
        }

        return packagesTree
    }

    // check that the project files associated with this assignment are valid
    fun checkAssignmentFiles(assignment: Assignment, principal: Principal?): List<AssignmentValidator.Info> {

        val assignmentFolder = File(assignmentsRootLocation, assignment.gitRepositoryFolder)

        val assignmentValidator = applicationContext.getBean(AssignmentValidator::class.java)

        assignmentValidator.validate(assignmentFolder, assignment)
        val report = assignmentValidator.report

        // it it has found errors, it doesn't even try to run the build
        if (report.any { it.type == AssignmentValidator.InfoType.ERROR }) {
            return report
        }

        // run mvn clean test on the assignment
        val buildReport = buildWorker.checkAssignment(assignmentFolder, assignment, principal?.name)
        if (buildReport == null) {
            report.add(AssignmentValidator.Info(AssignmentValidator.InfoType.ERROR,
                    "Assignment checking (run tests) was aborted by timeout! Why is it taking so long to run?"))
            return report
        }

        val buildReportDB = buildReportRepository.save(BuildReport(buildReport = buildReport.mavenOutput()))
        assignment.buildReportId = buildReportDB.id

        // let's update the test methods associated with this assignment
        // but first clear current test methods
        assignmentTestMethodRepository.deleteAllByAssignmentId(assignment.id)
        for (testMethod in assignmentValidator.testMethods) {
            val parts = testMethod.split(":")
            assignmentTestMethodRepository.save(AssignmentTestMethod(assignmentId = assignment.id,
                                                                     testClass = parts[0], testMethod = parts[1]))
        }

        if (!buildReport.compilationErrors().isEmpty()) {
            report.add(AssignmentValidator.Info(AssignmentValidator.InfoType.ERROR,
                                        "Assignment has compilation errors."))
            return report
        }

        if (!buildReport.checkstyleErrors().isEmpty()) {
            report.add(AssignmentValidator.Info(AssignmentValidator.InfoType.ERROR,
             "Assignment has checkstyle errors."))
            return report
        }

        if (buildReport.hasJUnitErrors() == true) {
            report.add(AssignmentValidator.Info(AssignmentValidator.InfoType.ERROR,
             "Assignment is failing some JUnit tests. Please fix this!",
                    "<pre>${buildReport.jUnitErrors()}</pre>"))
            return report
        }

        return report
    }

    fun getProjectFolderAsFile(submission: Submission, wasRebuilt: Boolean) : File {

        val projectFolder =
                if (submission.submissionId != null) submission.submissionId
                else submission.gitSubmissionId!!.toString()

        val suffix = if (wasRebuilt) "-mavenized-for-rebuild" else "-mavenized"

        val destinationPartialFolder = File(mavenizedProjectsRootLocation,
                Submission.relativeUploadFolder(submission.assignmentId, Date()))
        destinationPartialFolder.mkdirs()

        return File(destinationPartialFolder, projectFolder + suffix)
    }
}
