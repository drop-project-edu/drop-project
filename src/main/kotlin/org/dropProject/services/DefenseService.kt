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

import org.dropproject.dao.Assignment
import org.dropproject.dao.Indicator
import org.dropproject.dao.Submission
import org.dropproject.dao.SubmissionStatus
import org.dropproject.repository.ProjectGroupRepository
import org.dropproject.repository.SubmissionReportRepository
import org.dropproject.repository.SubmissionRepository
import org.springframework.stereotype.Service

/**
 * The state of the first phase of a defense, from the point of view of a certain student.
 *
 * On the first phase, the student has to prove that they can build and submit the exact code that they had already
 * submitted to the linked project assignment, since that is the code that the defense's changes will be made on top
 * of. Only a [VALID] checkpoint lets the student move on to the second phase.
 */
enum class DefenseCheckpointStatus {
    /** the student hasn't submitted their original code yet */
    NONE,
    /** the checkpoint was submitted and is still being built */
    PENDING,
    /** the checkpoint doesn't pass every test of the project assignment, or its build didn't complete */
    FAILED,
    /** the checkpoint is the original code and passes every test, so the student can start the defense */
    VALID
}

/**
 * Contains the rules of the "defense" assignments, that is, the assignments whose submissions are a bounded set of
 * changes on top of the code that the group had already submitted to another (the "project") assignment.
 *
 * A defense runs in two phases and, while the teacher decides when the second phase *may* start (see
 * [Assignment.defenseInstructionsReleased]), each student only actually gets there after passing the first one, so
 * the phase is calculated per student, by [defensePhaseFor].
 */
@Service
class DefenseService(
    val submissionRepository: SubmissionRepository,
    val submissionReportRepository: SubmissionReportRepository,
    val projectGroupRepository: ProjectGroupRepository
) {

    companion object {
        /**
         * The indicators that a checkpoint must have OK to be considered valid. Code quality and the student's own
         * tests are left out on purpose: the student is being asked to prove that the code they submitted to the
         * project assignment builds and works, and neither of those two indicators says anything about that.
         */
        private val REQUIRED_INDICATORS = setOf(Indicator.PROJECT_STRUCTURE, Indicator.COMPILATION,
            Indicator.TEACHER_UNIT_TESTS, Indicator.HIDDEN_UNIT_TESTS)
    }

    /**
     * The phase that [submitterUserId] is on, in the defense [assignment]: 1 while they still have to submit their
     * original code, 2 once the teacher has released the instructions to a student who did it.
     *
     * Teachers are on the second phase from the start, so that they can try the defense out, and see it as the
     * students will, before releasing it to them.
     */
    fun defensePhaseFor(assignment: Assignment, submitterUserId: String, isTeacher: Boolean): Int {
        if (isTeacher) {
            return 2
        }

        if (!assignment.defenseInstructionsReleased) {
            return 1
        }

        // the defense is open, but a student who didn't prove that they can build and submit their own code stays
        // on the first phase: the exercise is to change that code, so there is nothing for them to change yet
        return if (checkpointStatus(assignment, submitterUserId) == DefenseCheckpointStatus.VALID) 2 else 1
    }

    /**
     * The state of the first phase of the defense [assignment] for [submitterUserId].
     *
     * Only the group's *last* checkpoint counts, so that the student is always judged by the code they last
     * submitted, and so that deleting a checkpoint puts the group back on the first phase.
     */
    fun checkpointStatus(assignment: Assignment, submitterUserId: String): DefenseCheckpointStatus {
        val checkpoint = lastCheckpoint(assignment, submitterUserId) ?: return DefenseCheckpointStatus.NONE

        // the teacher looked at this checkpoint and decided that it is the group's code, whatever the tests say.
        // That's the way into the defense for a group whose project submission was already failing tests
        if (checkpoint.defenseCheckpointAccepted) {
            return DefenseCheckpointStatus.VALID
        }

        return when (checkpoint.getStatus()) {
            SubmissionStatus.SUBMITTED,
            SubmissionStatus.SUBMITTED_FOR_REBUILD,
            SubmissionStatus.REBUILDING -> DefenseCheckpointStatus.PENDING

            SubmissionStatus.VALIDATED,
            SubmissionStatus.VALIDATED_REBUILT ->
                if (isTheOriginalCode(checkpoint) && passesEveryTest(checkpoint)) DefenseCheckpointStatus.VALID
                else DefenseCheckpointStatus.FAILED

            // a build that failed, timed out or produced too much output doesn't prove that the code works
            else -> DefenseCheckpointStatus.FAILED
        }
    }

    /**
     * The last submission that [submitterUserId] made on the first phase of the defense [assignment], or null if
     * they haven't made one (or if it was deleted by the teacher).
     */
    fun lastCheckpoint(assignment: Assignment, submitterUserId: String): Submission? {
        val groups = projectGroupRepository.getGroupsForAuthor(submitterUserId)
        if (groups.isEmpty()) {
            return null
        }

        return submissionRepository
            .findByGroupInAndAssignmentIdOrderBySubmissionDateDescStatusDateDesc(groups, assignment.id)
            .firstOrNull { it.defenseCheckpoint && it.getStatus() != SubmissionStatus.DELETED }
    }

    /**
     * Checks that [checkpoint] is exactly the code of the group's submission to the project assignment. Divergent
     * checkpoints are refused at submission time, so this only excludes the ones submitted before that rule existed.
     */
    private fun isTheOriginalCode(checkpoint: Submission) = checkpoint.baseDivergenceLines == 0

    /**
     * Checks that every test of the project assignment passed on [checkpoint].
     */
    private fun passesEveryTest(checkpoint: Submission): Boolean {
        val reportElements = submissionReportRepository.findBySubmissionId(checkpoint.id)

        // a build that produced no teacher tests result (an assignment without tests, or a build that stopped
        // before running them) proves nothing about the code
        if (reportElements.none { it.indicator == Indicator.TEACHER_UNIT_TESTS }) {
            return false
        }

        return reportElements
            .filter { it.indicator in REQUIRED_INDICATORS }
            .all { it.reportValue == "OK" }
    }
}
