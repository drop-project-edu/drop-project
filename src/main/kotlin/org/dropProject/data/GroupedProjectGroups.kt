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
package org.dropProject.data

import org.dropProject.dao.ProjectGroup

/**
 * Represents student groups that were signalled as failling exactly the same unit tests
 * @property groups is a List of [ProjectGroup]s
 * @property failedTestNames is a List of Strings with the names of the unit tests that the groups are failing
 */
data class GroupedProjectGroups(val groups : List<ProjectGroup>,
                                val failedTestNames: List<String>) {

    fun getGroupIDs(): List<Long> {
        var result = mutableListOf<Long>()
        for(group in groups) {
            result.add(group.id)
        }
        return result
    }

    fun getGroupMembers(): List<String> {
        var result = mutableListOf<String>()
        for(group in groups) {
            result.add("Group: " + group.id + " | " + group.authorsNameStr())
        }
        return result;
    }

    fun showNrOfFailedTests(): Int {
        return failedTestNames.size
    }

    fun getTestNames(): List<String> {
        return failedTestNames;
    }
}
