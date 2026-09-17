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
package org.dropproject.controllers

import org.springframework.security.access.AccessDeniedException

/**
 * The exceptions raised by the "defense" assignments, that is, the assignments whose submissions are a bounded set
 * of changes on top of the code that the group had already submitted to another assignment.
 */

/**
 * Represents an Exception that is raised when a submission is made to a defense assignment but the group has no
 * submission to the linked project assignment to compare it with.
 */
class BaseSubmissionNotFoundException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Represents an Exception that is raised when a submission to a defense assignment changes more lines than the
 * assignment allows, relatively to the group's submission to the linked project assignment.
 */
class MaxChangedLinesExceededException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Represents an Exception that is raised when a submission made on the first phase of a defense is not exactly the
 * code that the group submitted to the linked project assignment. On that phase, the student is only supposed to
 * prove that they can build and submit their own code, so anything else is refused.
 */
class DivergentCheckpointException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Thrown when a user who is one of an assignment's intended users tries to open it while it is not active.
 *
 * It is an [AccessDeniedException] so that it is reported exactly like any other denial, through the security chain
 * of the request (see [org.dropproject.controllers.GlobalExceptionHandler]). The difference is that the access denied
 * page recognizes it and explains that the assignment is closed, instead of suggesting that the user asks the teacher
 * for permission that they already have.
 *
 * @property assignmentId is a String identifying the Assignment that is not active
 */
class AssignmentNotActiveException(val assignmentId: String) :
    AccessDeniedException("Assignment ${assignmentId} is not active")
