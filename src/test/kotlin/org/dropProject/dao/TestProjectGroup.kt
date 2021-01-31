package org.dropProject.dao

import junit.framework.Assert.assertFalse
import org.junit.Test

class TestProjectGroup {

    @Test
    fun projectGroup() {
        var projectGroup = ProjectGroup(1)
        projectGroup.authors.add(Author(1, "BC", "1983"))
        assert(projectGroup.contains("1983"))
        assertFalse(projectGroup.contains("1143"))
    }

}