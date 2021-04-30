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

import javax.persistence.*

/**
 * Represents an Assignee of an [Assignment]. Some assignments are private in the sense that they can only be accessed
 * by students that were given a certain permission. This "role" of Assignee represents that permission.
 *
 * @property id is primary-key like generated value
 * @property assignmentId is a String, identifying the Assignment
 * @property authorUserId is a String, corresponding to the author's user id (e.g. student number)
 */
@Entity
@Table(uniqueConstraints=[UniqueConstraint(columnNames = ["assignmentId", "authorUserId"])])
data class Assignee(
        @Id @GeneratedValue
        val id: Long = 0,

        val assignmentId: String,
        val authorUserId: String
)
