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
package org.dropProject.controllers

import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MultipartException
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.logging.Level
import java.util.logging.Logger
import jakarta.servlet.http.HttpServletRequest


@ControllerAdvice
class GlobalExceptionHandler {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    @ExceptionHandler(MultipartException::class)
    fun myError(exception: Exception, redirectAttributes: RedirectAttributes, request: HttpServletRequest): ResponseEntity<String> {

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

    // TODO: This assumes a REST response, But what about HTML responses?...
    @ExceptionHandler(RuntimeException::class)
    fun genericException(exception: Exception): ResponseEntity<String> {
        LOG.error("Generic exception", exception)
        exception.printStackTrace()  // TODO For same reason, the above line doesn't print the stacktrace
        return ResponseEntity("{\"error\": \"${exception}\"}", HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException::class)
    fun accessDeniedException(exception: Exception): ResponseEntity<Any> {
        LOG.warn("Access denied: ${exception.message}")
        return ResponseEntity("Access denied: ${exception.message}", HttpHeaders(), HttpStatus.FORBIDDEN)
    }

    @ExceptionHandler(IllegalAccessException::class)
    fun illegalAccessException(exception: Exception): ResponseEntity<Any> {
        LOG.warn("Illegal operation: ${exception.message}")
        return ResponseEntity("Illegal access: ${exception.message}", HttpHeaders(), HttpStatus.FORBIDDEN)
    }
}
