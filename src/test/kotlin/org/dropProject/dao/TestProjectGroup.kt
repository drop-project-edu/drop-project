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
package org.dropProject.dao

import junit.framework.TestCase.*
import org.junit.Test

class TestProjectGroup {

    @Test
    fun projectGroup() {
        var projectGroup = ProjectGroup(1)
        projectGroup.authors.add(Author(1, "BC", "1983"))
        assertTrue(projectGroup.contains("1983"))
        assertFalse(projectGroup.contains("1143"))
    }

}
