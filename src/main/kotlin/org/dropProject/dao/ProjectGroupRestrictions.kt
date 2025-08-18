/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2024 Pedro Alves
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

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate

@Entity
data class ProjectGroupRestrictions(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    var minGroupSize: Int = 1,
    var maxGroupSize: Int? = null,

    @Column(length = 1000)
    var exceptions: String? = null  // comma separated list of users that are exempt from the restrictions
) {

    @PrePersist
    @PreUpdate
    fun prePersist() {
        exceptions = exceptions?.replace(" ", "")?.replace("\n", "")?.replace("\r", "")
    }

    fun exceptionsAsList(): List<String> {
        if (exceptions.isNullOrBlank()) {
            return emptyList()
        }
        return exceptions!!.split(",").sorted()
    }
}
