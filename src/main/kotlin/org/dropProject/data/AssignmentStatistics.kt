package org.dropProject.data

/**
 * Represents statistics about the submissions done
 *
 * @property average is a Double, representing the average number of submissions
 * @property standardDeviation is a Double, representing the standard deviation of the average number of submissions
 */
import org.dropProject.services.AssignmentService

data class AssignmentStatistics(val average : Double,
                                val standardDeviation : Double) {}


fun identifyGroupsOutsideStatisticalNorms(submissionStatistics : List<GroupSubmissionStatistics>,
                                          statistics: AssignmentStatistics): List<GroupSubmissionStatistics> {
    val minPercentage = statistics.average - statistics.standardDeviation
    var result = mutableListOf<GroupSubmissionStatistics>()
    for(subStats in submissionStatistics) {
        if(subStats.nrSubmissions < minPercentage) {
            result.add(subStats)
        }
    }
    return result
}

/**
 * Represents the submission statistics for a certain [ProjectGroup].
 *
 * @property groupID is a Long, identifying the ProjectGroup
 * @property nrPassedTests is an Int
 * @property nrSubmissions is an Int
 */
data class GroupSubmissionStatistics(val groupID : Long,
                                     val nrPassedTests : Int,
                                     val nrSubmissions : Int) {

}

fun computeStatistics(submissionStatistics : List<GroupSubmissionStatistics>, nrTests : Int): AssignmentStatistics {
    var nrGroupsOverThreshold = 0
    var totalNrOfSubmissionsOverThreshold = 0
    for(subStats in submissionStatistics) {
        val passedTestsPercentage = subStats.nrPassedTests * 100 / nrTests
        if(passedTestsPercentage >= 75) {
            totalNrOfSubmissionsOverThreshold += subStats.nrSubmissions
            nrGroupsOverThreshold++;
        }
    }

    // calculate average nr of submissions
    val averageNrOfSubmissions = totalNrOfSubmissionsOverThreshold / (1.0 * nrGroupsOverThreshold)

    // calculate standard dev
    var dev = 0.0

    for(subStats in submissionStatistics) {
        //val delta = ().toDouble()
        val passedTestsPercentage = subStats.nrPassedTests * 100 / nrTests
        if(passedTestsPercentage >= 75) {
            dev += Math.pow(subStats.nrSubmissions - averageNrOfSubmissions, 2.0)
        }
    }

    dev = Math.sqrt(dev / (nrGroupsOverThreshold - 1))

    return AssignmentStatistics(averageNrOfSubmissions, dev)
}