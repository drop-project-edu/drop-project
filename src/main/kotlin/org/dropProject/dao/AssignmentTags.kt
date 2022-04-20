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
package org.dropProject.dao

import java.io.Serializable
import javax.persistence.*

@Embeddable
data class AssignmentTagsCompositeKey(
    val assignmentId: String,
    val tagId: Long): Serializable

/**
 * Represents an associate table between assignments and assignmentTags. Due to problems with spring "magic", i've decided to implement
 * this relation by hand
 *
 * @property assignmentId .
 * @property tagId .
 */
@Entity
@IdClass(AssignmentTagsCompositeKey::class)
data class AssignmentTags(
    @Id val assignmentId: String,
    @Id val tagId: Long
)
