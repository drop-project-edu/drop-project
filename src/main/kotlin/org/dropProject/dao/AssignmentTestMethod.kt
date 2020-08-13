package org.dropProject.dao

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
data class AssignmentTestMethod(
        @Id
        @GeneratedValue
        val id: Long = 0,

        val assignmentId: String,  // pseudo FK

        val testClass: String,
        val testMethod: String
)