package org.dropProject.dao

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.PrePersist

@Entity
data class ProjectGroupRestrictions(
    @Id @GeneratedValue
    val id: Long = 0,

    var minGroupSize: Int = 1,
    var maxGroupSize: Int? = null,
    var exceptions: String? = null  // comma separated list of users that are exempt from the restrictions
) {

    @PrePersist
    fun prePersist() {
        exceptions = exceptions?.replace(" ", "")?.replace("\n", "")
    }

    fun exceptionsAsList(): List<String>? {
        if (exceptions == null) {
            return null
        }
        return exceptions!!.split(",").sorted()
    }
}
