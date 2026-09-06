/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2026 Pedro Alves
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

import org.eclipse.jgit.diff.HistogramDiff
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File

/**
 * Compares the source code of two project folders, counting how many lines differ between them.
 *
 * This is used to measure how much a submission diverges from the code that the same group had already submitted to
 * another assignment (see [org.dropproject.dao.Assignment.baseAssignmentId]). The comparison is done directly on the
 * files, using JGit's diff algorithm, without needing a git repository.
 *
 * Blank lines never count, wherever they are added, removed or moved to: reformatting is not the kind of change that
 * this is meant to measure.
 *
 * Known limitations: renames are not detected (a file that was moved and edited counts as a full deletion plus a full
 * addition) and binary files weigh a single line, since JGit collapses content with NUL bytes into one line. Also,
 * differences that are only in the line terminators or in trailing whitespace are not counted as changes, otherwise
 * every line of every file would be flagged as modified for a student that unzips or edits the code on Windows.
 */
@Service
class SourceDiffService {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    /**
     * Counts the number of source lines that differ between two project folders. Only the contents of [sourceFolder]
     * are considered, since everything else (AUTHORS.txt, README.md, test-files, ...) is not code.
     *
     * @param referenceFolder is the File with the project folder that is used as the reference (the "old" version)
     * @param newFolder is the File with the project folder that is being compared with the reference
     * @param sourceFolder is the path, relative to each project folder, holding the code to compare. The default
     * covers the submitted projects; comparing two teachers' repositories instead means passing "src/main", so that
     * their unit tests, which the students never get, are left out
     *
     * @return an Int with the total number of added, removed and modified lines, blank lines excluded
     */
    fun countChangedLines(referenceFolder: File, newFolder: File, sourceFolder: String = "src"): Int {

        val referenceFiles = sourceFilesByRelativePath(referenceFolder, sourceFolder)
        val newFiles = sourceFilesByRelativePath(newFolder, sourceFolder)

        var changedLines = 0
        for (relativePath in referenceFiles.keys + newFiles.keys) {
            val referenceFile = referenceFiles[relativePath]
            val newFile = newFiles[relativePath]
            changedLines += when {
                referenceFile == null -> countLines(newFile!!)  // the file was added
                newFile == null -> countLines(referenceFile)    // the file was removed
                else -> countChangedLinesInFile(referenceFile, newFile)
            }
        }

        LOG.debug("${changedLines} changed line(s) between ${referenceFolder} and ${newFolder}")

        return changedLines
    }

    /**
     * Indexes all the files inside [sourcePath] of [projectFolder] by their path relative to [projectFolder],
     * which is what makes it possible to match each file with its counterpart on the other side of the comparison.
     */
    private fun sourceFilesByRelativePath(projectFolder: File, sourcePath: String): Map<String, File> {
        val sourceFolder = File(projectFolder, sourcePath)
        if (!sourceFolder.exists()) {
            return emptyMap()
        }

        return sourceFolder.walkTopDown()
            .filter { it.isFile }
            .associateBy { it.relativeTo(projectFolder).invariantSeparatorsPath }
    }

    /**
     * Counts the number of lines that differ between two versions of the same file. A binary file (any file with a
     * NUL byte) is handled by JGit as a single line, so any change to one of those weighs exactly one line.
     */
    private fun countChangedLinesInFile(referenceFile: File, newFile: File): Int {

        val referenceBytes = referenceFile.readBytes()
        val newBytes = newFile.readBytes()

        if (referenceBytes.contentEquals(newBytes)) {
            return 0
        }

        val referenceText = RawText(referenceBytes)
        val newText = RawText(newBytes)

        // WS_IGNORE_TRAILING makes a line that only differs in its terminator (LF vs CRLF) or in trailing
        // whitespace compare as unchanged
        return HistogramDiff().diff(RawTextComparator.WS_IGNORE_TRAILING, referenceText, newText)
            .sumOf {
                maxOf(countNonBlankLines(referenceText, it.beginA, it.endA),
                      countNonBlankLines(newText, it.beginB, it.endB))
            }
    }

    /**
     * Counts the number of lines of a whole file. Note that a binary file always counts as a single line, since
     * that's how JGit represents content that has NUL bytes.
     */
    private fun countLines(file: File): Int {
        val text = RawText(file.readBytes())
        return countNonBlankLines(text, 0, text.size())
    }

    /**
     * The number of lines of [text], between [begin] (inclusive) and [end] (exclusive), that are not blank. Blank
     * lines are skipped everywhere, so that adding, removing or moving them doesn't count as changing the code.
     */
    private fun countNonBlankLines(text: RawText, begin: Int, end: Int) =
        (begin until end).count { text.getString(it).isNotBlank() }
}
