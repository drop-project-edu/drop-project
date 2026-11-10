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
import org.dropproject.repository.AssignmentRepository
import org.dropproject.repository.JUnitReportRepository
import org.dropproject.repository.SubmissionReportRepository
import org.springframework.stereotype.Service

/**
 * Decides whether a submission is good enough to build upon in an assignment linked to another one (a defense or a
 * continuation): it has to pass the tests that the linked (project) assignment required, i.e. its mandatory tests,
 * if it defines them, or every one of its tests otherwise.
 */
@Service
class RequiredTestsService(
    val assignmentRepository: AssignmentRepository,
    val submissionReportRepository: SubmissionReportRepository,
    val jUnitReportRepository: JUnitReportRepository,
    val junitResultsParser: JunitResultsParser
) {

    companion object {
        /**
         * The indicators that a submission must have OK when the project assignment has no mandatory tests. Code
         * quality and the student's own tests are left out on purpose: the question is whether the code builds and
         * works, and neither of those two indicators says anything about that.
         */
        private val REQUIRED_INDICATORS = setOf(Indicator.PROJECT_STRUCTURE, Indicator.COMPILATION,
            Indicator.TEACHER_UNIT_TESTS, Indicator.HIDDEN_UNIT_TESTS)
    }

    /**
     * The suffix of the mandatory tests of the assignment that [assignment] is linked to (as a defense or as a
     * continuation), or null if that assignment doesn't define one.
     */
    fun baseMandatoryTestsSuffix(assignment: Assignment): String? {
        val baseAssignmentId = assignment.baseAssignmentId ?: return null
        return assignmentRepository.findById(baseAssignmentId).orElse(null)
            ?.mandatoryTestsSuffix?.takeIf { it.isNotEmpty() }
    }

    /**
     * Checks that [submission] passed the tests required by the assignment that [linkedAssignment] is linked to: its
     * mandatory tests, if it defines them, or every one of its tests otherwise.
     *
     * @param submission is the [Submission] to check, whose build has already ended
     * @param submissionAssignment is the [Assignment] that [submission] was made to. It decides which of its tests are
     * the teacher's public ones. It may be [linkedAssignment] itself or the one it is linked to.
     * @param linkedAssignment is the defense or continuation [Assignment] whose base assignment sets the rule
     */
    fun passesTheRequiredTests(submission: Submission, submissionAssignment: Assignment,
                               linkedAssignment: Assignment): Boolean {
        val reportElements = submissionReportRepository.findBySubmissionId(submission.id)

        // a build that produced no teacher tests result (an assignment without tests, or a build that stopped
        // before running them) proves nothing about the code
        if (reportElements.none { it.indicator == Indicator.TEACHER_UNIT_TESTS }) {
            return false
        }

        val mandatoryTests = baseMandatoryTestsSuffix(linkedAssignment)
            ?.let { mandatoryTestResults(submission, submissionAssignment, it) }

        // without mandatory tests, every test counts. That includes a suffix that matches no test, which is a
        // misconfiguration of the project assignment and not a reason to let any code in
        if (mandatoryTests.isNullOrEmpty()) {
            return reportElements
                .filter { it.indicator in REQUIRED_INDICATORS }
                .all { it.reportValue == "OK" }
        }

        // the code still has to build, but the other tests (public or hidden) are allowed to fail, as they were
        // allowed to fail on the project assignment
        val builds = reportElements
            .filter { it.indicator == Indicator.PROJECT_STRUCTURE || it.indicator == Indicator.COMPILATION }
            .all { it.reportValue == "OK" }
        return builds && mandatoryTests.all { it.type == JUnitMethodResultType.SUCCESS }
    }

    /**
     * The results, on [submission], of the public teacher tests whose name ends with [mandatoryTestsSuffix].
     */
    private fun mandatoryTestResults(submission: Submission, submissionAssignment: Assignment,
                                     mandatoryTestsSuffix: String): List<JUnitMethodResult> {
        return jUnitReportRepository.findBySubmissionId(submission.id).orEmpty()
            .map { junitResultsParser.parseXml(it.xmlReport) }
            .filter { it.isTeacherPublic(submissionAssignment) }
            .flatMap { it.junitMethodResults }
            .filter { it.fullMethodName.endsWith(mandatoryTestsSuffix) }
    }
}
