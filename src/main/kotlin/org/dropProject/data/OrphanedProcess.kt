/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2026 Pedro Alves
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

/**
 * A Maven (or forked Surefire) OS process still running for longer than the configured async timeout - i.e. one
 * that DP's own watchdog should already have terminated, but didn't.
 *
 * Identified by the "-DdropProject.submissionId=" system property [org.dropproject.services.MavenInvoker] tags
 * onto both the top-level Maven process and the forked Surefire JVM it starts, so this always resolves to a real
 * submission id, regardless of whether it came from an upload or a git submission.
 *
 * @property pid the OS process id
 * @property runningForSeconds how long the process has been running
 * @property submissionId the [org.dropproject.dao.Submission] this process is running for
 */
data class OrphanedProcess(
    val pid: Long,
    val runningForSeconds: Long,
    val submissionId: Long
)
