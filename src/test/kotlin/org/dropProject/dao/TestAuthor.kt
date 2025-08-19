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
package org.dropproject.dao

import junit.framework.TestCase.assertEquals
import org.junit.Test

class TestAuthor {

    @Test
    fun testAuthorConstructor() {
        var projGroup = ProjectGroup(1)
        var author = Author("BC", "1983", projGroup)
        assertEquals("BC", author.name)
        assertEquals("1983", author.userId)
        assertEquals(projGroup, author.group)
    }

    @Test
    fun testAuthorWithSpacesInName() {
        var author = Author(0, " Pedro   ", "1234")
        assertEquals("Pedro", author.name)
        assertEquals("1234", author.userId)
    }

}
