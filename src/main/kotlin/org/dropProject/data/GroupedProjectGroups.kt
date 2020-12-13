package org.dropProject.data

import org.apache.coyote.http11.Constants.a
import org.dropProject.dao.ProjectGroup

//class GroupedGroups(ids : List<Long>, failedTestNames : List<String>) {

/**
 * Represents student groups that were signalled as failling exactly the same unit tests
 * @property groups is a List of ProjectGroups
 * @property failedTestNames is a List of String
 */
data class GroupedProjectGroups(val groups : List<ProjectGroup>,
                                val failedTestNames: List<String>) {

    fun getGroupMembers(): List<String> {
        var result = mutableListOf<String>()
        for(group in groups) {
            result.add("Group: " + group.id + " | " + group.authorsNameStr());
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