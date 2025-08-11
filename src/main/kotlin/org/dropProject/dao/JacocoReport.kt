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

import jakarta.persistence.*

/**
 * Represents a persisted Jacoco report. The Jacoco report contains the result of calculating the code
 * coverage of a [Submission].
 *
 * @property id is a Long with a primary-key like generated id
 * @property submissionId is Long is a Long, identifying the [Submission] that the report is based on
 * @property fileName is a String with the name of the JUnit report file
 * @property csvReport is a String with the CSV version of the report
 */
@Entity
@Table(uniqueConstraints=[UniqueConstraint(columnNames = ["submissionId", "fileName"])], indexes = [Index(columnList = "submissionId")])
data class JacocoReport(
        @Id @GeneratedValue
        val id: Long = 0,

        val submissionId: Long,  // submission.id

        val fileName: String,

        @Column(columnDefinition = "LONGTEXT")  // TODO This is not working, it still creates with type TEXT...
        val csvReport: String
)
