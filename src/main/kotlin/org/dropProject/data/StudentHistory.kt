package org.dropProject.data

import org.dropProject.dao.Assignment
import org.dropProject.dao.Author
import org.dropProject.dao.ProjectGroup

class StudentHistoryEntry(val group: ProjectGroup,
                          val assignment: Assignment) {

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

}