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
package org.dropProject.services

import org.dropProject.repository.AssignmentACLRepository
import org.dropProject.repository.AssignmentRepository
import org.dropProject.repository.GitSubmissionRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AuthorizationService(
    private val assignmentRepository: AssignmentRepository,
    private val assignmentACLRepository: AssignmentACLRepository,
    private val assignmentService: AssignmentService,
    private val gitSubmissionRepository: GitSubmissionRepository
) {

    fun canAccessAssignment(assignmentId: String, userId: String, isTeacher: Boolean): Boolean {
        val assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow { throw ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found") }
        
        if (!isTeacher) {
            if (!assignment.active) {
                return false
            }
            return try {
                assignmentService.checkAssignees(assignmentId, userId)
                true
            } catch (e: Exception) {
                false
            }
        } else {
            if (!assignment.active) {
                val acl = assignmentACLRepository.findByAssignmentId(assignmentId)
                return userId == assignment.ownerUserId || acl.any { it.userId == userId }
            }
            return true
        }
    }

    fun canAccessAssignmentByGitSubmissionId(gitSubmissionId: String, userId: String, isTeacher: Boolean): Boolean {

        val gitSubmission = gitSubmissionRepository.findById(gitSubmissionId.toLong())
            .orElseThrow { throw ResponseStatusException(HttpStatus.NOT_FOUND, "Git Submission ${gitSubmissionId} not found") }
        return canAccessAssignment(gitSubmission.assignmentId, userId, isTeacher)
    }
}