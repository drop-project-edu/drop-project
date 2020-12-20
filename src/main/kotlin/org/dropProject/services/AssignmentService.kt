/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2020 Pedro Alves
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
package org.dropProject.services

import org.dropProject.controllers.ReportController
import org.dropProject.dao.Assignment
import org.dropProject.dao.ProjectGroup
import org.dropProject.data.GroupedProjectGroups
import org.dropProject.extensions.realName
import org.dropProject.repository.*
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.ui.ModelMap
import java.security.Principal
import javax.servlet.http.HttpServletRequest

/**
 * AssignmentService provides Assignment related functionality (e.g. list of assignments).
 */
@Service
class AssignmentService(
        val assignmentRepository: AssignmentRepository,
        val assignmentACLRepository: AssignmentACLRepository,
        val submissionRepository: SubmissionRepository,
        val assigneeRepository: AssigneeRepository,
        val submissionService: SubmissionService,
        val assignmentTestMethodRepository: AssignmentTestMethodRepository
) {

    @Cacheable(
            value = ["archivedAssignmentsCache"],
            key = "#principal.name",
            condition = "#archived==true")

    /**
     * Returns the [Assignment]s that a certain user can access. The returned assignments will be the ones
     * that are owned by the user and also the ones that the user has been given access to.
     * @param principal is a [Principal], representing the user whose assignments shall be retrieved.
     * @param archived is a Boolean. If true, only archived Assignment(s) will be returned. Otherwise, only
     * non-archived Assignment(s) will be returned.
     * @return An [ArrayList] of Assignment(s)
     */
    fun getMyAssignments(principal: Principal, archived: Boolean): List<Assignment> {
        val assignmentsOwns = assignmentRepository.findByOwnerUserId(principal.realName())

        val assignmentsACL = assignmentACLRepository.findByUserId(principal.realName())
        val assignmentsAuthorized = ArrayList<Assignment>()
        for (assignmentACL in assignmentsACL) {
            assignmentsAuthorized.add(assignmentRepository.findById(assignmentACL.assignmentId).get())
        }

        val assignments = ArrayList<Assignment>()
        assignments.addAll(assignmentsOwns)
        assignments.addAll(assignmentsAuthorized)

        val filteredAssigments = assignments.filter { it.archived == archived }

        for (assignment in filteredAssigments) {
            assignment.numSubmissions = submissionRepository.countByAssignmentId(assignment.id).toInt()
            if (assignment.numSubmissions > 0) {
                assignment.lastSubmissionDate = submissionRepository.findFirstByAssignmentIdOrderBySubmissionDateDesc(assignment.id).submissionDate
            }
            assignment.numUniqueSubmitters = submissionRepository.findUniqueSubmittersByAssignmentId(assignment.id).toInt()
            assignment.public = !assigneeRepository.existsByAssignmentId(assignment.id)
        }

        return filteredAssigments
    }

    /**
     * Collects into [model] information about all the [Submission]s related with a certain [Assignment].
     * @param assignmentId is a String identifying the relevant assignment.
     * @param principal is a [Principal] representing the user making the request.
     * @param model is a [ModelMap] that will be populated with information to use in a View.
     * @param request is a [HttpServletRequest]
     * @param includeTestDetails is a Boolean, indicating if test-matrix information should be included.
     * @param mode is a String which indicates the page that is being served and influences the information that is
     * placed in the model. Possible values are:
     * - "summary" - meaning that the data is being loaded for the "Summary" page;
     * - "testMatrix" - meaning that the data is being loaded for the "Test Matrix" page; and
     * - "signalledSubmissions" - meaning that the data is being loaded for the "Signalled Groups" page.
     */
    fun getAllSubmissionsForAssignment(assignmentId: String, principal: Principal, model: ModelMap,
                                               request: HttpServletRequest, includeTestDetails: Boolean = false,
                                               mode: String) {
        val assignment = assignmentRepository.findById(assignmentId).get()
        val acl = assignmentACLRepository.findByAssignmentId(assignmentId)

        if (principal.realName() != assignment.ownerUserId && acl.find { it.userId == principal.realName() } == null) {
            throw IllegalAccessError("Assignment reports can only be accessed by their owner or authorized teachers")
        }

        val submissionInfoList = submissionService.getSubmissionsList(assignment)

        if (submissionInfoList.any { it.lastSubmission.coverage != null }) {
            model["hasCoverage"] = true
        }

        if (includeTestDetails) {
            val assignmentTests = assignmentTestMethodRepository.findByAssignmentId(assignmentId)

            if (assignmentTests.isEmpty()) {
                model["message"] = "No information about tests for this assignment"
            } else {
                // calculate how many submissions pass each test
                val testCounts = assignmentTests.map { "${it.testMethod}:${it.testClass}" to 0 }.toMap(LinkedHashMap())
                var hashMap : HashMap<ProjectGroup, java.util.ArrayList<String>> = HashMap()

                submissionInfoList.forEach {
                    val group = it.projectGroup
                    var failed = java.util.ArrayList<String>()
                    it.lastSubmission.testResults?.forEach {
                        if (it.type == "Success") {
                            testCounts.computeIfPresent("${it.methodName}:${it.getClassName()}") { _, v -> v + 1 }
                        }
                        else {
                            failed.add(it.methodName);
                        }
                    }

                    if(!failed.isEmpty()) {
                        hashMap.put(group, failed)
                    }
                }

                model["tests"] = testCounts

                if(mode == "signalledSubmissions") {
                    val signalledGroups = groupGroupsByFailures(hashMap);
                    if(signalledGroups.isEmpty()) {
                        if(model["message"] == null) {
                            model["message"] = "No groups identified as similar"
                        }
                    }
                    model["signalledGroups"] = signalledGroups
                }
            }
        }

        model["submissions"] = submissionInfoList
        model["countMarkedAsFinal"] = submissionInfoList.asSequence().filter { it.lastSubmission.markedAsFinal }.count()
        model["isAdmin"] = request.isUserInRole("DROP_PROJECT_ADMIN")
        model["mode"] = mode
    }

    /**
     * Identifies and joins into a group the student groups that are failing the same unit tests.
     *
     * @param failuresByGroup is an [HashMap] with a [ProjectGroup] as key and an [ArrayList] of Strings as value. Each
     * String in the ArrayList represents the name of a unit test that the group fails.
     * @return a [List] of [GroupedProjectsGroup]s
     */
    public fun groupGroupsByFailures(failuresByGroup: HashMap<ProjectGroup, java.util.ArrayList<String>>): List<GroupedProjectGroups> {

        val projectGroupsByFailures = mutableMapOf<String, java.util.ArrayList<ProjectGroup>>()

        // first, build an HashMap where
        // the key is going to be all the test names concatenated into a String
        // (e.g. "test01, test02" and "test01, test03, test05")
        // and the value is going to be the groups that fail those lists
        for ((projectGroup, failures) in failuresByGroup) {
            failures.sort()

            val key: String = failures.joinToString()

            if (projectGroupsByFailures.containsKey(key)) {
                val groups: java.util.ArrayList<ProjectGroup>? = projectGroupsByFailures.get(key)
                groups?.add(projectGroup)
                if (groups != null) {
                    projectGroupsByFailures.put(key, groups)
                }
            } else {
                val newList: java.util.ArrayList<ProjectGroup> = java.util.ArrayList<ProjectGroup>()
                newList.add(projectGroup)
                projectGroupsByFailures.put(key, newList)
            }
        }

        val result = mutableListOf<GroupedProjectGroups>()

        // second, using the newly created HashMap, create a list of
        // GroupedProjectGroups
        for ((failures, groups) in projectGroupsByFailures) {
            val failedTestNames = failures.split(", ")
            // when there is only one ProjectGroup with a specific set of failures, it will be ignored
            if(groups.size > 1) {
                result.add(GroupedProjectGroups(groups, failedTestNames))
            }
        }
        return result
    }

}
