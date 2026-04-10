/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2025 Pedro Alves
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
 * Holds disk usage statistics for a single assignment, combining file-system
 * space (submissions and mavenized folders) with database space (build_report
 * and junit_report tables).
 */
data class AssignmentDiskUsage(
    val assignmentId: String,
    val assignmentName: String,
    /** Bytes occupied by submission folders (upload + git) on the file system */
    val submissionsSize: Long,
    /** Bytes occupied by mavenized project folders on the file system */
    val mavenizedSize: Long,
    /** Bytes occupied by the junit_report rows in the database */
    val junitReportDbSize: Long,
    /** Bytes occupied by the build_report rows in the database */
    val buildReportDbSize: Long
) {
    val totalSize: Long get() = submissionsSize + mavenizedSize + junitReportDbSize + buildReportDbSize

    val submissionsSizeFormatted: String get() = formatBytes(submissionsSize)
    val mavenizedSizeFormatted: String get() = formatBytes(mavenizedSize)
    val junitReportDbSizeFormatted: String get() = formatBytes(junitReportDbSize)
    val buildReportDbSizeFormatted: String get() = formatBytes(buildReportDbSize)
    val totalSizeFormatted: String get() = formatBytes(totalSize)

    companion object {
        fun formatBytes(bytes: Long): String = when {
            bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024L         -> "%.1f KB".format(bytes / 1_024.0)
            else                    -> "$bytes B"
        }
    }
}