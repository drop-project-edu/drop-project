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

import org.apache.maven.model.Dependency
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileReader
import java.util.Locale

/**
 * Result of validating a student's pom.xml file against the teacher's pom.xml file.
 *
 * @property isValid indicates whether the validation passed
 * @property errors list of error messages if validation failed
 */
data class PomValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
)

/**
 * Service responsible for validating student pom.xml files against teacher pom.xml files.
 */
@Service
class PomValidator(val i18n: MessageSource) {

    @Value("\${spring.web.locale}")
    val currentLocale: Locale = Locale.getDefault()

    /**
     * Validates that student's pom.xml dependencies match teacher's pom.xml dependencies.
     * Any differences (missing, extra, or version mismatches) result in validation failure.
     * When student tests are not accepted, testing dependencies are excluded from the required dependencies.
     *
     * @param studentPomFile the student's pom.xml file
     * @param teacherPomFile the teacher's pom.xml file
     * @param acceptsStudentTests whether the assignment accepts student tests
     * @return [PomValidationResult] containing validation status and any errors
     */
    fun validateStudentPom(studentPomFile: File, teacherPomFile: File,
                           acceptsStudentTests: Boolean): PomValidationResult {
        val errors = mutableListOf<String>()

        try {
            val reader = MavenXpp3Reader()
            val studentModel = reader.read(FileReader(studentPomFile))
            val teacherModel = reader.read(FileReader(teacherPomFile))

            // Validate parent element
            val teacherParent = teacherModel.parent
            val studentParent = studentModel.parent

            if (teacherParent != null) {
                if (studentParent == null) {
                    errors.add(i18n.getMessage("error.maven.parent.missing", null, currentLocale))
                    errors.add("  - ${teacherParent.groupId}:${teacherParent.artifactId}:${teacherParent.version}")
                } else if (teacherParent.groupId != studentParent.groupId ||
                           teacherParent.artifactId != studentParent.artifactId) {
                    errors.add(i18n.getMessage("error.maven.parent.mismatch", null, currentLocale))
                    errors.add("  - expected: ${teacherParent.groupId}:${teacherParent.artifactId}:${teacherParent.version}")
                    errors.add("  - found: ${studentParent.groupId}:${studentParent.artifactId}:${studentParent.version}")
                } else if (teacherParent.version != studentParent.version) {
                    errors.add(i18n.getMessage("error.maven.parent.version", null, currentLocale))
                    errors.add("  - ${teacherParent.groupId}:${teacherParent.artifactId} (expected: ${teacherParent.version}, found: ${studentParent.version})")
                }
            }

            val studentDeps = studentModel.dependencies.orEmpty()
            val teacherDeps = teacherModel.dependencies.orEmpty()

            // Filter required teacher deps (when student tests are not accepted, test deps are optional)
            val requiredTeacherDeps = if (acceptsStudentTests) {
                teacherDeps
            } else {
                teacherDeps.filter { !isTestDependency(it) }
            }

            // Maps by groupId:artifactId for detecting version mismatches
            val teacherDepsByGA = teacherDeps.associateBy { depGA(it) }
            val studentDepsByGA = studentDeps.associateBy { depGA(it) }

            // Check for version mismatches (same groupId:artifactId, different version)
            val mismatchedDeps = studentDeps.filter { dep ->
                val ga = depGA(dep)
                val teacherDep = teacherDepsByGA[ga]
                teacherDep != null && depKey(dep) != depKey(teacherDep)
            }
            if (mismatchedDeps.isNotEmpty()) {
                errors.add(i18n.getMessage("error.maven.deps.mismatch", null, currentLocale))
                mismatchedDeps.forEach { dep ->
                    val ga = depGA(dep)
                    errors.add("  - ${ga} (expected: ${teacherDepsByGA[ga]!!.version}, found: ${dep.version})")
                }
            }

            // Check for extra dependencies (not in teacher's pom at all)
            val extraDeps = studentDeps.filter { depGA(it) !in teacherDepsByGA }
            if (extraDeps.isNotEmpty()) {
                errors.add(i18n.getMessage("error.maven.deps.extra", null, currentLocale))
                extraDeps.forEach { errors.add("  - ${depKey(it)}") }
            }

            // Check for missing required dependencies
            val missingDeps = requiredTeacherDeps.filter { depGA(it) !in studentDepsByGA }
            if (missingDeps.isNotEmpty()) {
                errors.add(i18n.getMessage("error.maven.deps.missing", null, currentLocale))
                missingDeps.forEach { errors.add("  - ${depKey(it)}") }
            }

            // If no errors, validation passes
            if (errors.isEmpty()) {
                return PomValidationResult(isValid = true)
            }

        } catch (e: Exception) {
            errors.add(i18n.getMessage("error.maven.structure.invalid", null, currentLocale))
        }

        return PomValidationResult(isValid = false, errors = errors)
    }

    /**
     * Checks if a dependency is a testing dependency (JUnit, TestNG, Mockito, etc.).
     *
     * @param dep the Maven dependency to check
     * @return true if the dependency is for testing purposes
     */
    private fun isTestDependency(dep: Dependency): Boolean {
        val groupId = dep.groupId?.lowercase() ?: ""
        val artifactId = dep.artifactId?.lowercase() ?: ""

        return groupId.contains("junit") ||
               artifactId.contains("junit") ||
               groupId == "org.testng" ||
               groupId == "org.mockito" ||
               groupId.contains("hamcrest") ||
               artifactId.contains("mockito") ||
               artifactId.contains("hamcrest") ||
               dep.scope == "test"
    }

    /**
     * Creates a dependency key for comparison (includes version).
     *
     * @param dep the Maven dependency
     * @return a string key in format "groupId:artifactId:version"
     */
    private fun depKey(dep: Dependency): String {
        return "${dep.groupId}:${dep.artifactId}:${dep.version}"
    }

    /**
     * Creates a dependency key without version, for detecting version mismatches.
     *
     * @param dep the Maven dependency
     * @return a string key in format "groupId:artifactId"
     */
    private fun depGA(dep: Dependency): String {
        return "${dep.groupId}:${dep.artifactId}"
    }
}
