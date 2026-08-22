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

import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MultipartException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.logging.Level
import java.util.logging.Logger
import jakarta.servlet.http.HttpServletRequest


@ControllerAdvice
class GlobalExceptionHandler {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    @ExceptionHandler(MultipartException::class)
    fun multipartException(exception: Exception, redirectAttributes: RedirectAttributes, request: HttpServletRequest): ResponseEntity<String> {

        val isAjax = request.getHeader("X-Requested-With") == "XMLHttpRequest"

        if (!isAjax) {
            throw Exception("Was not expecting a multipart request that is not ajax...")
        }

        if (exception.cause?.cause is FileSizeLimitExceededException) {
            return ResponseEntity("{\"error\": \"Ficheiro excede o tamanho máximo permitido\"}", HttpStatus.INTERNAL_SERVER_ERROR);
        } else {
            return ResponseEntity("{\"error\": \"Erro genérico no upload\"}", HttpStatus.INTERNAL_SERVER_ERROR);
        }


    }

    /**
     * The exceptions that already carry the status to report, and the message to report it with, are answered with
     * exactly that. The status is the one that the thrower chose and the body is the reason, which is a message that
     * was written to be read by the user (e.g. "This export is no longer available").
     */
    @ExceptionHandler(ResponseStatusException::class)
    fun responseStatusException(exception: ResponseStatusException): ResponseEntity<String> {
        LOG.warn("ResponseStatusException: ${exception.reason}")
        return ResponseEntity(exception.reason ?: exception.message, exception.statusCode)
    }

    /**
     * Access denials are not converted into a response here. The exception is rethrown, so that it reaches the
     * ExceptionTranslationFilter of the security chain that is serving the request, and each chain reports it in its
     * own format: a json body for the API (see [org.dropproject.config.ApiSecurityConfig]) and a redirect to
     * /access-denied for the web interface (see [org.dropproject.security.WebSecurityConfig]). That is the same
     * report that the chains already produce for the denials that they detect themselves, in their authorization
     * rules, so a denial looks the same to the caller no matter where it was decided.
     *
     * The handler is still needed, even though it does not handle anything: without it, [genericException] would
     * catch the exception, because AccessDeniedException is a RuntimeException, and turn a 403 into a 500.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException::class)
    fun accessDeniedException(exception: org.springframework.security.access.AccessDeniedException) {
        LOG.warn("Access denied: ${exception.message}")
        throw exception
    }

    /**
     * [ResourceNotFoundException] is rethrown for the same reason as the access denials: it is annotated with
     * `@ResponseStatus(NOT_FOUND)`, but that annotation is only read by the ResponseStatusExceptionResolver, which
     * runs after this advice. Without this handler, [genericException] catches it, because it is a RuntimeException,
     * and reports a 404 as a 500.
     *
     * Rethrowing it lets the annotation be applied, and the response is then built by
     * [org.dropproject.controllers.AppErrorController], which answers with the exception page or with a json body,
     * depending on what the caller accepts.
     */
    @ExceptionHandler(ResourceNotFoundException::class)
    fun resourceNotFoundException(exception: ResourceNotFoundException) {
        LOG.warn("Resource not found")
        throw exception
    }

    /**
     * Reports the errors that no other handler knows how to report, in the format that the caller can read. This is
     * the last resort, so it is declared last, after all the handlers that know the exception that they are given.
     *
     * A browser is answered with the error page, and the way to do that is to rethrow the exception: leaving it
     * unhandled is what makes the servlet container dispatch the request to /error, which is served by
     * [AppErrorController] and rendered by the `exception` template. Building the response here instead would mean
     * duplicating the attributes (timestamp, status, message, ...) that Spring Boot already assembles for that page,
     * and it is the reason why an error used to be shown to the user as a line of raw json.
     *
     * The API and the ajax requests of the web interface are answered with the json below, which is the format that
     * they already expect, so they are not sent to an error page that they would not know how to display.
     *
     * Note that only a real servlet container dispatches to /error, so the branch that rethrows is not exercised by
     * the tests that use MockMvc (none of them asks for html, so they all take the json branch).
     */
    @ExceptionHandler(RuntimeException::class)
    fun genericException(exception: Exception, request: HttpServletRequest): ResponseEntity<String> {
        LOG.error("Generic exception", exception)

        val isAjax = request.getHeader("X-Requested-With") == "XMLHttpRequest"
        if (!isAjax && request.getHeader("Accept")?.contains(MediaType.TEXT_HTML_VALUE) == true) {
            throw exception
        }

        return ResponseEntity("{\"error\": \"${exception}\"}", HttpStatus.INTERNAL_SERVER_ERROR)
    }
}
