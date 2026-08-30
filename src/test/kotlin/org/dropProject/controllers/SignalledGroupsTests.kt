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
package org.dropproject.controllers

import org.dropproject.TestUsers.STUDENT_1
import org.dropproject.TestUsers.STUDENT_2
import org.dropproject.TestUsers.STUDENT_3
import org.dropproject.TestUsers.TEACHER_1
import org.junit.jupiter.api.Tag
import org.dropproject.DropProjectIntegrationTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.dropproject.dao.*
import org.dropproject.data.*
import org.dropproject.repository.*
import org.dropproject.services.AssignmentService
import org.hamcrest.Matchers.*
import java.util.*

@DropProjectIntegrationTest
@Tag("integration")
class SignalledGroupsTests : ReportTestBase() {

    /**
     * Tested function: ReportController.getSignaledGroupsOrSubmissions() via MVC
     * Test scenario:
     * - 3 students perform submissions.
     * - Two of them fail the same 2 tests.
     * - The other does not fail any test.
     * Expectations:
     * - The MVC controller function should place in the Model in the model a List of size 1.
     * - The only element of the list should be a GroupedProjectGroup with 2 groups (one for each student)
     * and 2 failed tests.
     */
    @Test
    fun `signalled groups`() {

        submissionFixtures.uploadProject("projectJUnitErrors", defaultAssignmentId, STUDENT_1)
        submissionFixtures.uploadProject("projectJUnitErrors", defaultAssignmentId, STUDENT_2,
            authors = listOf(STUDENT_2.username to "Student 2")
        )
        submissionFixtures.uploadProject("projectOK", defaultAssignmentId, STUDENT_3,
            authors = listOf(STUDENT_3.username to "Student 3")
        )

        val reportResult = this.mvc.perform(
            get("/signalledSubmissions/${defaultAssignmentId}")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val signalledGroups = reportResult.modelAndView!!.modelMap["signalledGroups"] as List<GroupedProjectGroups>

        assert(signalledGroups != null)
        assert(signalledGroups.size == 1)
        assert(signalledGroups.get(0).groups.size == 2)

        assert(signalledGroups.get(0).failedTestNames.size == 2)
        var expectedFailedTests = mutableListOf("testFuncaoParaTestar", "testFuncaoParaTestarQueNaoApareceAosAlunos")
        assert(signalledGroups.get(0).failedTestNames.containsAll(expectedFailedTests))
    }

    /**
     * Tested function: ReportController.getSignaledGroupsOrSubmissions() via MVC
     * Test scenario:
     * - 2 students perform submissions.
     * - One of them fails tests.
     * - The other does not fail any test.
     * Expectations:
     * - The MVC controller function should place in the Model:
     * -- a List of size 0; and
     * -- a String with a message saying that there are no signalled groups.
     */
    @Test
    fun `signalled groups via mvc - no groups are signalled`() {

        submissionFixtures.uploadProject("projectJUnitErrors", defaultAssignmentId, STUDENT_1)
        submissionFixtures.uploadProject("projectOK", defaultAssignmentId, STUDENT_3,
            authors = listOf(STUDENT_3.username to "Student 3")
        )

        val reportResult = this.mvc.perform(
            get("/signalledSubmissions/${defaultAssignmentId}")
                .with(user(TEACHER_1))
        )
            .andExpect(status().isOk())
            .andReturn()

        @Suppress("UNCHECKED_CAST")
        val message = reportResult.modelAndView!!.modelMap["message"] as String
        val list = reportResult.modelAndView!!.modelMap["signalledGroups"]
        assertNull(list)
        assertNotNull(message)
        assertEquals("No groups identified as similar", message)
    }

    /**
     * This function creates "test data" that will be used in multiple tests of
     * the function AssignmentService.groupGroupsByFailures()
     */
    private fun testDataForGroupGroupsByFailures(): List<ProjectGroup> {
        val g1 = ProjectGroup(1)
        g1.authors.add(Author(1, "BC", "BC"))
        val g2 = ProjectGroup(2)
        g2.authors.add(Author(2, "RCC", "RCC"))
        val g3 = ProjectGroup(3)
        g3.authors.add(Author(3, "PA", "PA"))
        val g4 = ProjectGroup(4)
        g4.authors.add(Author(4, "IL", "IL"))
        val g5 = ProjectGroup(5)
        g5.authors.add(Author(5, "RP", "RP"))
        return mutableListOf<ProjectGroup>(g1, g2, g3, g4, g5)
    }

    /**
     * Tested function: AssignmentService.groupGroupsByFailures()
     * Test scenario:
     * - 3 student/project groups
     * - All have different test failures
     * - The groupGroupsByFailures() function will return a List with size 0
     */
    @Test
    fun `groupGroupsByFailures - no groups are signalled`() {
        val projectGroups = testDataForGroupGroupsByFailures();
        var g1 = projectGroups[0];
        var g2 = projectGroups[1];
        var g3 = projectGroups[2];

        val failuresByGroup: HashMap<ProjectGroup, ArrayList<String>> = HashMap()

        // in this scenario, the 3 ProjectoGroups have distinct failures, so nothing will be Signalled
        failuresByGroup.put(g1, mutableListOf("Test001", "Test002") as ArrayList<String>)
        failuresByGroup.put(g2, mutableListOf("Test001") as ArrayList<String>)
        failuresByGroup.put(g3, mutableListOf("Test002") as ArrayList<String>)

        val expected = mutableListOf<GroupedProjectGroups>()
        val result = assignmentService.groupGroupsByFailures(failuresByGroup)

        assert(expected != null)
        assert(result.size == 0)
    }

    /**
     * Tested function: AssignmentService.groupGroupsByFailures()
     * Test scenario:
     * - 3 student/project groups
     * - All have the same failures, but one of the groups has the failures in different order
     * Expectations:
     * - The groupGroupsByFailures() function will return a List with size 1
     * - The only element of the returned list will contain 3 student groups and 2 failed tests
     */
    @Test
    fun `groupGroupsByFailures - all groups are signalled`() {
        val projectGroups = testDataForGroupGroupsByFailures();
        var g1 = projectGroups[0];
        var g2 = projectGroups[1];
        var g3 = projectGroups[2];

        val failuresByGroup: HashMap<ProjectGroup, ArrayList<String>> = HashMap()

        failuresByGroup.put(g1, mutableListOf("Test001", "Test002") as ArrayList<String>)
        failuresByGroup.put(g2, mutableListOf("Test001", "Test002") as ArrayList<String>)
        // the order of the failures should not influence the "signalling"
        failuresByGroup.put(g3, mutableListOf("Test002", "Test001") as ArrayList<String>)

        var group1 = GroupedProjectGroups(mutableListOf<ProjectGroup>(g1, g2, g3), mutableListOf("Test001", "Test002"))
        val result = assignmentService.groupGroupsByFailures(failuresByGroup)

        assert(result != null)
        assert(result.size == 1)

        // the order of each group (g1, g2 and g3) in the result might change, so we check
        // the list size and the individual existence of each group
        assert(result.get(0).groups.size == 3)
        assert(result.get(0).groups.contains(g1));
        assert(result.get(0).groups.contains(g2));
        assert(result.get(0).groups.contains(g3));

        assert(result.get(0).failedTestNames.contains("Test001"))
        assert(result.get(0).failedTestNames.contains("Test002"))
    }

    /**
     * Tested function: AssignmentService.groupGroupsByFailures()
     * Test scenario:
     * - 5 student/project groups
     * - groups 1 and 3 have the same failures
     * - group 2 has failures but the list is unlike any other group
     * - groups 4 and 5 have hte same failures (in different order)
     * Expectations:
     * - The groupGroupsByFailures() function will return a List with size 2
     * - The list will have two GroupedProjectGroups objects:
     * -- One with groups 1 and 3
     * -- One with groups 4 and 5
     */
    @Test
    fun `groupGroupsByFailures - more complex scenario`() {

        val projectGroups = testDataForGroupGroupsByFailures();
        var g1 = projectGroups[0];
        var g2 = projectGroups[1];
        var g3 = projectGroups[2];
        var g4 = projectGroups[3];
        var g5 = projectGroups[4];

        val failuresByGroup: HashMap<ProjectGroup, ArrayList<String>> = HashMap()

        failuresByGroup.put(g1, mutableListOf("Test001", "Test002") as ArrayList<String>)
        failuresByGroup.put(g2, mutableListOf("Test001") as ArrayList<String>)
        failuresByGroup.put(g3, mutableListOf("Test001", "Test002") as ArrayList<String>)

        // same failed tests, different order
        failuresByGroup.put(g4, mutableListOf("Test001", "Test003") as ArrayList<String>);
        failuresByGroup.put(g5, mutableListOf("Test003", "Test001") as ArrayList<String>);

        // the ProjectGroups with the same failures are:
        //    (1 and 3) ; (2) ; (4 and 5)
        // however, (2) will be ignored because there is no "suspicion"
        // as such, the expected groups are:
        //    (1 and 3) ; (4 and 5)
        val group1 = GroupedProjectGroups(mutableListOf(g1, g3), mutableListOf("Test001", "Test002"))
        val group3 = GroupedProjectGroups(mutableListOf(g4, g5), mutableListOf("Test001", "Test003"))

        //val expected = mutableListOf<GroupedProjectGroups>(group1, group2, group3)
        val expected = mutableListOf<GroupedProjectGroups>(group1, group3)

        // cal fn to check result
        val result = assignmentService.groupGroupsByFailures(failuresByGroup)

        assert(result != null)
        assert(result.size == 2)

        assert(result.containsAll(expected))
    }

    /**
     * This function generates test data for the code that identifies "suspicious" groups.
     *
     * @param nrSuspiciousCases is an Int, identifying one of two possible scenarios. If the value is 1, the returned
     * data will contain only one suspicious group. If the value is 2, the returned data will contain two suspicious
     * groups.
     */
    fun testDataForComputeStatistics(nrSuspiciousCases: Int): List<GroupSubmissionStatistics> {
        var groups = testDataForGroupGroupsByFailures();
        var submissionStatistics = mutableListOf<GroupSubmissionStatistics>()

        submissionStatistics.add(GroupSubmissionStatistics(groups[0].id, 15, 20, groups[0]));
        submissionStatistics.add(GroupSubmissionStatistics(groups[1].id, 10, 22, groups[1])); // ignored
        submissionStatistics.add(GroupSubmissionStatistics(groups[2].id, 17, 18, groups[2]));
        submissionStatistics.add(GroupSubmissionStatistics(groups[3].id, 15, 5, groups[3])); // suspicious
        submissionStatistics.add(GroupSubmissionStatistics(groups[4].id, 20, 20, groups[4]));

        if (nrSuspiciousCases == 2) {
            submissionStatistics.add(GroupSubmissionStatistics(6, 16, 6, ProjectGroup(6))) // suspicious
            submissionStatistics.add(GroupSubmissionStatistics(7, 17, 19, ProjectGroup(7)))
            submissionStatistics.add(GroupSubmissionStatistics(8, 14, 14, ProjectGroup(8))) // ignored
        }

        return submissionStatistics
    }

    /**
     * Tested function: computeStatistics()
     *
     * This is a test for the calculation of the average and standard deviation statistics.
     */
    @Test
    fun `compute group statistics`() {
        var submissionStatistics = testDataForComputeStatistics(1)
        var nrOfGroups = 4.0
        var expectedAverageNumberOfSubmissions = (20 + 18 + 5 + 20) / nrOfGroups
        var expectedStdDev = 7.22
        var result = computeStatistics(submissionStatistics, 20)
        assertEquals(expectedAverageNumberOfSubmissions, result.average, 0.01)
        assertEquals(expectedStdDev, result.standardDeviation, 0.01);
    }

    /**
     * Tested function: AssignmentStatistics.identifyGroupsOutsideStatisticalNorms()
     *
     * In this scenario there are 4 relevant groups. One (1) of those groups has a result that is considered "too good
     * to be true" (i.e. it is suspicious). That group should be returned by the function.
     */
    @Test
    fun `identifyGroupsOutsideStatisticalNorms`() {
        var submissionStatistics = testDataForComputeStatistics(1)
        var assignmentStatistics = computeStatistics(submissionStatistics, 20)
        // hack
        val pGroup = ProjectGroup(-1)
        var expected = listOf<GroupSubmissionStatistics>(GroupSubmissionStatistics(4, 15, 5, pGroup))
        var result = assignmentStatistics.identifyGroupsOutsideStatisticalNorms()
        assert(1 == result.size)
        assertEquals(expected, result)
    }

    /**
     * Tested function: AssignmentStatistics.identifyGroupsOutsideStatisticalNorms()
     *
     * In this scenario there are 6 relevant groups. Two (2) of those groups have a result that is considered "too good
     * to be true" (i.e. it is suspicious). Those 2 groups should be returned by the function.
     */
    @Test
    fun `identifyGroupsOutsideStatisticalNorms - more than one suspicious group`() {
        var submissionStatistics = testDataForComputeStatistics(2)
        var assignmentStatistics = computeStatistics(submissionStatistics, 20)

        // hack
        // create a dummy ProjectGroup just to respect GroupSubmissionStatistics' protocol
        // the test does not check the ProjectGroups, so we can use the same object
        val pGroup = ProjectGroup(-1)

        var gss1 = GroupSubmissionStatistics(4, 15, 5, pGroup)
        var gss2 = GroupSubmissionStatistics(6, 16, 6, pGroup)
        var expected = listOf<GroupSubmissionStatistics>(gss1, gss2)
        var result = assignmentStatistics.identifyGroupsOutsideStatisticalNorms()
        assert(2 == result.size)
        assert(result.containsAll(expected))
    }

    /**
     * Tested function: AssignmentStatistics.identifyGroupsOutsideStatisticalNorms()
     *
     * In this scenario all the groups will be below de 75% threshold. This means that no groups should be identified
     * as being outside the statistical norms.
     */
    @Test
    fun `identifyGroupsOutsideStatisticalNorms - no groups over threshold`() {
        var submissionStatistics = mutableListOf<GroupSubmissionStatistics>()
        // hack : since the test is not testing the ProjectGroup objects, we can use this dummy group in all objects
        val pGroup = ProjectGroup(-1);
        // all the groups are below the 75% threshold
        submissionStatistics.add(GroupSubmissionStatistics(1, 10, 20, pGroup));
        submissionStatistics.add(GroupSubmissionStatistics(2, 10, 22, pGroup));
        submissionStatistics.add(GroupSubmissionStatistics(3, 12, 20, pGroup));
        var assignmentStatistics = computeStatistics(submissionStatistics, 20)
        var result = assignmentStatistics.identifyGroupsOutsideStatisticalNorms()
        assertEquals(0, assignmentStatistics.groupsConsideredForStatistics.size)
        assert(result.isEmpty())
    }

    /**
     * Tested function: AssignmentStatistics.identifyGroupsOutsideStatisticalNorms()
     *
     * In this scenario, two of the three groups are below the 75% threshold. This means that no groups should be
     * identified as being outside the statistical norms, because only one group will define the norm and will not be
     * outside of it.
     */
    @Test
    fun `identifyGroupsOutsideStatisticalNorms - only the one group is over threshold`() {
        var submissionStatistics = mutableListOf<GroupSubmissionStatistics>()
        // hack : since the test is not testing the ProjectGroup objects, we can use this dummy group in all objects
        val pGroup = ProjectGroup(-1)
        // 2 of the 3 groups are below the 75% threshold
        submissionStatistics.add(GroupSubmissionStatistics(1, 10, 20, pGroup)); // ignored
        submissionStatistics.add(GroupSubmissionStatistics(2, 15, 22, pGroup));
        submissionStatistics.add(GroupSubmissionStatistics(3, 12, 20, pGroup)); // ignored
        var assignmentStatistics = computeStatistics(submissionStatistics, 20)
        var result = assignmentStatistics.identifyGroupsOutsideStatisticalNorms()
        assert(result.isEmpty())
        assertEquals(1, assignmentStatistics.groupsConsideredForStatistics.size)
    }

    /**
     * Tested function: AssignmentStatistics.identifyGroupsOutsideStatisticalNorms()
     *
     * Scenario where a Group has little submissions but also has little tests.
     *
     * Average: 21.33
     * StdDev: 1.154
     * Average - StdDev = 20.17
     *
     * Three Groups will be considered for the statistics.
     *
     * Any Group with at least 15 tests and less than 20.17 submissions will be signalled. There is only 1 Group in
     * this situation (nr tests: 17, nr subs: 20).
     */
    @Test
    fun `identifyGroupsOutsideStatisticalNorms - more complex scenario`() {
        var submissionStatistics = mutableListOf<GroupSubmissionStatistics>()

        // hack : since the test is not testing the ProjectGroup objects, we can use this dummy group in all objects
        val pGroup = ProjectGroup(-1)

        submissionStatistics.add(GroupSubmissionStatistics(1, 10, 20, pGroup)); // ignored because below 75% of tests
        submissionStatistics.add(GroupSubmissionStatistics(2, 15, 22, pGroup));
        submissionStatistics.add(GroupSubmissionStatistics(3, 10, 10, pGroup)); // ignored low tests & low subs
        submissionStatistics.add(GroupSubmissionStatistics(4, 15, 22, pGroup));
        val gss5 = GroupSubmissionStatistics(5, 17, 20, pGroup)
        submissionStatistics.add(gss5); // signalled
        var assignmentStatistics = computeStatistics(submissionStatistics, 20)
        var result = assignmentStatistics.identifyGroupsOutsideStatisticalNorms()
        assertEquals(3, assignmentStatistics.groupsConsideredForStatistics.size)
        assert(1 == result.size)
        assert(result.contains(gss5))
    }
}
