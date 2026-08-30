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

import org.junit.jupiter.api.extension.ExtendWith
import org.dropproject.ResetStateExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.io.File


@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
@ExtendWith(ResetStateExtension::class)
class TestPomValidator {

    @Autowired
    private lateinit var pomValidator: PomValidator

    @Test
    fun `validate matching pom files`() {
        val teacherPom = File("src/test/sampleProjects/maven/java/projectOK-maven/pom.xml")
        val studentPom = File("src/test/sampleProjects/maven/java/projectOK-maven/pom.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertTrue(result.isValid, "Validation should pass for matching POMs")
        assertTrue(result.errors.isEmpty(), "No errors expected")
    }

    @Test
    fun `validate pom with extra dependencies`() {
        val teacherPom = File("src/test/sampleAssignments/sampleJavaProject/pom.xml")
        val studentPom = File("src/test/samplePomFiles/pom-with-extra-deps.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertFalse(result.isValid, "Validation should fail for extra dependencies")
        assertTrue(result.errors.isNotEmpty(), "Should have error messages")
        assertTrue(result.errors.any { it.contains("extra dependencies", ignoreCase = true) ||
                                it.contains("commons-lang3") }, "Should mention extra dependencies")
    }

    @Test
    fun `validate pom with missing dependencies`() {
        val teacherPom = File("src/test/sampleAssignments/sampleJavaProject/pom.xml")
        val studentPom = File("src/test/samplePomFiles/pom-with-missing-deps.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertFalse(result.isValid, "Validation should fail for missing dependencies")
        assertTrue(result.errors.isNotEmpty(), "Should have error messages")
        assertTrue(result.errors.any { it.contains("missing", ignoreCase = true) ||
                                it.contains("junit") }, "Should mention missing dependencies")
    }

    @Test
    fun `validate pom with different version`() {
        val teacherPom = File("src/test/sampleAssignments/sampleJavaProject/pom.xml")
        val studentPom = File("src/test/samplePomFiles/pom-with-different-version.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertFalse(result.isValid, "Validation should fail for different versions")
        assertTrue(result.errors.isNotEmpty(), "Should have error messages")
        // Different version should be detected as both extra (new version) and missing (old version)
        assertTrue(result.errors.any { it.contains("junit:junit") && it.contains("4.13.1") && it.contains("4.13.2") }, "Should detect version mismatch")
    }

    @Test
    fun `validate pom with matching parent`() {
        val teacherPom = File("src/test/samplePomFiles/pom-with-parent.xml")
        val studentPom = File("src/test/samplePomFiles/pom-with-parent.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertTrue(result.isValid, "Validation should pass for matching parent")
        assertTrue(result.errors.isEmpty(), "No errors expected")
    }

    @Test
    fun `validate pom with different parent version`() {
        val teacherPom = File("src/test/samplePomFiles/pom-with-parent.xml")
        val studentPom = File("src/test/samplePomFiles/pom-with-parent-different-version.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertFalse(result.isValid, "Validation should fail for different parent version")
        assertTrue(result.errors.isNotEmpty(), "Should have error messages")
        assertTrue(result.errors.any { it.contains("3.5.10") && it.contains("4.0.2") }, "Should detect parent version mismatch")
    }

    @Test
    fun `validate pom with missing parent`() {
        val teacherPom = File("src/test/samplePomFiles/pom-with-parent.xml")
        val studentPom = File("src/test/samplePomFiles/pom-without-parent.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertFalse(result.isValid, "Validation should fail for missing parent")
        assertTrue(result.errors.isNotEmpty(), "Should have error messages")
        assertTrue(result.errors.any { it.contains("spring-boot-starter-parent") }, "Should mention the expected parent")
    }

    @Test
    fun `validate pom without parent when teacher has no parent`() {
        val teacherPom = File("src/test/sampleAssignments/sampleJavaProject/pom.xml")
        val studentPom = File("src/test/sampleAssignments/sampleJavaProject/pom.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertTrue(result.isValid, "Validation should pass when neither has parent")
    }

    @Test
    fun `validate kotlin pom files`() {
        val teacherPom = File("src/test/sampleAssignments/testKotlinProj/pom.xml")
        val studentPom = File("src/test/sampleProjects/maven/kotlin/projectKotlinOK-maven/pom.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertTrue(result.isValid, "Validation should pass for matching Kotlin POMs")
        assertTrue(result.errors.isEmpty(), "No errors expected")
    }
}
