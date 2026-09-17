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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Tests for [SourceDiffService], the utility that measures how much the source code of two project folders
 * diverges. Each test builds a pair of project folders in a temporary directory, so that the expected number of
 * changed lines is visible right next to the assertion.
 */
class SourceDiffServiceTests {

    private val sourceDiffService = SourceDiffService()

    @TempDir
    lateinit var tempFolder: File

    private val sourcePath = "src/org/dropProject/sampleAssignments/testProj/Main.java"

    /**
     * Creates a project folder with the given name inside the temporary folder of this test.
     */
    private fun projectFolder(name: String): File {
        val folder = File(tempFolder, name)
        folder.mkdirs()
        return folder
    }

    /**
     * Writes a file (creating the intermediate folders) inside a project folder.
     */
    private fun writeFile(projectFolder: File, relativePath: String, content: String) {
        val file = File(projectFolder, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun writeBinaryFile(projectFolder: File, relativePath: String, content: ByteArray) {
        val file = File(projectFolder, relativePath)
        file.parentFile.mkdirs()
        file.writeBytes(content)
    }

    /**
     * Builds the content of a text file from its lines, so that the line count of each fixture is obvious.
     */
    private fun linesOf(vararg lines: String) = lines.joinToString(separator = "\n", postfix = "\n")

    @Test
    fun `two identical projects have no changed lines`() {

        val content = linesOf("line 1", "line 2", "line 3")

        val reference = projectFolder("reference")
        writeFile(reference, sourcePath, content)

        val new = projectFolder("new")
        writeFile(new, sourcePath, content)

        assertEquals(0, sourceDiffService.countChangedLines(reference, new))
    }

    @Test
    fun `a file that only exists in the new project counts all its lines as added`() {

        val reference = projectFolder("reference")
        writeFile(reference, sourcePath, linesOf("line 1", "line 2", "line 3"))

        val new = projectFolder("new")
        writeFile(new, sourcePath, linesOf("line 1", "line 2", "line 3"))
        writeFile(new, "src/org/dropProject/sampleAssignments/testProj/Helper.java",
            linesOf("added 1", "added 2", "added 3", "added 4"))

        assertEquals(4, sourceDiffService.countChangedLines(reference, new))
    }

    @Test
    fun `a file that only exists in the reference project counts all its lines as removed`() {

        val reference = projectFolder("reference")
        writeFile(reference, sourcePath, linesOf("line 1", "line 2", "line 3"))
        writeFile(reference, "src/org/dropProject/sampleAssignments/testProj/Helper.java",
            linesOf("removed 1", "removed 2"))

        val new = projectFolder("new")
        writeFile(new, sourcePath, linesOf("line 1", "line 2", "line 3"))

        assertEquals(2, sourceDiffService.countChangedLines(reference, new))
    }

    @Test
    fun `only the modified lines of a file are counted`() {

        val reference = projectFolder("reference")
        writeFile(reference, sourcePath, linesOf("line 1", "line 2", "line 3", "line 4", "line 5"))

        val new = projectFolder("new")
        writeFile(new, sourcePath, linesOf("line 1", "line 2", "CHANGED", "line 4", "line 5"))

        assertEquals(1, sourceDiffService.countChangedLines(reference, new))
    }

    @Test
    fun `a line replaced by several lines counts as the biggest of the two sides`() {

        val reference = projectFolder("reference")
        writeFile(reference, sourcePath, linesOf("line 1", "line 2", "line 3", "line 4"))

        val new = projectFolder("new")
        writeFile(new, sourcePath, linesOf("line 1", "new 2a", "new 2b", "new 2c", "line 3", "line 4"))

        // one line was replaced by three, so the change is 3 lines wide
        assertEquals(3, sourceDiffService.countChangedLines(reference, new))
    }

    @Test
    fun `lines added and removed in the same file are both counted`() {

        val reference = projectFolder("reference")
        writeFile(reference, sourcePath, linesOf("line 1", "line 2", "line 3", "line 4", "line 5", "line 6", "line 7"))

        val new = projectFolder("new")
        // two lines were inserted after 'line 1' and 'line 5' was removed
        writeFile(new, sourcePath,
            linesOf("line 1", "added a", "added b", "line 2", "line 3", "line 4", "line 6", "line 7"))

        assertEquals(3, sourceDiffService.countChangedLines(reference, new))
    }

    @Test
    fun `changes in several files are summed`() {

        val reference = projectFolder("reference")
        writeFile(reference, "src/pkg/Untouched.java", linesOf("line 1", "line 2"))
        writeFile(reference, "src/pkg/Modified.java", linesOf("line 1", "line 2", "line 3"))
        writeFile(reference, "src/pkg/Removed.java", linesOf("line 1", "line 2"))

        val new = projectFolder("new")
        writeFile(new, "src/pkg/Untouched.java", linesOf("line 1", "line 2"))          // 0 changed lines
        writeFile(new, "src/pkg/Modified.java", linesOf("line 1", "CHANGED", "line 3"))  // 1 changed line
        writeFile(new, "src/pkg/Added.java", linesOf("line 1", "line 2", "line 3"))    // 3 added lines
                                                                                       // Removed.java: 2 removed lines

        assertEquals(6, sourceDiffService.countChangedLines(reference, new))
    }

    @Test
    fun `blank lines added to a file are not counted`() {

        val reference = projectFolder("reference")
        writeFile(reference, sourcePath, linesOf("line 1", "line 2"))

        val new = projectFolder("new")
        writeFile(new, sourcePath, linesOf("line 1", "", "   ", "line 2", ""))

        assertEquals(0, sourceDiffService.countChangedLines(reference, new))
    }

    @Test
    fun `blank lines are not counted around a real change`() {

        val reference = projectFolder("reference")
        writeFile(reference, sourcePath, linesOf("line 1", "line 2"))

        val new = projectFolder("new")
        writeFile(new, sourcePath, linesOf("line 1", "", "line 2 changed", ""))

        assertEquals(1, sourceDiffService.countChangedLines(reference, new))
    }

    @Test
    fun `the blank lines of an added file are not counted`() {

        val reference = projectFolder("reference")
        writeFile(reference, "src/other.java", linesOf("something"))

        val new = projectFolder("new")
        writeFile(new, "src/other.java", linesOf("something"))
        writeFile(new, sourcePath, linesOf("line 1", "", "line 2", "  ", "line 3"))

        assertEquals(3, sourceDiffService.countChangedLines(reference, new))
    }

    @Test
    fun `the folder that is compared can be narrowed`() {

        val reference = projectFolder("reference")
        writeFile(reference, "src/main/java/Main.java", linesOf("line 1", "line 2"))
        writeFile(reference, "src/test/java/TestTeacher.java", linesOf("a test"))

        val new = projectFolder("new")
        writeFile(new, "src/main/java/Main.java", linesOf("line 1", "line 2 changed"))
        writeFile(new, "src/test/java/TestTeacher.java", linesOf("a test", "another test", "yet another"))

        assertEquals(3, sourceDiffService.countChangedLines(reference, new),
            "the whole src folder counts the tests too")
        assertEquals(1, sourceDiffService.countChangedLines(reference, new, sourceFolder = "src/main"),
            "narrowing to src/main leaves the tests out")
    }

    @Test
    fun `changed binary files are counted as one changed line`() {

        // the NUL character is what makes JGit consider these files binary
        val reference = projectFolder("reference")
        writeBinaryFile(reference, "src/pkg/image.png", "\u0000binary\nline 2\n".toByteArray())

        val new = projectFolder("new")
        writeBinaryFile(new, "src/pkg/image.png", "\u0000binary\nline 2\nline 3\n".toByteArray())

        // binary content can't be line-diffed, so JGit handles the whole file as a single line, which means that
        // any change to a binary file weighs exactly one line
        assertEquals(1, sourceDiffService.countChangedLines(reference, new))
    }

    @Test
    fun `identical binary files have no changed lines`() {

        val content = "\u0000binary\nline 2\n".toByteArray()

        val reference = projectFolder("reference")
        writeBinaryFile(reference, "src/pkg/image.png", content)

        val new = projectFolder("new")
        writeBinaryFile(new, "src/pkg/image.png", content)

        assertEquals(0, sourceDiffService.countChangedLines(reference, new))
    }

    @Test
    fun `a binary file that was added counts as one added line`() {

        val reference = projectFolder("reference")
        writeFile(reference, sourcePath, linesOf("line 1", "line 2"))

        val new = projectFolder("new")
        writeFile(new, sourcePath, linesOf("line 1", "line 2"))
        writeBinaryFile(new, "src/pkg/image.png", "\u0000binary\nline 2\nline 3\n".toByteArray())

        assertEquals(1, sourceDiffService.countChangedLines(reference, new))
    }

    @Test
    fun `files outside the src folder are ignored`() {

        val content = linesOf("line 1", "line 2")

        val reference = projectFolder("reference")
        writeFile(reference, sourcePath, content)
        writeFile(reference, "AUTHORS.txt", linesOf("student1;Student 1"))
        writeFile(reference, "README.md", linesOf("some", "instructions"))
        writeFile(reference, "test-files/input.txt", linesOf("1", "2", "3"))

        val new = projectFolder("new")
        writeFile(new, sourcePath, content)
        writeFile(new, "AUTHORS.txt", linesOf("student1;Student 1", "student2;Student 2"))
        writeFile(new, "README.md", linesOf("completely", "different", "instructions"))

        assertEquals(0, sourceDiffService.countChangedLines(reference, new))
    }

    @Test
    fun `a project without a src folder counts all the source lines of the other one`() {

        val reference = projectFolder("reference")
        writeFile(reference, sourcePath, linesOf("line 1", "line 2", "line 3"))

        val newWithoutSources = projectFolder("newWithoutSources")
        writeFile(newWithoutSources, "AUTHORS.txt", linesOf("student1;Student 1"))

        assertEquals(3, sourceDiffService.countChangedLines(reference, newWithoutSources))
        assertEquals(3, sourceDiffService.countChangedLines(newWithoutSources, reference))
    }

    @Test
    fun `two projects without a src folder have no changed lines`() {

        val reference = projectFolder("reference")
        writeFile(reference, "AUTHORS.txt", linesOf("student1;Student 1"))

        val new = projectFolder("new")
        writeFile(new, "AUTHORS.txt", linesOf("student1;Student 1", "student2;Student 2"))

        assertEquals(0, sourceDiffService.countChangedLines(reference, new))
    }

    @Test
    fun `files are matched by their path relative to the project folder`() {

        val content = linesOf("line 1", "line 2", "line 3")

        // the same file, in the same place, on two project folders with different names
        val reference = projectFolder("submission-1")
        writeFile(reference, "src/pkg/deeply/nested/Main.java", content)

        val new = projectFolder("another-submission-with-a-different-name")
        writeFile(new, "src/pkg/deeply/nested/Main.java", content)

        assertEquals(0, sourceDiffService.countChangedLines(reference, new))
    }

    @Test
    fun `a moved file counts as a removal plus an addition`() {

        val content = linesOf("line 1", "line 2", "line 3")

        val reference = projectFolder("reference")
        writeFile(reference, "src/pkg/Main.java", content)

        val new = projectFolder("new")
        writeFile(new, "src/anotherpkg/Main.java", content)

        // renames are a known limitation: the file is counted as fully removed and fully added
        assertEquals(6, sourceDiffService.countChangedLines(reference, new))
    }
}
