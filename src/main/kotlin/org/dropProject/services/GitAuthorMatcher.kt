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
import java.text.Normalizer

/**
 * Tells which member of a group wrote a commit, from the name and email that git recorded for its author.
 *
 * Git identities are whatever each student configured on their machine, so they are only linked to a member when
 * the evidence is unambiguous, and never by resemblance. An identity is linked to a member when:
 *
 * - its email or its name contains the member's id (e.g. `a22504146@alunos.ulusofona.pt` for `a22504146`), or the
 *   digits of that id when there are at least [MIN_DIGITS] of them (e.g. `22504146@...`), as a whole token; or
 * - its name is the member's name, ignoring case, accents, spaces and punctuation (e.g. `tiago.costa` for
 *   "Tiago Costa", but not `tiagoc` nor "Tiago Miguel Costa").
 *
 * An identity that matches no member, or more than one, is reported as unmapped, so that a teacher can decide.
 *
 * @property members the authors of the group, as declared in its AUTHORS.txt
 */
class GitAuthorMatcher(private val members: List<Author>) {

    sealed interface Match {
        data class Mapped(val member: Author) : Match
        data object Unmapped : Match
        data class Ambiguous(val candidates: List<Author>) : Match
    }

    fun match(name: String, email: String): Match {
        val candidates = members.filter { matches(it, name, email) }
        return when (candidates.size) {
            0 -> Match.Unmapped
            1 -> Match.Mapped(candidates.single())
            else -> Match.Ambiguous(candidates)
        }
    }

    private fun matches(member: Author, name: String, email: String): Boolean {
        val texts = listOf(email.lowercase(), name.lowercase())

        val userId = member.userId.trim().lowercase()
        if (userId.isNotEmpty() && texts.any { containsToken(it, userId, Regex("[a-z0-9]")) }) {
            return true
        }

        val digits = userId.filter { it.isDigit() }
        if (digits.length >= MIN_DIGITS && texts.any { containsToken(it, digits, Regex("[0-9]")) }) {
            return true
        }

        val memberName = normalizedName(member.name)
        return memberName.isNotEmpty() && memberName == normalizedName(name)
    }

    /**
     * Whether [token] occurs in [text] without being part of a longer token, i.e. not surrounded by characters that
     * match [tokenChar].
     */
    private fun containsToken(text: String, token: String, tokenChar: Regex): Boolean {
        var start = text.indexOf(token)
        while (start >= 0) {
            val end = start + token.length
            val before = text.getOrNull(start - 1)?.toString()
            val after = text.getOrNull(end)?.toString()
            if ((before == null || !tokenChar.matches(before)) && (after == null || !tokenChar.matches(after))) {
                return true
            }
            start = text.indexOf(token, start + 1)
        }
        return false
    }

    private fun normalizedName(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
            .filter { it.isLetterOrDigit() }

    companion object {
        /**
         * Shorter digit sequences are too likely to occur by chance in an email or a name.
         */
        const val MIN_DIGITS = 5
    }
}
