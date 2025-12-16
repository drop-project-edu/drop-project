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
package org.dropproject.data

import junit.framework.TestCase.assertEquals
import org.dropproject.dao.Author
import org.dropproject.dao.ProjectGroup
import org.junit.Test

class TestProjectGroup {

    @Test
    fun testAssignmentStatistics() {

        val pGroup1 = ProjectGroup(1)
        pGroup1.authors.add(Author(1, "BC", "1983"))

        val pGroup2 = ProjectGroup(2)
        pGroup2.authors.add(Author(2, "Vader, D. Vader", "42"))

        val projGroups = mutableListOf<ProjectGroup>(pGroup1, pGroup2)
        val failedTestNames = mutableListOf<String>("test001", "test002", "test003")
        val gpg = GroupedProjectGroups(projGroups, failedTestNames)

        assertEquals(2, gpg.getGroupIDs().size)
        assert(gpg.getGroupIDs().contains(1))
        assert(gpg.getGroupIDs().contains(2))
        assertEquals(3, gpg.showNrOfFailedTests())
        assertEquals(2, gpg.getGroupMembers().size)

    }

}
