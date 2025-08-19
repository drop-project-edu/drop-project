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
package org.dropproject.data

import com.fasterxml.jackson.annotation.JsonView
import org.dropproject.dao.*

/**
 * Response data class containing comprehensive assignment detail information.
 * Used by both AssignmentController and MCP service.
 */
data class AssignmentDetailResponse(
    @JsonView(JSONViews.StudentAPI::class, JSONViews.TeacherAPI::class)
    val assignment: Assignment,
    
    @JsonView(JSONViews.TeacherAPI::class)
    val assignees: List<Assignee>,
    
    @JsonView(JSONViews.TeacherAPI::class)
    val acl: List<AssignmentACL>,
    
    @JsonView(JSONViews.TeacherAPI::class)
    val tests: List<AssignmentTestMethod>,
    
    @JsonView(JSONViews.TeacherAPI::class)
    val reports: List<AssignmentReport>,
    
    @JsonView(JSONViews.TeacherAPI::class)
    val reportMessage: String,
    
    @JsonView(JSONViews.TeacherAPI::class)
    val lastCommitInfo: String?,
    
    @JsonView(JSONViews.TeacherAPI::class)
    val sshKeyFingerprint: String?,
    
    @JsonView(JSONViews.TeacherAPI::class)
    val isAdmin: Boolean
)