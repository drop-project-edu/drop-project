package org.dropProject.data

import org.dropProject.dao.Assignment
import org.dropProject.dao.Author
import org.dropProject.dao.ProjectGroup
import org.dropProject.dao.Submission

class StudentHistoryEntry(val group: ProjectGroup,
                          val assignment: Assignment) {

    var sorted = false
    val submissions: ArrayList<Submission> = ArrayList<Submission>()
    var sortedSubmissions: List<Submission> = ArrayList<Submission>()

    fun addSubmission(s: Submission) {
        submissions.add(s);
        sorted = false;
    }

    fun getNrOfSubmissions(): Int {
        return submissions.size
    }

    fun ensureSubmissionsAreSorted() {
        if(!sorted) {
            sortedSubmissions = submissions.sortedBy { it.submissionDate }.reversed()
            sorted = true;
        }
    }

    fun getLastSubmission(): Submission {
        ensureSubmissionsAreSorted()
        return sortedSubmissions.get(0)
    }
}

class StudentHistory(val author : Author) {

    var groupByAssignment = HashMap<Long?, ArrayList<Assignment>>()

    var history = ArrayList<StudentHistoryEntry>()

    fun addGroupAndAssignment(group: ProjectGroup, assignment: Assignment) {
        /*
        var assignments = ArrayList<Assignment>()
        if(groupByAssignment.containsKey(group.id)) {
            assignments = groupByAssignment.get(group.id)!!
        }
        assignments.add(assignment)
        groupByAssignment.put(group.id, assignments)
        */
        history.add(StudentHistoryEntry(group, assignment))
    }

    fun addSubmission(s: Submission) {
        for(entry in history) {
            if(entry.group == s.group && entry.assignment.id == s.assignmentId) {
                entry.addSubmission(s);
                break;
            }
        }
    }

    /**
     * Returns the history sorted by date, starting with the assignment associated with the last submission
     */
    fun getHistorySortedByDateDesc() : List<StudentHistoryEntry> {
        return history.sortedWith(compareByDescending { it.getLastSubmission().submissionDate })
    }

    fun ensureSubmissionsAreSorted() {
        history.forEach { it.ensureSubmissionsAreSorted() }
    }
}
