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
package org.dropProject.mcp.data

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

// JSON-RPC 2.0 Request according to MCP spec
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: Any?, // String, Number, or null for notifications as per JSON-RPC 2.0 spec
    val method: String,
    val params: Map<String, Any>? = null
)

// JSON-RPC 2.0 Success Response
@JsonInclude(JsonInclude.Include.NON_NULL)
data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: Any?, // String, Number, or null as per JSON-RPC 2.0 spec
    val result: Any
)

// JSON-RPC 2.0 Error Response
@JsonInclude(JsonInclude.Include.NON_NULL)
data class McpErrorResponse(
    val jsonrpc: String = "2.0",
    val id: Any?, // String, Number, or null as per JSON-RPC 2.0 spec
    val error: McpError
)

// JSON-RPC 2.0 Error object
data class McpError(
    val code: Int,
    val message: String,
    val data: Any? = null
)

// MCP Initialize Response (as per MCP spec)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class McpInitializeResult(
    val protocolVersion: String = "2024-11-05",
    val capabilities: McpServerCapabilities,
    val serverInfo: McpServerInfo
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class McpServerCapabilities(
    val tools: McpToolsCapability? = null,
    val logging: Map<String, Any>? = null,
    val prompts: Map<String, Any>? = null,
    val resources: Map<String, Any>? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class McpToolsCapability(
    val listChanged: Boolean = false
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class McpServerInfo(
    val name: String,
    val version: String
)

// MCP Tools List Response (as per MCP spec)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class McpToolsListResult(
    val tools: List<McpTool>,
    @JsonProperty("_meta")
    val meta: Map<String, Any>? = null
)

// MCP Tool definition (as per MCP spec)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class McpTool(
    val name: String,
    val description: String? = null,
    val inputSchema: Map<String, Any>
)

// MCP Tool Call Response (as per MCP spec)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class McpToolCallResult(
    val content: List<McpContent>,
    val isError: Boolean? = null,
    @JsonProperty("_meta")
    val meta: Map<String, Any>? = null
)

// MCP Content types
@JsonInclude(JsonInclude.Include.NON_NULL)
data class McpContent(
    val type: String, // "text", "image", "resource", etc.
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)

// Custom DropProject-specific server info for our initialize response
data class DropProjectServerInfo(
    val name: String,
    val version: String,
    val description: String,
    val domain: String,
    val use_cases: List<String>,
    val context: Map<String, Any>
)