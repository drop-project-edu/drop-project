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
 * Represents student groups that were signalled as failling exactly the same unit tests.
 *
 * @property groups is a List of [ProjectGroup]s
 * @property failedTestNames is a List of Strings with the names of the unit tests that the groups are failing. Each
 * String is the name of one failed test.
 */
data class GroupedProjectGroups(val groups : List<ProjectGroup>,
                                val failedTestNames: List<String>) {

    /**
     * @return a List of Longs representing the IDs of each group
     */
    fun getGroupIDs(): List<Long> {
        var result = mutableListOf<Long>()
        for(group in groups) {
            result.add(group.id)
        }
        return result
    }

    /**
     * @return a List of Strings representing the names of the authors (i.e. students) in each group
     */
    fun getGroupMembers(): List<String> {
        var result = mutableListOf<String>()
        for(group in groups) {
            result.add("Group: " + group.id + " | " + group.authorsNameStr())
        }
        return result;
    }

    /**
     * @return an Int with the number of failed tests
     */
    fun showNrOfFailedTests(): Int {
        return failedTestNames.size
    }

    /**
     * @return a List of Strings with the names of tests that are failed by the groups
     */
    fun getTestNames(): List<String> {
        return failedTestNames;
    }
}
