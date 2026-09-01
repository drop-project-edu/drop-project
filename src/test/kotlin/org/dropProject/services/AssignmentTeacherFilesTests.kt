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

import org.dropproject.DropProjectIntegrationTest
import org.dropproject.dao.Language
import org.dropproject.dao.SubmissionStructure
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@DropProjectIntegrationTest
class AssignmentTeacherFilesTests {

    @Autowired
    lateinit var assignmentTeacherFiles: AssignmentTeacherFiles

    @Test
    fun `buildPackageTree for a compact java project shows the package folders and the main file`() {
        val tree = assignmentTeacherFiles.buildPackageTree(
            "org.dropProject.sample", Language.JAVA, SubmissionStructure.COMPACT)

        assertTrue(tree.contains("+ src"), "tree should start at the src folder:\n$tree")
        assertTrue(tree.contains("|--- org"), "tree should contain the first package level:\n$tree")
        assertTrue(tree.contains("|------ dropProject"), "tree should contain the second package level:\n$tree")
        assertTrue(tree.contains("|--------- sample"), "tree should contain the third package level:\n$tree")
        assertTrue(tree.contains("Main.java"), "a java project must show Main.java:\n$tree")
        assertFalse(tree.contains("pom.xml"), "a compact project must not mention pom.xml:\n$tree")
    }

    @Test
    fun `buildPackageTree for a compact kotlin project without package shows the main file at the root`() {
        val tree = assignmentTeacherFiles.buildPackageTree(
            null, Language.KOTLIN, SubmissionStructure.COMPACT)

        assertTrue(tree.contains("Main.kt"), "a kotlin project must show Main.kt:\n$tree")
        assertFalse(tree.contains("Main.java"), "a kotlin project must not show Main.java:\n$tree")
    }

    @Test
    fun `buildPackageTree for a maven java project shows the maven layout`() {
        val tree = assignmentTeacherFiles.buildPackageTree(
            "org.sample", Language.JAVA, SubmissionStructure.MAVEN)

        assertTrue(tree.contains("pom.xml"), "a maven project must mention pom.xml:\n$tree")
        assertTrue(tree.contains("|--- main"), "a maven project must show src/main:\n$tree")
        assertTrue(tree.contains("|------ java"), "a java maven project must show the java folder:\n$tree")
        assertTrue(tree.contains("SomethingApplication.java"), "must show the spring boot application file:\n$tree")
        assertTrue(tree.contains("application.properties"), "must show the resources:\n$tree")
        assertTrue(tree.contains("|--- test"), "a maven project must show src/test:\n$tree")
    }

    @Test
    fun `buildPackageTree for a maven kotlin project with student tests shows the student tests placeholder`() {
        val tree = assignmentTeacherFiles.buildPackageTree(
            "org.sample", Language.KOTLIN, SubmissionStructure.MAVEN, hasStudentTests = true)

        assertTrue(tree.contains("|------ kotlin"), "a kotlin maven project must show the kotlin folder:\n$tree")
        assertTrue(tree.contains("SomethingApplication.kt"), "must show the kotlin application file:\n$tree")
        assertTrue(tree.contains("(student tests)"), "must mention the student tests placeholder:\n$tree")
    }
}
