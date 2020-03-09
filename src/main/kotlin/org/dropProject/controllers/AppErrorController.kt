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

import org.springframework.boot.web.servlet.error.ErrorAttributes
import org.springframework.boot.web.servlet.error.ErrorController
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest

const val ERROR_PATH = "/error"

@Controller
class AppErrorController(var errorAttributes: ErrorAttributes) : ErrorController {
    @RequestMapping(value = [ERROR_PATH], produces = ["text/html"])
    fun errorHtml(webRequest: WebRequest): ModelAndView {
        return ModelAndView("exception", getErrorAttributes(webRequest, false))
    }

    /**
     * Supports other formats like JSON, XML
     * @param request
     * @return
     */
    @RequestMapping(value = [ERROR_PATH])
    @ResponseBody
    fun error(request: HttpServletRequest, webRequest: WebRequest): ResponseEntity<Map<String, Any>> {
        val body = getErrorAttributes(webRequest, getTraceParameter(request))
        val status = getStatus(request)
        return ResponseEntity(body, status)
    }

    /**
     * Returns the path of the error page.
     *
     * @return the error path
     */
    override fun getErrorPath(): String {
        return ERROR_PATH
    }


    private fun getTraceParameter(request: HttpServletRequest): Boolean {
        val parameter = request.getParameter("trace") ?: return false
        return "false" != parameter.toLowerCase()
    }

    private fun getErrorAttributes(webRequest: WebRequest,
                                   includeStackTrace: Boolean): Map<String, Any> {
        return this.errorAttributes.getErrorAttributes(webRequest, includeStackTrace)
    }

    private fun getStatus(request: HttpServletRequest): HttpStatus {
        val statusCode = request.getAttribute("javax.servlet.error.status_code") as Int

        try {
            return HttpStatus.valueOf(statusCode)
        } catch (ex: Exception) {
            return HttpStatus.INTERNAL_SERVER_ERROR
        }
    }

}
