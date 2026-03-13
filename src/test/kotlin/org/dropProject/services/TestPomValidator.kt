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

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import java.io.File


@RunWith(SpringRunner::class)
@SpringBootTest
@TestPropertySource(locations = ["classpath:drop-project-test.properties"])
@ActiveProfiles("test")
class TestPomValidator {

    @Autowired
    private lateinit var pomValidator: PomValidator

    @Test
    fun `validate matching pom files`() {
        val teacherPom = File("src/test/sampleProjects/maven/java/projectOK-maven/pom.xml")
        val studentPom = File("src/test/sampleProjects/maven/java/projectOK-maven/pom.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertTrue("Validation should pass for matching POMs", result.isValid)
        assertTrue("No errors expected", result.errors.isEmpty())
    }

    @Test
    fun `validate pom with extra dependencies`() {
        val teacherPom = File("src/test/sampleAssignments/sampleJavaProject/pom.xml")
        val studentPom = File("src/test/samplePomFiles/pom-with-extra-deps.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertFalse("Validation should fail for extra dependencies", result.isValid)
        assertTrue("Should have error messages", result.errors.isNotEmpty())
        assertTrue("Should mention extra dependencies",
            result.errors.any { it.contains("extra dependencies", ignoreCase = true) ||
                                it.contains("commons-lang3") })
    }

    @Test
    fun `validate pom with missing dependencies`() {
        val teacherPom = File("src/test/sampleAssignments/sampleJavaProject/pom.xml")
        val studentPom = File("src/test/samplePomFiles/pom-with-missing-deps.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertFalse("Validation should fail for missing dependencies", result.isValid)
        assertTrue("Should have error messages", result.errors.isNotEmpty())
        assertTrue("Should mention missing dependencies",
            result.errors.any { it.contains("missing", ignoreCase = true) ||
                                it.contains("junit") })
    }

    @Test
    fun `validate pom with different version`() {
        val teacherPom = File("src/test/sampleAssignments/sampleJavaProject/pom.xml")
        val studentPom = File("src/test/samplePomFiles/pom-with-different-version.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertFalse("Validation should fail for different versions", result.isValid)
        assertTrue("Should have error messages", result.errors.isNotEmpty())
        // Different version should be detected as both extra (new version) and missing (old version)
        assertTrue("Should detect version mismatch",
            result.errors.any { it.contains("junit:junit") && it.contains("4.13.1") && it.contains("4.13.2") })
    }

    @Test
    fun `validate pom with matching parent`() {
        val teacherPom = File("src/test/samplePomFiles/pom-with-parent.xml")
        val studentPom = File("src/test/samplePomFiles/pom-with-parent.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertTrue("Validation should pass for matching parent", result.isValid)
        assertTrue("No errors expected", result.errors.isEmpty())
    }

    @Test
    fun `validate pom with different parent version`() {
        val teacherPom = File("src/test/samplePomFiles/pom-with-parent.xml")
        val studentPom = File("src/test/samplePomFiles/pom-with-parent-different-version.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertFalse("Validation should fail for different parent version", result.isValid)
        assertTrue("Should have error messages", result.errors.isNotEmpty())
        assertTrue("Should detect parent version mismatch",
            result.errors.any { it.contains("3.5.10") && it.contains("4.0.2") })
    }

    @Test
    fun `validate pom with missing parent`() {
        val teacherPom = File("src/test/samplePomFiles/pom-with-parent.xml")
        val studentPom = File("src/test/samplePomFiles/pom-without-parent.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertFalse("Validation should fail for missing parent", result.isValid)
        assertTrue("Should have error messages", result.errors.isNotEmpty())
        assertTrue("Should mention the expected parent",
            result.errors.any { it.contains("spring-boot-starter-parent") })
    }

    @Test
    fun `validate pom without parent when teacher has no parent`() {
        val teacherPom = File("src/test/sampleAssignments/sampleJavaProject/pom.xml")
        val studentPom = File("src/test/sampleAssignments/sampleJavaProject/pom.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertTrue("Validation should pass when neither has parent", result.isValid)
    }

    @Test
    fun `validate kotlin pom files`() {
        val teacherPom = File("src/test/sampleAssignments/testKotlinProj/pom.xml")
        val studentPom = File("src/test/sampleProjects/maven/kotlin/projectKotlinOK-maven/pom.xml")

        val result = pomValidator.validateStudentPom(studentPom, teacherPom, acceptsStudentTests = true)

        assertTrue("Validation should pass for matching Kotlin POMs", result.isValid)
        assertTrue("No errors expected", result.errors.isEmpty())
    }
}
