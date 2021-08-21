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
import org.dropProject.data.JSONViews
import org.dropProject.extensions.realName
import org.dropProject.repository.AssigneeRepository
import org.dropProject.repository.AssignmentRepository
import org.dropProject.services.AssignmentService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import springfox.documentation.annotations.ApiIgnore
import java.security.Principal


@RestController
@RequestMapping("/api/student")
@Api(authorizations = [Authorization(value="basicAuth")],
     value="Student API",
     description = "All endpoints must be authenticated using a personal token, sent with the standard \"basic auth\" header")
class StudentAPIController(
    val assignmentRepository: AssignmentRepository,
    val assigneeRepository: AssigneeRepository,
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    @GetMapping(value = ["/assignments/current"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @JsonView(JSONViews.API::class)  // to publish only certain fields of the Assignment
    @ApiOperation(value = "Get all the current assignments assigned or associated with the person identified by the \"basic auth\" header",
                  response = Assignment::class, responseContainer = "List", ignoreJsonView = false)
    fun getCurrentAssignments(principal: Principal): ResponseEntity<List<Assignment>> {
        val authorizedAssignments = assigneeRepository.findByAuthorUserId(principal.realName())
        val result = authorizedAssignments.map {
            assignmentRepository.getById(it.assignmentId)
        }
        return ResponseEntity.ok(result)
    }
}
