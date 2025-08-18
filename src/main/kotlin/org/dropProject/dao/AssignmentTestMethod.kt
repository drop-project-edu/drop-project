/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2020 Pedro Alves
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

import jakarta.persistence.*

/**
 * Represents an [Assignment]'s JUnit test method/function.
 *
 * @property id is a primary-key like generated value
 * @property assignmentId is a String, identifying the assignment
 * @property testClass is a String with the name of the test class
 * @property testMethod is a String with the name of the method
 */
@Entity
data class AssignmentTestMethod(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long = 0,

        @ManyToOne
        @JoinColumn(name = "assignment_id", nullable = false)
        val assignment: Assignment,

        val testClass: String,
        val testMethod: String
) {
        override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as AssignmentTestMethod

                return id == other.id
        }

        override fun hashCode() = id.hashCode()

}
