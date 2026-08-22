/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2026 Pedro Alves
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
package org.dropproject.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import java.nio.charset.StandardCharsets

private val errorMapper = ObjectMapper()

/**
 * Writes the body that the API reports its authentication and authorization errors with.
 *
 * The message is serialized instead of being interpolated into the json, because it is written by whoever threw the
 * exception and can contain the characters that would otherwise break the response: a quote, a backslash or a
 * newline. It usually contains a user's name, so the response is also explicitly encoded in UTF-8 - the default
 * encoding of a servlet response is ISO-8859-1, which would mangle any name that is not plain ascii.
 */
fun HttpServletResponse.writeApiError(status: Int, error: String, message: String?) {
    this.status = status
    this.contentType = MediaType.APPLICATION_JSON_VALUE
    this.characterEncoding = StandardCharsets.UTF_8.name()
    errorMapper.writeValue(this.writer, mapOf("error" to error, "message" to message))
}
