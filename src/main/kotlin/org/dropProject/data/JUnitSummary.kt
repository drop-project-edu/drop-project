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
package org.dropProject.data

/**
 * Represents the summary of execution of a set of JUnit Tests.
 *
 * @property numTests is an Int
 * @property numFailures is an Int representing the number of failed tests
 * @property numErrors is an Int representing the number of tests that resulted in a runtime error
 * (e.g. "ArrayIndexOutOfBoundsException")
 * @property numSkipped is an Int
 * @property ellapsed is a Float representing the time that the execution of the tests took
 */
data class JUnitSummary(
        val numTests: Int,
        val numFailures: Int,
        val numErrors: Int,
        val numSkipped: Int,
        val ellapsed: Float,
        val numMandatoryOK: Int
) {

    val progress: Int
        get() {
            return numTests - (numErrors + numFailures)
        }

    fun toStr(): String {
        return "${progress}/${numTests}"
    }
}
