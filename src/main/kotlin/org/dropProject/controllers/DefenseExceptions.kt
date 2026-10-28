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
