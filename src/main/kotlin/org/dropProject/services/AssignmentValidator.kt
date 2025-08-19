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
package org.dropproject.services

import com.thoughtworks.qdox.JavaProjectBuilder
import com.thoughtworks.qdox.model.impl.DefaultJavaMethod
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.dropproject.Constants
import org.dropproject.dao.Assignment
import org.dropproject.dao.Language
import org.dropproject.dao.TestVisibility
import org.dropproject.extensions.toEscapedHtml
import org.jetbrains.kotlin.org.jline.utils.Log
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileReader
import java.nio.file.Paths

/**
 * This class performs validation of the assignments created by teachers, in order to make sure that they have the
 * correct formats and include the expected plugins.
 *
 * @property report is a List of [Info], containing warnings about the problems that were identified during the validation
 * @property testMethods is a List of String, containing the names of the JUnit test methods that were found in the assignment's
 * test classes. Each String will contain the name of a test method, prefixed by the name of the class where it was declared.
 */
@Service
@Scope("prototype")
class AssignmentValidator {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    enum class InfoType { INFO, WARNING, ERROR }

    /**
     * Represents an assignment validation message that is used to report problems with the [Assignment] being validated.
     */
    data class Info(val type: InfoType, val message: String, val description: String = "")

    val report = mutableListOf<Info>()
    val testMethods = mutableListOf<String>()

    /**
     * Validates the [Assignment].
     *
     * @param assignmentFolder is a File, representing the file system folder where the assignment's code is stored
     * @param assignment is the Assignment to validate
     */
    fun validate(assignmentFolder: File, assignment: Assignment) {

        val pomFile = File(assignmentFolder, "pom.xml")
        if (!pomFile.exists()) {
            report.add(Info(InfoType.ERROR, "Assignment must have a pom.xml.",
                    "Check <a href=\"https://github.com/palves-ulht/sampleJavaAssignment\">" +
                            "https://github.com/palves-ulht/sampleJavaAssignment</a> for an example"))
            return
        } else {
            report.add(Info(InfoType.INFO, "Assignment has a pom.xml"))
        }

        val reader = MavenXpp3Reader()
        val model = reader.read(FileReader(pomFile))

        if (assignment.packageName == null) {
            report.add(Info(InfoType.WARNING, "Assignment without package.",
                "If you don't set a package for the assignment, Drop Project won't be able to filter stacktraces that are shown to the students."));
        }

        validateCurrentUserIdSystemVariable(assignmentFolder, model)
        validateUntrimmedStacktrace(model)
        try {
            validateProperTestClasses(assignmentFolder, assignment)
        } catch (e: Exception) {
            // since this validaton is fragile (depends on 3rd party libs to parse the source files), just catch eventual exceptions and move on
            LOG.warn("Non-critical error while validating test classes. Moving on...", e)
        }
        if (assignment.maxMemoryMb != null) {
            validatePomPreparedForMaxMemory(model)
        }
        validatePomPreparedForCoverage(model, assignment)
        validateCheckstyleVersion(model)
    }

    // tests that the assignment is ready to use the system property "dropProject.currentUserId"
    private fun validateCurrentUserIdSystemVariable(assignmentFolder: File, pomModel: Model) {

        // first check if the assignment code is referencing this property
        if (searchAllSourceFilesWithinFolder(assignmentFolder, "System.getProperty(\"dropProject.currentUserId\")")) {
            val surefirePlugin = pomModel.build.plugins.find { it.artifactId == "maven-surefire-plugin" }
            if (surefirePlugin == null ||
                    surefirePlugin.configuration == null ||
                    !surefirePlugin.configuration.toString().contains("<argLine>\${dp.argLine}</argLine>")) {

                addWarningAboutSurefireWithArgline("POM file is not prepared to use the 'dropProject.currentUserId' system property")

            } else {
                report.add(Info(InfoType.INFO, "POM file is prepared to set the 'dropProject.currentUserId' system property"))
            }
        } else {
            report.add(Info(InfoType.INFO, "Doesn't use the 'dropProject.currentUserId' system property"))
        }

    }

    // tests that the surefire-plugin is showing full stacktraces
    private fun validateUntrimmedStacktrace(pomModel: Model) {

        val surefirePlugin = pomModel.build.plugins.find { it.artifactId == "maven-surefire-plugin" }
        if (surefirePlugin != null && surefirePlugin.version != null) {

            if (surefirePlugin.configuration == null ||
                    !surefirePlugin.configuration.toString().contains("<trimStackTrace>false</trimStackTrace>")) {

                report.add(Info(InfoType.WARNING, "POM file is not configured to prevent stacktrace trimming on junit errors",
                        "By default, the maven-surefire-plugin trims stacktraces (version >= 2.2), which may " +
                                "complicate students efforts to understand junit reports. " +
                                "It is suggested to set the 'trimStackStrace' flag to false, like this:<br/><pre>" +
                                """
                                    |<plugin>
                                    |   <groupId>org.apache.maven.plugins</groupId>
                                    |   <artifactId>maven-surefire-plugin</artifactId>
                                    |   <version>2.19.1</version>
                                    |   <configuration>
                                    |       ...
                                    |       <trimStackTrace>false</trimStackTrace>
                                    |   </configuration>
                                    |</plugin>
                                    """.trimMargin().toEscapedHtml()
                                + "</pre>"
                ))

            } else {
                report.add(Info(InfoType.INFO, "POM file is prepared to prevent stacktrace trimming on junit errors"))
            }
        } else {
            report.add(Info(InfoType.INFO, "POM file is prepared to prevent stacktrace trimming on junit errors"))
        }
    }


    private fun validatePomPreparedForMaxMemory(pomModel: Model) {
        val surefirePlugin = pomModel.build.plugins.find { it.artifactId == "maven-surefire-plugin" }
        if (surefirePlugin == null ||
                surefirePlugin.configuration == null ||
                !surefirePlugin.configuration.toString().contains("<argLine>\$\\{dp.argLine\\}</argLine>")) {

            addWarningAboutSurefireWithArgline("POM file is not prepared to set the max memory available")

        } else {
            report.add(Info(InfoType.INFO, "POM file is prepared to define the max memory for each submission"))
        }
    }

    /**
     * Performs [Assignment] validations related with the calculation the test coverage of student's own tests.
     * Namely, it validates if an assignment that is configured to calculate the coverage contains the necessary plugins
     * in the respective pom.xml file (and vice versa).
     *
     * @param pomModel is a Model
     * @param assignment is the Assignment to validate
     */
    private fun validatePomPreparedForCoverage(pomModel: Model, assignment: Assignment) {
        val surefirePlugin = pomModel.build.plugins.find { it.artifactId == "jacoco-maven-plugin" }
        val packagePath = assignment.packageName?.replace(".","/").orEmpty()
        if (surefirePlugin != null) {
            if (assignment.calculateStudentTestsCoverage) {
                if (surefirePlugin.configuration == null ||
                        !surefirePlugin.configuration.toString().contains("<include>${packagePath}/*</include>")) {
                    report.add(Info(InfoType.ERROR, "jacoco-maven-plugin (used for coverage) has a configuration problem",
                            "The jacoco-maven-plugin must include a configuration that includes only the classes of " +
                                    "the assignment package. Please fix this in your assignment POM file. " +
                                    "Configuration example:<br/><pre>" +
                                    """
                                    |<plugin>
                                    |    <groupId>org.jacoco</groupId>
                                    |    <artifactId>jacoco-maven-plugin</artifactId>
                                    |    <version>0.8.2</version>
                                    |    <configuration>
                                    |        <includes>
                                    |            <include>${packagePath}/*</include>
                                    |        </includes>
                                    |    </configuration>
                                    |    <executions>
                                    |        <execution>
                                    |            <goals>
                                    |                <goal>prepare-agent</goal>
                                    |            </goals>
                                    |        </execution>
                                    |        <execution>
                                    |            <id>generate-code-coverage-report</id>
                                    |            <phase>test</phase>
                                    |            <goals>
                                    |                <goal>report</goal>
                                    |            </goals>
                                    |        </execution>
                                    |    </executions>
                                    |</plugin>
                                    """.trimMargin().toEscapedHtml()
                                    + "</pre>"

                    ))
                } else {
                    report.add(Info(InfoType.INFO, "POM file is prepared to calculate coverage"))
                }
            } else {
                report.add(Info(InfoType.WARNING, "POM file includes a plugin to calculate coverage but the " +
                        "assignment has the flag 'Calculate coverage of student tests?' set to 'No'",
                        "For performance reasons, you should remove the jacoco-maven-plugin from your POM file"))
            }
        } else {
            if (assignment.calculateStudentTestsCoverage) {
                report.add(Info(InfoType.ERROR, "POM file is not prepared to calculate coverage",
                        "The assignment has the flag 'Calculate coverage of student tests?' set to 'Yes' " +
                                "but the POM file doesn't include the jacoco-maven-plugin. Please add the following " +
                                "lines to your pom file:<br/><pre>" +
                                """
                                    |<plugin>
                                    |    <groupId>org.jacoco</groupId>
                                    |    <artifactId>jacoco-maven-plugin</artifactId>
                                    |    <version>0.8.2</version>
                                    |    <configuration>
                                    |        <includes>
                                    |            <include>${packagePath}/*</include>
                                    |        </includes>
                                    |    </configuration>
                                    |    <executions>
                                    |        <execution>
                                    |            <goals>
                                    |                <goal>prepare-agent</goal>
                                    |            </goals>
                                    |        </execution>
                                    |        <execution>
                                    |            <id>generate-code-coverage-report</id>
                                    |            <phase>test</phase>
                                    |            <goals>
                                    |                <goal>report</goal>
                                    |            </goals>
                                    |        </execution>
                                    |    </executions>
                                    |</plugin>
                                """.trimMargin().toEscapedHtml()
                                + "</pre>"))
                println("")
            }
        }
    }

    /**
     * Validates an [Assignment]'s test files to determine if the test classes are respecting the expected
     * formats (for example, ensure that the respective filename starts with the correct prefix).
     *
     * @param assignmentFolder is a File, representing the file system folder where the assignment's code is stored
     * @param assignment is the Assignment to validate
     */
    private fun validateProperTestClasses(assignmentFolder: File, assignment: Assignment) {

        var correctlyPrefixed = true

        val testClasses = File(assignmentFolder, "src/test")
                .walkTopDown()
                .filter { it -> it.name.startsWith(Constants.TEST_NAME_PREFIX) }
                .toList()

        if (testClasses.isEmpty()) {
            report.add(Info(InfoType.WARNING, "You must have at least one test class on src/test/** whose name starts with ${Constants.TEST_NAME_PREFIX}"))
        } else {
            report.add(Info(InfoType.INFO, "Found ${testClasses.size} test classes"))

            // for each test class, check if all the @Test define a timeout
            var invalidTestMethods = 0
            var validTestMethods = 0
            var mandatoryTestMethods = 0
            var hasGlobalTimeout = false

            if (assignment.language == Language.JAVA) {
                val builder = JavaProjectBuilder()

                for (testClass in testClasses) {
                    val testClassSource = builder.addSource(testClass)
                    testClassSource.classes.forEach {

                        if (it.annotations.any { annotation -> annotation.type.fullyQualifiedName == "org.junit.jupiter.api.Timeout" }) {
                            hasGlobalTimeout = true
                        }

                        it.methods.forEach {
                            val methodName = it.name

                            if (!it.annotations.any { it.type.fullyQualifiedName == "org.junit.Ignore" ||  // ignore @Ignore (junit4)
                                            it.type.fullyQualifiedName == "org.junit.jupiter.api.Disabled" ||  // ignore @Disabled (junit5)
                                            it.type.fullyQualifiedName == "Ignore" }) {
                                it.annotations.forEach {
                                    if (it.type.fullyQualifiedName == "org.junit.Test" ||  // found @Test (junit4)
                                        it.type.fullyQualifiedName == "org.junit.jupiter.api.Test" ||  // found @Test (junit5)
                                        it.type.fullyQualifiedName == "Test") {  // qdox doesn't handle import *
                                        if (it.getNamedParameter("timeout") == null && !hasGlobalTimeout) {
                                            invalidTestMethods++
                                        } else {
                                            validTestMethods++
                                        }
                                        testMethods.add(testClassSource.classes.get(0).name + ":" + methodName)

                                        assignment.mandatoryTestsSuffix?.let { suffix ->
                                            if (methodName.endsWith(suffix)) {
                                                mandatoryTestMethods++
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            } else if (assignment.language == Language.KOTLIN) {

                for (testClass in testClasses) {
                    val ktCompiler = io.github.detekt.parser.KtCompiler()
                    val ktFile = ktCompiler.compile(Paths.get("").toAbsolutePath(), testClass.toPath())
                    ktFile.declarations.filter { it is KtClass }.forEach {
                        val clazz = it as KtClass
                        clazz.declarations.filter { it is KtNamedFunction }.forEach {
                            val function = it as KtNamedFunction

                            if (function.annotationEntries.any { it.shortName?.asString() == "Test" }) {
                                if (function.annotationEntries.first { it.shortName?.asString() == "Test"  }.text.contains("timeout")) {
                                    validTestMethods++;
                                } else {
                                    invalidTestMethods++;
                                }

                                testMethods.add(clazz.name + ":" + function.name)

                                assignment.mandatoryTestsSuffix?.let { suffix ->
                                    if (function.name?.endsWith(suffix) == true) {
                                        mandatoryTestMethods++
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (invalidTestMethods + validTestMethods == 0) {
                report.add(Info(InfoType.WARNING, "You haven't defined any test methods.", "Use the @Test(timeout=xxx) annotation to mark test methods."))
            }

            if (invalidTestMethods > 0) {
                report.add(Info(InfoType.WARNING, "You haven't defined a timeout for ${invalidTestMethods} test methods.",
                    "If you don't define a timeout, students submitting projects with infinite loops or wait conditions " +
                            "will degrade the server. Example: Use @Test(timeout=500) to set a timeout of 500 miliseconds (JUnit 4) or" +
                            "@Timeout (JUnit 5)."))
            } else if (validTestMethods > 0) {
                if (hasGlobalTimeout) {
                    report.add(Info(InfoType.INFO, "You have defined a global timeout for the test methods."))
                } else {
                    report.add(Info(InfoType.INFO, "You have defined ${validTestMethods} test methods with timeout."))
                }

            }

            if (!assignment.mandatoryTestsSuffix.isNullOrEmpty()) {
                if (mandatoryTestMethods == 0) {
                    report.add(Info(InfoType.WARNING, "You haven't defined mandatory tests",
                        "You have defined a mandatory tests suffix (${assignment.mandatoryTestsSuffix}) but none of the " +
                                "test methods end with that suffix."))
                } else {
                    report.add(Info(InfoType.INFO, "You have defined ${mandatoryTestMethods} mandatory test methods"))
                }
            }
        }

        if (assignment.acceptsStudentTests) {
            for (testClass in testClasses) {
                if (!testClass.name.startsWith(Constants.TEACHER_TEST_NAME_PREFIX)) {
                    report.add(Info(InfoType.WARNING, "${testClass} is not valid for assignments which accept student tests.",
                            "All teacher tests must be prefixed with ${Constants.TEACHER_TEST_NAME_PREFIX} " +
                                    "(e.g., ${Constants.TEACHER_TEST_NAME_PREFIX}Calculator" +
                                    " instead of ${Constants.TEST_NAME_PREFIX}Calculator)"))
                    correctlyPrefixed = false
                }
            }

            if (correctlyPrefixed) {
                report.add(Info(InfoType.INFO, "All test classes correctly prefixed"));
            }
        }

        // check if it has hidden tests and the visibility policy has not been set
        val hasHiddenTests = File(assignmentFolder, "src/test")
                .walkTopDown()
                .any { it -> it.name.startsWith(Constants.TEACHER_HIDDEN_TEST_NAME_PREFIX) }

        if (hasHiddenTests) {
            if (assignment.hiddenTestsVisibility == null) {
                report.add(Info(InfoType.ERROR, "You have hidden tests but you didn't set their visibility to students.",
                        "Edit this assignment and select an option in the field 'Hidden tests' to define if the results should be " +
                                "completely hidden from the students or if some information is shown."))
            } else {
                val message = when (assignment.hiddenTestsVisibility) {
                        TestVisibility.HIDE_EVERYTHING ->  "The results will be completely hidden from the students."
                        TestVisibility.SHOW_OK_NOK -> "Students will only see if they pass all the hidden tests or not."
                        TestVisibility.SHOW_PROGRESS -> "Students will only see the number of tests passed."
                        null -> throw Exception("This shouldn't be possible!")
                }
                report.add(Info(InfoType.INFO, "You have hidden tests. ${message}"))
            }
        }

    }

    private fun validateCheckstyleVersion(pomModel: Model) {

        val checkstylePlugin = pomModel.build.plugins.find { it.artifactId == "maven-checkstyle-plugin" }
        if (checkstylePlugin != null) {
            val checkstyleDependency = checkstylePlugin.dependencies.find { it.artifactId == "checkstyle" }
            if (checkstyleDependency != null) {
                val majorVersion = checkstyleDependency.version.split(".")[0].toInt()
                if (majorVersion < 9) {
                    report.add(Info(InfoType.ERROR, "You are using an outdated version of checkstyle.",
                            "You checkstyle dependency should be at least '9.0.1' " +
                                    "to support recent java versions' syntax."))
                } else {
                    report.add(Info(InfoType.INFO, "You are using a recent version of checkstyle."))
                }
            }
        }
    }

    private fun searchAllSourceFilesWithinFolder(folder: File, text: String): Boolean {
        return folder.walkTopDown()
                .filter { it -> it.name.endsWith(".java") || it.name.endsWith(".kt") }
                .any {
                    it.readText().contains(text)
                }
    }

    private fun addWarningAboutSurefireWithArgline(message: String) {
        report.add(Info(InfoType.WARNING, message,
                "The system property 'dropProject.currentUserId' only works if you add the following lines to your pom file:<br/><pre>" +
                        """
                        |<plugin>
                        |   <groupId>org.apache.maven.plugins</groupId>
                        |   <artifactId>maven-surefire-plugin</artifactId>
                        |   <version>2.19.1</version>
                        |   <configuration>
                        |      <argLine>$\{dp.argLine}</argLine>
                        |   </configuration>
                        |</plugin>
                        """.trimMargin().replace("\\","").toEscapedHtml()
                        + "</pre>"

        ))
    }

}
