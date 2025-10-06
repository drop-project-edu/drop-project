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
package org.dropproject.controllers

import org.dropproject.mcp.data.McpError
import org.dropproject.mcp.data.McpErrorResponse
import org.dropproject.mcp.data.McpRequest
import org.dropproject.mcp.data.McpResponse
import org.dropproject.mcp.services.McpService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/mcp")
class McpController(
    private val mcpService: McpService
) {

    private val logger = LoggerFactory.getLogger(McpController::class.java)

    @PostMapping("/", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun handleMcpRequest(
        @RequestBody request: McpRequest,
        principal: Principal
    ): ResponseEntity<Any>? {
        
        logger.info("MCP request: method=${request.method}, user=${principal.name}")
        
        // Handle notifications (no response expected)
//        if (request.method.startsWith("notifications/")) {
//            when (request.method) {
//                "notifications/initialized" -> {
//                    logger.info("MCP client initialized successfully for user: ${principal.name}")
//                }
//                else -> {
//                    logger.warn("Unknown notification: ${request.method}")
//                }
//            }
//            return null // No response for notifications
//        }
        
        // Handle regular requests (response expected)
        return try {
            val result = when (request.method) {
                "initialize" -> mcpService.getInitializeResult()
                "tools/list" -> mcpService.listTools()
                "tools/call" -> {
                    val toolName = request.params?.get("name") as? String
                        ?: throw IllegalArgumentException("Tool name is required")
                    val arguments = request.params["arguments"] as? Map<String, Any> ?: emptyMap()

                    mcpService.callTool(toolName, arguments, principal)
                }
                "resources/list" -> {
                    val submissionId = (request.params?.get("submissionId") as? Number)?.toLong()
                    mcpService.listResources(submissionId, principal)
                }
                "resources/read" -> {
                    val uri = request.params?.get("uri") as? String
                        ?: throw IllegalArgumentException("Resource URI is required")
                    mcpService.readResource(uri, principal)
                }
                else -> throw IllegalArgumentException("Unknown method: ${request.method}")
            }

            ResponseEntity.ok(McpResponse(id = request.id, result = result))

        } catch (e: Exception) {
            logger.error("MCP request failed: ${e.message}", e)
            ResponseEntity.ok(
                McpErrorResponse(
                    id = request.id,
                    error = McpError(
                        code = -1,
                        message = e.message ?: "Unknown error"
                    )
                )
            )
        }
    }
}
