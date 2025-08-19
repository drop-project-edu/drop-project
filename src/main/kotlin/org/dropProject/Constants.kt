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
package org.dropproject

object Constants {
    const val TEST_NAME_PREFIX = "Test"
    const val TEACHER_TEST_NAME_PREFIX = "TestTeacher"
    const val TEACHER_HIDDEN_TEST_NAME_PREFIX = "TestTeacherHidden"

    const val DEFAULT_MAX_MEMORY_MB = 512  // max memory (in Mb) that is given to each submission to run their tests

    const val COOLOFF_FOR_STRUCTURE_OR_COMPILATION = 2 // minutes

    const val TOO_MUCH_OUTPUT_THRESHOLD = 2500 // more than 2500 println's is too much

    const val CACHE_ARCHIVED_ASSIGNMENTS_KEY = "archivedAssignmentsCache"

    const val SIMILARITY_THRESHOLD = 0.5  // minimum similarity to consider as plagiarism (0.0 .. 1.0)
}
    
    
