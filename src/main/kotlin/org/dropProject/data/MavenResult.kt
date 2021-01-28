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

import org.dropProject.Constants

/**
 * Represents the Output of a Maven build process.
 *
 * @property resultCode is an Int
 * @property outputLines is a List of String
 * @property expiredByTimeout is a Boolean
 */
data class MavenResult(val resultCode : Int,
                       val outputLines: List<String>,
                       var expiredByTimeout : Boolean = false) {
    fun tooMuchOutput() = outputLines.size >= Constants.TOO_MUCH_OUTPUT_THRESHOLD
}
