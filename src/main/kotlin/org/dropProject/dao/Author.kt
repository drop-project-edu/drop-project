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
package org.dropproject.dao

import com.fasterxml.jackson.annotation.JsonView
import org.dropproject.data.JSONViews
import jakarta.persistence.*

/**
 * Represents the author of a submission (a student or a teacher).
 *
 * @property id is a primary-key like generated value
 * @property name is a String, representing the author's name. All names are automatically trimmed
 * @property userId is a String, representing the author's id (e.g. student number, teacher number)
 * @property group is a [ProjectGroup], representing the group that the author belongs to
 */
@Entity
class Author(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        @JsonView(JSONViews.TeacherAPI::class)
        val id: Long = 0,
        name: String,
        val userId: String) {

    @JsonView(JSONViews.TeacherAPI::class)
    val name: String = name.trim()

    @ManyToOne
    lateinit var group: ProjectGroup

    /**
     * Creates an Author.
     * @param name is a String
     * @param number is a String
     * @param group is the [ProjectGroup] that the Author belongs to
     */
    constructor(name: String, number: String, group: ProjectGroup) : this(name = name, userId = number) {
        this.group = group
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Author

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }


}
