/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2020 Pedro Alves
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

import org.dropProject.dao.Assignment
import org.dropProject.extensions.realName
import org.dropProject.repository.AssigneeRepository
import org.dropProject.repository.AssignmentACLRepository
import org.dropProject.repository.AssignmentRepository
import org.dropProject.repository.SubmissionRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.security.Principal

@Service
class AssignmentService(
        val assignmentRepository: AssignmentRepository,
        val assignmentACLRepository: AssignmentACLRepository,
        val submissionRepository: SubmissionRepository,
        val assigneeRepository: AssigneeRepository
) {

    @Cacheable(
            value = ["archivedAssignmentsCache"],
            key = "#principal.name",
            condition = "#archived==true")
    fun getMyAssignments(principal: Principal, archived: Boolean): List<Assignment> {
        val assignmentsOwns = assignmentRepository.findByOwnerUserId(principal.realName())

        val assignmentsACL = assignmentACLRepository.findByUserId(principal.realName())
        val assignmentsAuthorized = ArrayList<Assignment>()
        for (assignmentACL in assignmentsACL) {
            assignmentsAuthorized.add(assignmentRepository.findById(assignmentACL.assignmentId).get())
        }

        val assignments = ArrayList<Assignment>()
        assignments.addAll(assignmentsOwns)
        assignments.addAll(assignmentsAuthorized)

        val filteredAssigments = assignments.filter { it.archived == archived }

        for (assignment in filteredAssigments) {
            assignment.numSubmissions = submissionRepository.countByAssignmentId(assignment.id).toInt()
            if (assignment.numSubmissions > 0) {
                assignment.lastSubmissionDate = submissionRepository.findFirstByAssignmentIdOrderBySubmissionDateDesc(assignment.id).submissionDate
            }
            assignment.numUniqueSubmitters = submissionRepository.findUniqueSubmittersByAssignmentId(assignment.id).toInt()
            assignment.public = !assigneeRepository.existsByAssignmentId(assignment.id)
        }
        return filteredAssigments
    }
}
