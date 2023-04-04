/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2022 Pedro Alves
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

import de.jplag.JPlag
import de.jplag.options.JPlagOptions
import de.jplag.reporting.reportobject.ReportObjectFactory
import org.apache.commons.io.FileUtils
import org.dropProject.Constants
import org.dropProject.dao.Assignment
import org.dropProject.dao.Submission
import org.dropProject.repository.SubmissionRepository
import org.springframework.stereotype.Service
import java.io.File
import kotlin.math.round

/**
 * Representation of a plagiarism checking result, regardless of the engine used
 * (i.e., not tied to JPlag for example)
 */
data class PlagiarismComparison(
    val matchId: Int,  // sequential id that will be used to show the details of the comparison
    val firstSubmission: Submission,
    val secondSubmission: Submission,
    val similarityPercentage: Int,
    var firstNumTries: Int = -1, // how many submissions did the first group
    var secondNumTries: Int = -1
)

data class PlagiarismResult(
    val comparisons: List<PlagiarismComparison>,
    val ignoredSubmissions: List<String>
)

@Service
class JPlagService(private val submissionService: SubmissionService,
                   private val submissionRepository: SubmissionRepository) {

    var reportFoldersByAssignmentId = mutableMapOf<String,File>()

    /**
     * Copies to baseFolder all the submissions that will be checked for plagiarism, in the
     * appropriate folder structure for this tool
     */
    fun prepareSubmissions(submissions: List<Submission>, baseFolder: File) {
        for (submission in submissions) {
            val originalProjectFolder = submissionService.getOriginalProjectFolder(submission)
            val submissionName = submission.group.authorsStr()
            FileUtils.copyDirectoryToDirectory(File(originalProjectFolder, "src"), File(baseFolder, submissionName))
        }
    }

    /**
     * Runs JPlag over the submissions in baseFolder (previously prepared, see prepareSubmissions) and
     * returns the result as a list of [PlagiarismComparison].
     * Also, it produces an HTML report into the outputReportFolder. This will be needed to show individual
     * reports for each match (showing the code side by side)
     * Finally, it associates the assignmentId with the outputReportFolder in a local variable for future reference
     */
    fun checkSubmissions(baseFolder: File, assignment: Assignment, outputReportFolder: File): PlagiarismResult {

        val language = when (assignment.language) {
            org.dropProject.dao.Language.JAVA -> de.jplag.java.Language()
            org.dropProject.dao.Language.KOTLIN -> de.jplag.kotlin.Language()
        }
        val options = JPlagOptions(language, mutableSetOf(baseFolder), setOf())
            .withSimilarityThreshold(Constants.SIMILARITY_THRESHOLD)  // ignore comparisons below this similarity
        val jplag = JPlag(options)
        val result = jplag.run()

        val jplagComparisons = result.allComparisons

        val invalidSubmissions = result.submissions.invalidSubmissions.map { it.name }

        val reportObjectFactory = ReportObjectFactory()
        reportObjectFactory.createAndSaveReport(result, outputReportFolder.absolutePath)

        reportFoldersByAssignmentId[assignment.id] = outputReportFolder

        val submissions = submissionRepository.findByAssignmentId(assignment.id)
        val submissionsByAuthorStr = submissions.associateBy(
            keySelector = { it.group.authorsStr() },
            valueTransform = { it }
        )

        val comparisons = jplagComparisons.mapIndexed { idx, jplagComparison ->
            PlagiarismComparison(idx,
                submissionsByAuthorStr[jplagComparison.firstSubmission.name] ?: throw IllegalArgumentException("No submission"),
                submissionsByAuthorStr[jplagComparison.secondSubmission.name] ?: throw IllegalArgumentException("No submission"),
                round(jplagComparison.similarity() * 100).toInt())
        }

        return PlagiarismResult(comparisons, invalidSubmissions)
    }
}
