package org.dropProject.dao

import junit.framework.Assert.assertEquals
import org.junit.Test

class TestAuthor {

    @Test
    fun testAuthorConstructor() {
        var projGroup = ProjectGroup(1)
        var author = Author("BC", "1983", projGroup)
        assertEquals("BC", author.name)
        assertEquals("1983", author.userId)
        assertEquals(projGroup, author.group)
    }

}