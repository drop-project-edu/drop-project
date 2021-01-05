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
package org.dropProject.data

/**
 * Represents one of the authors of a [Submission].
 * Note that the author might not be the submitter (e.g. if a group has 2 elements, both will be authors, but only one
 * of them will be the submitter).
 *
 * @property name is a String
 * @property number is a String
 * @property submitter is a Boolean which will be true if the author object was the one performing the submission
 */
data class AuthorDetails(val name: String, val number: String, val submitter: Boolean = false) {
    override fun toString(): String {
        return "${number}-${name}"
    }
}
