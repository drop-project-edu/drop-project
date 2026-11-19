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

import jakarta.servlet.http.HttpServletRequest
import org.dropproject.dao.Assignment
import org.dropproject.repository.AssignmentACLRepository
import org.springframework.stereotype.Service

/**
 * Decides which teachers manage an [Assignment]. It only depends on repositories, so that any service can use it,
 * including the ones that [AssignmentService] itself depends on.
 */
@Service
class TeacherAuthorizationService(
    private val assignmentACLRepository: AssignmentACLRepository
) {

    /**
     * Checks if the user identified by [principalName] is a teacher that manages [assignment], that is, a teacher
     * who owns it or is on its access control list.
     *
     * @param request is the HttpServletRequest, used to check the user's teacher role
     */
    fun isAuthorizedTeacher(assignment: Assignment, principalName: String, request: HttpServletRequest): Boolean {
        return request.isUserInRole("TEACHER") &&
                (assignment.ownerUserId == principalName ||
                        assignmentACLRepository.existsByAssignmentIdAndUserId(assignment.id, principalName))
    }
}
