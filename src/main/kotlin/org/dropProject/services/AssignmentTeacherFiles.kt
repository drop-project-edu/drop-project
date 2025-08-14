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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonView
import org.apache.commons.io.FileUtils
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.AttributeProviderContext
import org.commonmark.renderer.html.AttributeProviderFactory
import org.commonmark.renderer.html.HtmlRenderer
import org.dropProject.dao.*
import org.dropProject.data.JSONViews
import org.dropProject.repository.AssignmentTestMethodRepository
import org.dropProject.repository.BuildReportRepository
import org.dropProject.config.DropProjectProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import java.io.File
import java.security.Principal
import java.util.*

enum class AssignmentInstructionsFormat {
    HTML,
    MD
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AssignmentInstructions(
    @JsonView(JSONViews.StudentAPI::class)
    var format: AssignmentInstructionsFormat? = null,
    @JsonView(JSONViews.StudentAPI::class)
    var body: String? = null
)

/**
 * Provides functionality related with an Assignment's Teacher Files (for example, checking if the Teacher's submission
 * compiles, passes the CheckStyle, and so on).
 */
@Service
class AssignmentTeacherFiles(val buildWorker: BuildWorker,
                             val buildReportRepository: BuildReportRepository,
                             val assignmentTestMethodRepository: AssignmentTestMethodRepository,
                             val applicationContext: ApplicationContext,
                             val i18n: MessageSource,
                             val markdownRenderer: MarkdownRenderer,
                             val dropProjectProperties: DropProjectProperties
) {

    @Value("\${spring.web.locale}")
    val currentLocale : Locale = Locale.getDefault()

    fun getInstructions(assignment: Assignment) : AssignmentInstructions {

        val instructions = AssignmentInstructions()
        val files = File("${dropProjectProperties.assignments.rootLocation}/${assignment.gitRepositoryFolder}").listFiles { _, name -> name.startsWith("instructions")}
        if (files != null && files.isNotEmpty()) {
            val fragment = files.firstOrNull { it.extension.uppercase() == "MD" } ?: files[0]
            val extension = fragment.extension.uppercase()
            instructions.format = AssignmentInstructionsFormat.valueOf(extension)
            if (extension == "MD") {
                instructions.body = markdownRenderer.render(fragment.readText(),
                    "${assignment.id}/",
                    "${assignment.id}/")

                // TODO: While the plugin is not able to render markdown, let's just return html
                instructions.format = AssignmentInstructionsFormat.HTML

            } else {
                instructions.body = fragment.readText()
            }
        }
        return instructions
    }

    fun copyTeacherFilesTo(assignment: Assignment, mavenizedProjectFolder: File) {

        // TODO: should change artifactId in pom.xml with the group-id...

        val rootFolder = File(dropProjectProperties.assignments.rootLocation, assignment.gitRepositoryFolder)
        FileUtils.copyDirectory(rootFolder, mavenizedProjectFolder) {
            !it.absolutePath.startsWith("${rootFolder.absolutePath}/src/main") &&
                    !it.absolutePath.startsWith("${rootFolder.absolutePath}/.git") &&
                    !it.absolutePath.startsWith("${rootFolder.absolutePath}/target")
        }
    }

    /**
     * Builds a String with the expected project structure for an [Assignment], in order to help students configure
     * their projects.
     *
     * @param packageName is a String with the Assignment's expected package name
     * @param language is a Language, identifying the programming language that is used in the [Assignment]
     * @param hasStudentTests is a Boolean indicating if the [Assignment] requires/allows student tests
     *
     * @return a String
     */
    fun buildPackageTree(packageName: String?, language: Language, hasStudentTests: Boolean = false): String {

        val packages = packageName.orEmpty().split(".")
        val mainFile = if (language == Language.JAVA) "Main.java" else "Main.kt"

        var packagesTree = i18n.getMessage("student.upload.form.tree1", null, currentLocale) + System.lineSeparator()
        packagesTree += "+ src" + System.lineSeparator()
        var indent = 3
        for (packagePart in packages) {
            packagesTree += "|" + "-".repeat(indent) + " " + packagePart + System.lineSeparator()
            indent += 3
        }

        packagesTree += "|" + "-".repeat(indent) + " ${mainFile}" + System.lineSeparator()
        packagesTree += "|" + "-".repeat(indent) + " ...   (${i18n.getMessage("student.upload.form.tree2", null, currentLocale)})" + System.lineSeparator()
        if (hasStudentTests) {
            packagesTree += "+ test-files" + System.lineSeparator()
            packagesTree += "|--- somefile1.txt" + System.lineSeparator()
            packagesTree += "|--- ...   (${i18n.getMessage("student.upload.form.tree3", null, currentLocale)})" + System.lineSeparator()
        }

        return packagesTree
    }

    // check that the project files associated with this assignment are valid
    fun checkAssignmentFiles(assignment: Assignment, principal: Principal?): List<AssignmentValidator.Info> {

        val assignmentFolder = File(dropProjectProperties.assignments.rootLocation, assignment.gitRepositoryFolder)

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
            assignmentTestMethodRepository.save(AssignmentTestMethod(assignment = assignment,
                testClass = parts[0], testMethod = parts[1]))
        }

        if (!buildReport.compilationErrors.isEmpty()) {
            report.add(AssignmentValidator.Info(AssignmentValidator.InfoType.ERROR,
                "Assignment has compilation errors."))
            return report
        }

        if (!buildReport.checkstyleErrors.isEmpty()) {
            report.add(AssignmentValidator.Info(AssignmentValidator.InfoType.ERROR,
                "Assignment has checkstyle errors."))
            return report
        }

        if (buildReport.hasJUnitErrors() == true) {
            report.add(AssignmentValidator.Info(AssignmentValidator.InfoType.ERROR,
                "Assignment is failing some JUnit tests. Please fix this!",
                "<pre>${buildReport.junitErrorsTeacher}</pre>"))
            return report
        }

        return report
    }

    fun getProjectFolderAsFile(submission: Submission, wasRebuilt: Boolean) : File {
        val projectFolder =
            if (submission.submissionId != null) submission.submissionId
            else submission.gitSubmissionId!!.toString()

        val suffix = if (wasRebuilt) "-mavenized-for-rebuild" else "-mavenized"

        val destinationPartialFolder = File(dropProjectProperties.mavenizedProjects.rootLocation,
            Submission.relativeUploadFolder(submission.assignmentId, submission.submissionDate))
        destinationPartialFolder.mkdirs()

        return File(destinationPartialFolder, projectFolder + suffix)
    }
}
