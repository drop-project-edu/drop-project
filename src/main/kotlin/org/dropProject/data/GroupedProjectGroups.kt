package org.dropProject.data

import org.apache.coyote.http11.Constants.a
import org.dropProject.dao.ProjectGroup

/**
 * Represents student groups that were signalled as failling exactly the same unit tests
 * @property groups is a List of [ProjectGroup]s
 * @property failedTestNames is a List of Strings with the names of the unit tests that the groups are failing
 */
data class GroupedProjectGroups(val groups : List<ProjectGroup>,
                                val failedTestNames: List<String>) {

    fun getGroupIDs(): List<Long> {
        var result = mutableListOf<Long>()
        for(group in groups) {
            result.add(group.id)
        }
        return result
    }

    fun getGroupMembers(): List<String> {
        var result = mutableListOf<String>()
        for(group in groups) {
            result.add("Group: " + group.id + " | " + group.authorsNameStr())
        }
        return result;
    }

    fun showNrOfFailedTests(): Int {
        return failedTestNames.size
    }

    fun getTestNames(): List<String> {
        return failedTestNames;
    }
}