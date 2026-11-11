/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2025 Pedro Alves
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
package org.dropproject.services

import org.dropproject.dao.Author
import org.dropproject.services.GitAuthorMatcher.Match
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitAuthorMatcherTests {

    private val tiago = Author(name = "Tiago Costa", userId = "a22504146")
    private val miguel = Author(name = "Miguel Belo Duarte", userId = "a22506369")
    private val matcher = GitAuthorMatcher(listOf(tiago, miguel))

    private fun memberOf(name: String, email: String): Author? = (matcher.match(name, email) as? Match.Mapped)?.member

    @Test
    fun `an email with the student number is mapped to that student`() {
        assertEquals(tiago, memberOf("tc", "a22504146@alunos.ulusofona.pt"))
        assertEquals(tiago, memberOf("tc", "A22504146@alunos.ulusofona.pt"))
        assertEquals(tiago, memberOf("tc", "22504146@gmail.com"))
        assertEquals(miguel, memberOf("tc", "miguel.22506369@gmail.com"))
    }

    @Test
    fun `a name with the student number is mapped to that student`() {
        assertEquals(tiago, memberOf("a22504146", "1234+someone@users.noreply.github.com"))
        assertEquals(miguel, memberOf("Miguel 22506369", "x@example.com"))
    }

    @Test
    fun `a number that is part of a longer one is not a match`() {
        // the digits may be preceded by letters (e.g. the "a" of the id), but not by other digits
        assertEquals(Match.Unmapped, matcher.match("x", "122504146@gmail.com"))
        assertEquals(Match.Unmapped, matcher.match("x", "225041467@gmail.com"))
    }

    @Test
    fun `the member's name is a match, ignoring case, accents and punctuation`() {
        assertEquals(tiago, memberOf("tiago.costa", "tc@example.com"))
        assertEquals(tiago, memberOf("TiagoCosta", "tc@example.com"))
        assertEquals(tiago, memberOf("Tiágo Cósta", "tc@example.com"))
        assertEquals(miguel, memberOf("Miguel Belo Duarte", "md@example.com"))
    }

    @Test
    fun `a name that only resembles the member's is not a match`() {
        // GitHub usernames and partial names are never guessed
        assertEquals(Match.Unmapped, matcher.match("MiguelBeloDuart", "md@example.com"))
        assertEquals(Match.Unmapped, matcher.match("Miguel Duarte", "md@example.com"))
        assertEquals(Match.Unmapped, matcher.match("tiagoc", "tc@example.com"))
    }

    @Test
    fun `an identity that matches several members is ambiguous`() {
        val match = matcher.match("Tiago Costa", "a22506369@alunos.ulusofona.pt")
        assertTrue(match is Match.Ambiguous)
        assertEquals(setOf(tiago, miguel), (match as Match.Ambiguous).candidates.toSet())
    }

    @Test
    fun `short numbers only match as the whole id`() {
        val shortIdMatcher = GitAuthorMatcher(listOf(Author(name = "Student One", userId = "p123")))
        assertEquals(Match.Unmapped, shortIdMatcher.match("x", "123@example.com"))
        assertTrue(shortIdMatcher.match("x", "p123@example.com") is Match.Mapped)
    }
}
