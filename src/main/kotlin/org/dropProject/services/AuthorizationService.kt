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

import org.dropproject.extensions.realName
import org.dropproject.repository.AssignmentACLRepository
import org.dropproject.repository.AssignmentRepository
import org.dropproject.repository.GitSubmissionRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * The outcome of checking if a user may access an [org.dropproject.dao.Assignment].
 */
enum class AssignmentAccess {
    /** the user may access the assignment */
    GRANTED,

    /** the user is one of the assignment's intended users, but it is not open to submissions */
    NOT_ACTIVE,

    /** the assignment is not for this user */
    DENIED
}

@Service
class AuthorizationService(
    private val assignmentRepository: AssignmentRepository,
    private val assignmentACLRepository: AssignmentACLRepository,
    private val assignmentService: AssignmentService,
    private val gitSubmissionRepository: GitSubmissionRepository
) {

    fun isOwnerOrACL(assignmentId: String, authentication: Authentication): Boolean {
        val userId = (authentication as java.security.Principal).realName()
        val assignment = assignmentRepository.findById(assignmentId).orElse(null) ?: return false
        return assignment.ownerUserId == userId ||
            assignmentACLRepository.existsByAssignmentIdAndUserId(assignmentId, userId)
    }

    /**
     * Decides if [userId] may access the assignment identified by [assignmentId], distinguishing the two reasons for
     * a refusal, so that the caller can tell the user which one it was.
     *
     * @param assignmentId is a String identifying the Assignment
     * @param userId is a String identifying the user trying to access it
     * @param isTeacher is a Boolean, true if the user has the teacher role
     * @return [AssignmentAccess.GRANTED], [AssignmentAccess.NOT_ACTIVE] if the user would otherwise be allowed but
     * the assignment is closed to submissions, or [AssignmentAccess.DENIED] if the assignment is not for this user
     */
    fun checkAssignmentAccess(assignmentId: String, userId: String, isTeacher: Boolean): AssignmentAccess {
        val assignment = assignmentRepository.findById(assignmentId)
            .orElseThrow { throw ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found") }

        val allowedWhenActive = isTeacher || try {
            assignmentService.checkAssignees(assignmentId, userId)
            true
        } catch (e: Exception) {
            false
        }

        if (!allowedWhenActive) {
            return AssignmentAccess.DENIED
        }

        if (!assignment.active) {
            // the teachers that manage it can still see it while it is closed, since they are the ones who open it
            val acl = assignmentACLRepository.findByAssignmentId(assignmentId)
            val managesIt = isTeacher && (userId == assignment.ownerUserId || acl.any { it.userId == userId })
            return if (managesIt) AssignmentAccess.GRANTED else AssignmentAccess.NOT_ACTIVE
        }

        return AssignmentAccess.GRANTED
    }

    fun canAccessAssignment(assignmentId: String, userId: String, isTeacher: Boolean): Boolean =
        checkAssignmentAccess(assignmentId, userId, isTeacher) == AssignmentAccess.GRANTED

    fun canAccessAssignmentByGitSubmissionId(gitSubmissionId: String, userId: String, isTeacher: Boolean): Boolean {

        val gitSubmission = gitSubmissionRepository.findById(gitSubmissionId.toLong())
            .orElseThrow { throw ResponseStatusException(HttpStatus.NOT_FOUND, "Git Submission ${gitSubmissionId} not found") }
        return canAccessAssignment(gitSubmission.assignmentId, userId, isTeacher)
    }
}