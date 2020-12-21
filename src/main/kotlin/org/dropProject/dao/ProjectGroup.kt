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

import java.util.HashSet
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.OneToMany

/**
 * Represents a set of [Author]s (for example, students) that interact with Drop Project as a group.
 *
 * @property id is a primary-key like generated value
 * @property authors is a [MutableSet] containing the [Author]s that are part of the group
 * @property submissions is a [MutableSet] containing the [Submission]s done by the group
 */
@Entity
data class ProjectGroup(
        @Id @GeneratedValue
        val id: Long = 0
){
    @OneToMany(mappedBy = "group")
    val authors: MutableSet<Author> = HashSet()

    @OneToMany(mappedBy = "group")
    val submissions: MutableSet<Submission> = HashSet()

    fun authorsStr(separator: String = ","): String {
        return authors
                .map { it -> it.userId }
                .sortedBy { it }
                .joinToString(separator = separator)
    }

    fun authorsNameStr(): String {
        return authors
                .sortedBy { it.userId }
                .map { it -> it.name }
                .joinToString(separator = ",")
    }

    fun contains(authorName: String) : Boolean {
        return authors
                .map { it -> it.userId }
                .contains(authorName)
    }

    override fun equals(other: Any?): Boolean {
        val otherProjectGroup = other as ProjectGroup
        return authorsStr().equals(otherProjectGroup.authorsStr())
    }

    override fun hashCode(): Int {
        return authorsStr().hashCode()
    }


}
