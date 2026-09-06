/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2021 Pedro Alves
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
package org.dropproject.data

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Represents the response after a submission. It will be converted to JSON
 *
 * @property submissionId identifies the submission that was accepted
 * @property error describes why the submission was refused
 * @property redirectTo is the page to send the submitter to, relative to the context path, when it shouldn't be the
 * build report of the submission. Used by the first phase of a defense assignment, where the submission is just a
 * checkpoint of the code the student had already submitted elsewhere, so its report has nothing to tell them
 */
@JsonInclude(JsonInclude.Include.NON_NULL)  // exclude nulls fields from serialization
class SubmissionResult(val submissionId: Long? = null, val error: String? = null,
                       val redirectTo: String? = null) {

    init {
        if (submissionId == null && error == null) {
            throw Exception("You must set at least one of [submissionId, error]")
        }

        if (submissionId != null && error != null) {
            throw Exception("You can't set both [submissionId, error]")
        }
    }

}
