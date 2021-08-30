/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2021 Pedro Alves
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

import com.fasterxml.jackson.annotation.JsonView
import io.swagger.annotations.*
import org.dropProject.dao.Assignment
import org.dropProject.dao.Indicator
import org.dropProject.dao.SubmissionReport
import org.dropProject.data.JSONViews
import org.dropProject.data.SubmissionResult
import org.dropProject.extensions.realName
import org.dropProject.forms.UploadForm
import org.dropProject.repository.AssigneeRepository
import org.dropProject.repository.AssignmentRepository
import org.dropProject.services.AssignmentService
import org.dropProject.services.FullBuildReport
import org.dropProject.services.ReportService
import org.dropProject.services.SubmissionService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.ui.ModelMap
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.security.Principal
import javax.servlet.http.HttpServletRequest
import javax.validation.Valid


@RestController
@RequestMapping("/api/teacher")
@Api(
    authorizations = [Authorization(value = "basicAuth")],
    value = "Teacher API",
    description = "All endpoints must be authenticated using a personal token associated with the TEACHER role, " +
            "sent with the standard \"basic auth\" header.<br/>" +
            "Using curl, it's as simple as including <pre>-u user:token</pre>"
)
class TeacherAPIController(
    val assignmentRepository: AssignmentRepository,
    val assigneeRepository: AssigneeRepository,
    val assignmentService: AssignmentService,
    val reportService: ReportService
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    @GetMapping(value = ["/assignments/current"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @JsonView(JSONViews.TeacherAPI::class)  // to publish only certain fields of the Assignment
    @ApiOperation(
        value = "Get all the current assignments created or visible to the person identified by the \"basic auth\" header",
        response = Assignment::class, responseContainer = "List", ignoreJsonView = false
    )
    fun getCurrentAssignments(principal: Principal): ResponseEntity<List<Assignment>> {
        val assignments = assignmentService.getMyAssignments(principal, archived = false)
        return ResponseEntity.ok(assignments)
    }


}
