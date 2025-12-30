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
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileReader

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
class PomValidator {

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

            val studentDeps = studentModel.dependencies.orEmpty()
            val teacherDeps = teacherModel.dependencies.orEmpty()

            // Create dependency keys for comparison (groupId:artifactId:version)
            val allTeacherDepKeys = teacherDeps.map { depKey(it) }.toSet()
            val studentDepKeys = studentDeps.map { depKey(it) }.toSet()

            // Check for extra dependencies in student pom
            // Students cannot add dependencies not in teacher's pom
            val extraDeps = studentDepKeys - allTeacherDepKeys
            if (extraDeps.isNotEmpty()) {
                errors.add("Student pom.xml contains dependencies not in teacher's pom.xml:")
                extraDeps.forEach { errors.add("  - $it") }
            }

            // Check for missing required dependencies
            // When student tests are not accepted, test dependencies are optional
            val requiredTeacherDeps = if (acceptsStudentTests) {
                teacherDeps
            } else {
                teacherDeps.filter { !isTestDependency(it) }
            }
            val requiredDepKeys = requiredTeacherDeps.map { depKey(it) }.toSet()
            val missingDeps = requiredDepKeys - studentDepKeys
            if (missingDeps.isNotEmpty()) {
                errors.add("Student pom.xml is missing required dependencies:")
                missingDeps.forEach { errors.add("  - $it") }
            }

            // If no errors, validation passes
            if (errors.isEmpty()) {
                return PomValidationResult(isValid = true)
            }

        } catch (e: Exception) {
            errors.add("Failed to parse pom.xml file: ${e.message}")
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
     * Creates a dependency key for comparison.
     *
     * @param dep the Maven dependency
     * @return a string key in format "groupId:artifactId:version"
     */
    private fun depKey(dep: Dependency): String {
        return "${dep.groupId}:${dep.artifactId}:${dep.version}"
    }
}
