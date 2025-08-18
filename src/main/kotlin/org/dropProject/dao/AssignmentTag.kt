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

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*

/**
 * Represents a "tag" used to categorize [Assignment]s. It is mostly used for filtering purposes. It might take any
 * value (e.g. the name of the course, the year, the type of evaluation, etc.).
 *
 * @property id is a primary-key like generated value.
 * @property name is a String, representing the name of the tag.
 * @property selected is a Boolean, indicating if this tag has been selected in a current filter.
 */
@Entity
data class AssignmentTag(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(unique = true)
    val name: String,  // tag name

    @Transient
    var selected: Boolean = false,  // used for the filter in the assignments' list

    @ManyToMany(mappedBy = "tags")
    @JsonIgnore // avoid recursion in APIs
    var assignments: MutableSet<Assignment> = mutableSetOf()
) {
    override fun equals(other: Any?) = other is AssignmentTag && id == other.id
    override fun hashCode() = id.hashCode()
    override fun toString(): String = "AssignmentTag(id=$id, name=$name)"
}
