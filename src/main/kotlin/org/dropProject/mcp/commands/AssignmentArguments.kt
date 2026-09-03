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
package org.dropproject.mcp.commands

import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * The arguments of the tools that create and edit assignments, read with the types that
 * [org.dropproject.forms.AssignmentForm] expects. This is what the web form gets from Spring's data binding, which
 * has no form to bind here.
 *
 * Editing an assignment has to tell an argument that was left out, meaning "keep whatever the assignment already
 * has", from one that was passed empty, meaning "clear this setting". Both read as null, so [isPresent] is what
 * separates them, and [orCurrent] is how the editing tool applies that distinction.
 *
 * @property arguments the raw arguments of the tool call
 */
class AssignmentArguments(private val arguments: Map<String, Any?>) {

    /**
     * Tells whether [name] was passed at all, no matter which value it carries.
     */
    fun isPresent(name: String): Boolean = arguments.containsKey(name)

    /**
     * The value of [name] if it was passed, or [current] if it wasn't. [read] is only called for the arguments that
     * were passed, so the settings that the caller didn't mention keep their value instead of being cleared.
     */
    fun <T> orCurrent(name: String, current: T, read: AssignmentArguments.(String) -> T): T =
        if (isPresent(name)) this.read(name) else current

    /**
     * @throws IllegalArgumentException if [name] is missing, empty or not a string
     */
    fun requiredString(name: String): String =
        string(name) ?: throw IllegalArgumentException("$name is required and must be a non empty string")

    fun string(name: String): String? {
        val value = arguments[name] ?: return null
        if (value !is String) {
            throw IllegalArgumentException("$name must be a string")
        }
        return value.ifBlank { null }
    }

    fun number(name: String): Int? {
        val value = arguments[name] ?: return null
        return (value as? Number)?.toInt()
            ?: throw IllegalArgumentException("$name must be a number")
    }

    fun boolean(name: String): Boolean? {
        val value = arguments[name] ?: return null
        return value as? Boolean
            ?: throw IllegalArgumentException("$name must be a boolean")
    }

    fun <T : Enum<T>> enum(name: String, values: List<T>): T? {
        val value = string(name) ?: return null
        return values.find { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("$name must be one of ${values.joinToString { it.name }}")
    }

    /**
     * Reads a setting that an assignment can't be without, i.e. one that can be changed but not cleared.
     *
     * @throws IllegalArgumentException if [name] is missing, empty or not one of [values]
     */
    fun <T : Enum<T>> requiredEnum(name: String, values: List<T>): T =
        enum(name, values)
            ?: throw IllegalArgumentException("$name is required and must be one of ${values.joinToString { it.name }}")

    fun dateTime(name: String): LocalDateTime? {
        val value = string(name) ?: return null
        return try {
            LocalDateTime.parse(value)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("$name must be a date in ISO-8601 format, e.g. '2026-10-15T23:59'")
        }
    }
}
