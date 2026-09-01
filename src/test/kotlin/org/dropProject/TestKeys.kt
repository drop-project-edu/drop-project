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

/**
 * SSH deploy keys used by the test fixtures to access the sample repositories on github
 * (drop-project-edu/sampleJavaAssignment and drop-project-edu/sampleJavaSubmission).
 * The actual keys live in src/test/resources/testKeys.
 */
object TestKeys {
    val sampleJavaAssignmentPrivateKey = read("sampleJavaAssignment_id_rsa")
    val sampleJavaAssignmentPublicKey = read("sampleJavaAssignment_id_rsa.pub")
    val sampleJavaSubmissionPrivateKey = read("sampleJavaSubmission_id_rsa")
    val sampleJavaSubmissionPublicKey = read("sampleJavaSubmission_id_rsa.pub")

    private fun read(filename: String) =
        TestKeys::class.java.getResource("/testKeys/$filename")!!.readText()
}
