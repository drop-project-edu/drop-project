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
 * Represents the author of a submission (a student or a teacher).
 *
 * @property id is a primary-key like generated value
 * @property name is a String, representing the author's name
 * @property userId is a String, representing the author's id (e.g. student number, teacher number)
 */
@Entity
data class Author(
        @Id @GeneratedValue
        val id: Long = 0,
        val name: String,
        val userId: String) {

    @ManyToOne
    lateinit var group: ProjectGroup

    constructor(name: String, number: String, group: ProjectGroup) : this(name = name, userId = number) {
        this.group = group
    }
}
