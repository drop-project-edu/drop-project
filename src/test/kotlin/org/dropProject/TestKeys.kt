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
 * SSH keys used by the test fixtures to access the sample repositories on github
 * (drop-project-edu/sampleJavaAssignment, drop-project-edu/sampleJavaSubmission, etc).
 *
 * Each key is resolved lazily (tests that don't touch github run without any keys), in this order:
 *  1. environment variable or the git-ignored .env file, via [DotEnv] (CI uses env variables
 *     fed from github actions secrets; developers use .env - see .env.example)
 *  2. for the public keys only, the committed files in src/test/resources/testKeys
 *
 * The private keys must NEVER be committed, in any format - github detects published
 * private keys and revokes them.
 */
object TestKeys {
    val sampleJavaAssignmentPrivateKey: String by lazy {
        load("DP_SAMPLE_JAVA_ASSIGNMENT_PRIVATE_KEY")
    }
    val sampleJavaAssignmentPublicKey: String by lazy {
        load("DP_SAMPLE_JAVA_ASSIGNMENT_PUBLIC_KEY", "sampleJavaAssignment_id_rsa.pub")
    }
    val sampleJavaSubmissionPrivateKey: String by lazy {
        load("DP_SAMPLE_JAVA_SUBMISSION_PRIVATE_KEY")
    }
    val sampleJavaSubmissionPublicKey: String by lazy {
        load("DP_SAMPLE_JAVA_SUBMISSION_PUBLIC_KEY", "sampleJavaSubmission_id_rsa.pub")
    }

    private fun load(variable: String, resourceFilename: String? = null): String {
        DotEnv.resolve(variable)?.let { return it }
        resourceFilename?.let { filename ->
            TestKeys::class.java.getResource("/testKeys/$filename")?.let { return it.readText() }
        }
        throw IllegalStateException("Missing test ssh key $variable: copy .env.example to .env " +
                "and follow the instructions there")
    }
}
