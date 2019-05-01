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

import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.dropProject.Constants
import org.dropProject.dao.Assignment
import org.dropProject.dao.TestVisibility
import org.dropProject.extensions.toEscapedHtml
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Service
import java.io.*

@Service
@Scope("prototype")
class AssignmentValidator {

    enum class InfoType { INFO, WARNING, ERROR }

    data class Info(val type: InfoType, val message: String, val description: String = "")

    val report = mutableListOf<Info>()

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

        validateCurrentUserIdSystemVariable(assignmentFolder, model)
        validateProperTestClasses(assignmentFolder, assignment)
        if (assignment.maxMemoryMb != null) {
            validatePomPreparedForMaxMemory(model)
        }
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
            for (testClass in testClasses) {
                val content = testClass.readLines()
                invalidTestMethods += content.filter { it.contains("@Test") && !it.contains("@Test(timeout=") }.count()
                validTestMethods += content.filter { it.contains("@Test(timeout=") }.count()
            }


            if (invalidTestMethods + validTestMethods == 0) {
                report.add(Info(InfoType.WARNING, "You haven't defined any test methods.", "Use the @Test(timeout=xxx) annotation to mark test methods."))
            }

            if (invalidTestMethods > 0) {
                report.add(Info(InfoType.WARNING, "You haven't defined a timeout for ${invalidTestMethods} test methods.",
                        "If you don't define a timeout, students submitting projects with infinite loops or wait conditions " +
                                "will degrade the server. Example: Use @Test(timeout=500) to set a timeout of 500 miliseconds."))
            } else if (validTestMethods > 0) {
                report.add(Info(InfoType.INFO, "You have defined ${validTestMethods} test methods with timeout."))
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
                        TestVisibility.SHOW_OK_NOK -> "Students will only see if it passes all the hidden tests or not."
                        TestVisibility.SHOW_PROGRESS -> "Students will only see the number of tests passed."
                        null -> throw Exception("This shouldn't be possible!")
                }
                report.add(Info(InfoType.INFO, "You have hidden tests. ${message}"))
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
<plugin>
   <groupId>org.apache.maven.plugins</groupId>
   <artifactId>maven-surefire-plugin</artifactId>
   <version>2.19.1</version>
   <configuration>
      <argLine>$\{dp.argLine}</argLine>
   </configuration>
</plugin>
    """.replace("\\","").toEscapedHtml()
                        + "</pre>"

        ))
    }

}
