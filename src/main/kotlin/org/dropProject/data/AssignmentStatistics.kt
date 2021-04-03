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
package org.dropProject.data

/**
 * Represents statistics about the submissions done for an [Assignment].
 *
 * @property average is a Double, representing the average number of submissions
 * @property standardDeviation is a Double, representing the standard deviation of the average number of submissions
 * @property groupsConsideredForStatistics is a List with the [GroupSubmissionStatistics] that were considered when
 * calculating the statistics
 */
data class AssignmentStatistics(val average : Double,
                                val standardDeviation : Double,
                                val groupsConsideredForStatistics : List<GroupSubmissionStatistics>) {

    /**
     * Identifies [ProjectGroup]'s that have a result that is outside the norm (e.g. they pass "many tests" while having
     * "little submissions" when compared with the other groups.
     *
     * @return is a List of [GroupSubmissionStatistics]
     */
    fun identifyGroupsOutsideStatisticalNorms(): List<GroupSubmissionStatistics> {
        val minSubmissions = average - standardDeviation
        var result = mutableListOf<GroupSubmissionStatistics>()
        for(subStats in groupsConsideredForStatistics) {
            if(subStats.nrSubmissions < minSubmissions) {
                result.add(subStats)
            }
        }
        return result
    }
}

/**
 * Represents the submission statistics for a certain [ProjectGroup].
 *
 * @property groupID is a Long, identifying the ProjectGroup
 * @property nrPassedTests is an Int, representing how many tests the group's (last) submission passes
 * @property nrSubmissions is an Int, representing the number of submissions that the group performed
 */
data class GroupSubmissionStatistics(val groupID : Long,
                                     val nrPassedTests : Int,
                                     val nrSubmissions : Int) {

    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(other?.javaClass != this.javaClass) return false
        other as GroupSubmissionStatistics
        if(groupID == other.groupID && nrPassedTests == other.nrPassedTests && nrSubmissions == other.nrSubmissions) {
            return true;
        }
        return false;
    }
}

/**
 * Computes statistics for an [Assignment], considering the statistics about each [ProjectGroup]'s submission statistics.
 *
 * Only the groups that pass more than a certain number of tests will be considered for the statistics.
 *
 * @param submissionStatistics is a List of [GroupSubmissionStatistics]. Each element contains the statistics for a ProjectGroup.
 * @param nrTests is an Int, indicating how many tests the assignment has.
 * @param inclusionThreshold is an Int, representing a min percentage that defines the threshold for inclusion of a ProjectGroup
 * into the calculations. By default, 75% will be used.
 * @return An [AssignmentStatistics]
 */
fun computeStatistics(submissionStatistics : List<GroupSubmissionStatistics>, nrTests : Int, inclusionThreshold: Int = 75): AssignmentStatistics {
    var nrGroupsOverThreshold = 0
    var totalNrOfSubmissionsOverThreshold = 0
    var groupsConsideredForStatistics = mutableListOf<GroupSubmissionStatistics>()

    for(subStats in submissionStatistics) {
        val passedTestsPercentage = subStats.nrPassedTests * 100 / nrTests
        if(passedTestsPercentage >= inclusionThreshold) {
            totalNrOfSubmissionsOverThreshold += subStats.nrSubmissions
            nrGroupsOverThreshold++;
            groupsConsideredForStatistics.add(subStats)
        }
    }

    // calculate average nr of submissions
    val averageNrOfSubmissions = totalNrOfSubmissionsOverThreshold / (1.0 * nrGroupsOverThreshold)

    // calculate standard dev
    var dev = 0.0

    // if there is only 1 group, the std dev will be irrelevant
    if(nrGroupsOverThreshold > 1) {
        for (subStats in submissionStatistics) {
            val passedTestsPercentage = subStats.nrPassedTests * 100 / nrTests
            if (passedTestsPercentage >= inclusionThreshold) {
                dev += Math.pow(subStats.nrSubmissions - averageNrOfSubmissions, 2.0)
            }
        }
        dev = Math.sqrt(dev / (nrGroupsOverThreshold - 1))
    }


    return AssignmentStatistics(averageNrOfSubmissions, dev, groupsConsideredForStatistics)
}
