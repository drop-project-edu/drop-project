/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2021 Pedro Alves
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
package org.dropProject.services

import org.dropProject.dao.Assignment
import org.dropProject.dao.Author
import org.dropProject.dao.ProjectGroup
import org.dropProject.data.AuthorDetails
import org.dropProject.repository.AuthorRepository
import org.dropProject.repository.ProjectGroupRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * ProjectGroupService provides [ProjectGroup] related functionality.
 */
@Service
class ProjectGroupService(val projectGroupRepository: ProjectGroupRepository,
                            val authorRepository: AuthorRepository) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    /**
     * Searches for a [ProjectGroup] object that contains the authors described by [authors]. If none is found, then a
     * new ProjectGroup is created.
     *
     * @param authors is a List of [AuthorDetail]s.
     *
     * @return a [ProjectGroup]
     */
     fun searchExistingProjectGroupOrCreateNew(authors: List<AuthorDetails>): ProjectGroup {
        val groups = projectGroupRepository.getGroupsForAuthor(authors.first().number)
        for (group in groups) {
            if (group.authors.size == authors.size &&
                group.authors.map { it.userId }.containsAll(authors.map { it.number })) {
                // it's the same group
                return group
            }
        }
        return ProjectGroup()
    }

    /**
     * Searches for a [ProjectGroup] that has a specific set of Authors. If no ProjectGroup is found,
     * creates a new one.
     *
     * @param authors is a List of [AuthorDetails]
     *
     * @return a ProjectGroup
     */
     fun getOrCreateProjectGroup(authors: List<AuthorDetails>): ProjectGroup {
        val group = searchExistingProjectGroupOrCreateNew(authors)
        if (group.authors.isEmpty()) { // new group
            projectGroupRepository.save(group)

            for (authorDetails in authors) {
                val authorDB = Author(name = authorDetails.name, userId = authorDetails.number)
                authorDB.group = group
                authorRepository.save(authorDB)
            }
            LOG.info("New group created with students ${authors.joinToString(separator = "|")}")
        } else {
            LOG.info("Group already exists for students ${authors.joinToString(separator = "|")}")
        }
        return group
    }
}
