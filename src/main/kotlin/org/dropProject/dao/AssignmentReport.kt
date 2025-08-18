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

import org.dropProject.services.AssignmentValidator
import jakarta.persistence.*

/**
 * Represents a report of information that was generated during an [Assignment]'s validation. This information will be
 * listed on the Assignments "Validation Report" page.
 *
 * @property id is a primary-key like generated value
 * @property assignmentId is a String with the relevant Assignment's ID
 * @property type is the type of the report (e.g. Warning)
 * @property message is a short description of the problem (note that some problems only result in this message)
 * @property description is the long description of the problem
 */
@Entity
data class AssignmentReport(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long = 0,

        @Column(nullable = false, length = 50)
        val assignmentId: String,  // assignment.id

        @Column(nullable = false)
        val type: AssignmentValidator.InfoType,

        @Column(nullable = false)
        val message: String,

        @Column(columnDefinition = "TEXT")
        val description: String?
) {

    fun typeIcon(): String {
        return when (type) {
            AssignmentValidator.InfoType.ERROR -> "error.png"
            AssignmentValidator.InfoType.WARNING -> "warn.png"
            AssignmentValidator.InfoType.INFO -> "info.png"
        }
    }
}
