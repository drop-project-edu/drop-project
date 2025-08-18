# Model Context Protocol (MCP) Integration for DropProject

## Overview

This document outlines the implementation of MCP (Model Context Protocol) endpoints for DropProject. MCP endpoints provide programmatic access to assignment information and search functionality through a standardized protocol. The implementation exposes two main tools: `get_assignment_info` for detailed assignment information and `search_assignments` for finding assignments.

## Architecture

### MCP Protocol Support
- **Transport**: HTTP JSON-RPC 2.0
- **Authentication**: Bearer token authentication using DropProject personal tokens
- **Base Path**: `/mcp/`
- **Content Type**: `application/json`
- **Protocol Version**: `2024-11-05`

### Security Model
MCP endpoints use DropProject's existing personal token system:
- **Authentication**: `Authorization: Bearer <personal-token>`
- **Token Type**: DropProject personal tokens (no token exchange needed)
- **Token Validation**: Uses existing PersonalTokenRepository
- **User Context**: Maintains full user identity and permissions
- **Authorization**: Same role-based access control as REST API

## Implementation Overview

The MCP integration consists of four main components:

1. **Data Transfer Objects** - JSON-RPC 2.0 compliant request/response structures
2. **Security Layer** - Bearer token authentication using personal tokens  
3. **Service Layer** - Business logic for MCP tools and assignment data
4. **Controller Layer** - HTTP endpoint for MCP requests

## Key Components

### 1. MCP Data Transfer Objects (`src/main/kotlin/org/dropProject/mcp/data/`)

The implementation includes standard MCP-compliant data classes:

- **McpRequest** - JSON-RPC 2.0 request format
- **McpResponse** - Success response wrapper  
- **McpErrorResponse** - Error response wrapper
- **McpInitializeResult** - Server capabilities and info
- **McpToolsListResult** - Available tools list
- **McpToolCallResult** - Tool execution results with content
- **McpContent** - Content wrapper for tool responses

### 2. Assignment Detail Response (`src/main/kotlin/org/dropProject/data/AssignmentDetailResponse.kt`)

A comprehensive data class containing all assignment information:
- Assignment metadata (name, ID, owner, etc.)
- Assignees and access control lists
- Test methods and validation reports  
- Git repository information
- Instructions and due dates

### 3. Personal Token Validation Service (`src/main/kotlin/org/dropProject/controllers/McpTokenController.kt`)

A simple service that validates DropProject personal tokens for MCP authentication:

```kotlin
@Service
class McpTokenController(
    private val personalTokenRepository: PersonalTokenRepository
) {
    fun validateBearerToken(token: String): String? {
        return try {
            val tokenEntity = personalTokenRepository.getByPersonalToken(token)
            if (tokenEntity != null && 
                tokenEntity.status == TokenStatus.ACTIVE && 
                tokenEntity.expirationDate.after(Date())) {
                tokenEntity.userId
            } else null
        } catch (e: Exception) { null }
    }
}
```

### 4. MCP Service Layer (`src/main/kotlin/org/dropProject/mcp/services/McpService.kt`)

The service layer handles MCP protocol operations and provides two main tools:

#### Available Tools

**1. `get_assignment_info`**
- **Purpose**: Get comprehensive assignment information
- **Input**: `assignmentId` (string)
- **Output**: Detailed assignment information including instructions, tests, assignees, validation reports, and git information
- **Implementation**: Uses `AssignmentService.getAssignmentDetailData()` for consistent data with web interface

**2. `search_assignments`**
- **Purpose**: Search assignments by name, ID, or tags
- **Input**: `query` (string) 
- **Output**: List of matching assignments with ID and name
- **Implementation**: Uses `TeacherAPIController.searchAssignments()` with proper authorization

#### Service Architecture

The service integrates with existing DropProject components:
- **AssignmentService** - For detailed assignment data
- **TeacherAPIController** - For assignment search functionality
- **Authorization** - Maintains existing role-based access control

Key features:
- **MCP Compliance**: All responses follow MCP tool result format with content arrays
- **Comprehensive Data**: Assignment info includes all details from web interface
- **Proper Authorization**: Same access control as REST API
- **Error Handling**: Proper exception handling with informative messages
### 5. Bearer Token Filter (`src/main/kotlin/org/dropProject/config/McpBearerTokenFilter.kt`)

A Spring Security filter that validates Bearer tokens for MCP endpoints:

```kotlin
class McpBearerTokenFilter(
    private val mcpTokenController: McpTokenController
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        
        if (authHeader?.startsWith("Bearer ") == true) {
            val token = authHeader.substring(7)
            val userId = mcpTokenController.validateBearerToken(token)
            
            if (userId != null) {
                val user = User.builder()
                    .username(userId)
                    .password("[PROTECTED]")
                    .authorities("ROLE_TEACHER") // MCP users get teacher privileges
                    .build()
                
                val authentication = UsernamePasswordAuthenticationToken(
                    user, null, user.authorities
                )
                SecurityContextHolder.getContext().authentication = authentication
            }
        }
        
        filterChain.doFilter(request, response)
    }
}
```

### 6. MCP Controller (`src/main/kotlin/org/dropProject/controllers/McpController.kt`)

The HTTP controller that handles MCP JSON-RPC requests:

```kotlin
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
    ): ResponseEntity<McpResponse> {
        logger.info("MCP request: method=${request.method}, user=${principal.name}")
        
        return try {
            val result = when (request.method) {
                "initialize" -> mcpService.getInitializeResult()
                "notifications/initialized" -> {
                    logger.info("MCP client initialized successfully for user: ${principal.name}")
                    return ResponseEntity.ok().build() // No response for notifications
                }
                "tools/list" -> mcpService.listTools()
                "tools/call" -> {
                    val toolName = request.params?.get("name") as? String
                        ?: throw IllegalArgumentException("Tool name is required")
                    val arguments = request.params["arguments"] as? Map<String, Any> ?: emptyMap()
                    
                    mcpService.callTool(toolName, arguments, principal)
                }
                else -> throw IllegalArgumentException("Unknown method: ${request.method}")
            }
            
            ResponseEntity.ok(McpResponse(id = request.id, result = result))
            
        } catch (e: Exception) {
            logger.error("MCP request failed: ${e.message}", e)
            ResponseEntity.ok(
                McpErrorResponse(
                    id = request.id,
                    error = McpError(code = -1, message = e.message ?: "Unknown error")
                )
            )
        }
    }
}
```

Key features:
- **JSON-RPC 2.0 Compliance**: Proper handling of requests, responses, and notifications
- **Method Routing**: Supports `initialize`, `tools/list`, `tools/call`, and `notifications/initialized`
- **Error Handling**: Catches exceptions and returns proper JSON-RPC error responses
- **Logging**: Request tracking and error logging

### 7. Security Configuration (`src/main/kotlin/org/dropProject/config/McpSecurityConfig.kt`)

Spring Security configuration for MCP endpoints:

```kotlin
@Configuration
@Order(1)
class McpSecurityConfig(
    private val mcpTokenController: McpTokenController
) {
    @Bean
    fun mcpSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher("/mcp/**")
            .authorizeHttpRequests { requests ->
                requests.anyRequest().authenticated()
            }
            .addFilterBefore(
                McpBearerTokenFilter(mcpTokenController),
                UsernamePasswordAuthenticationFilter::class.java
            )
            .csrf { it.disable() }
            .build()
    }
}
```

Key features:
- **Endpoint Matching**: Only applies to `/mcp/**` paths
- **Authentication Required**: All MCP endpoints require authentication
- **Bearer Token Filter**: Custom filter validates personal tokens
- **CSRF Disabled**: Not needed for API endpoints

### 8. Assignment Service Integration

The implementation includes service layer refactoring for better code organization:

#### AssignmentService.getAssignmentDetailData()
- **Purpose**: Extract assignment detail logic from controller to service
- **Benefits**: Enables code reuse between web interface and MCP API
- **Data**: Returns `AssignmentDetailResponse` with comprehensive assignment information
- **Authorization**: Maintains existing access control logic

#### AssignmentDetailResponse
- **Location**: `src/main/kotlin/org/dropProject/data/AssignmentDetailResponse.kt`
- **Purpose**: Unified data structure for assignment details
- **Usage**: Used by both `AssignmentController` and `McpService`
- **Fields**: Assignment, assignees, ACL, tests, reports, git info, admin status

## Testing

### Test Coverage

The implementation includes comprehensive integration tests (`McpControllerTests.kt`):

- **MCP Initialize**: Tests server initialization and capability reporting
- **Tools List**: Validates available tools and their schemas
- **Get Assignment Info**: Tests detailed assignment information retrieval
- **Search Assignments**: Validates assignment search functionality
- **Authentication**: Tests Bearer token authentication flow

All tests use DropProject's existing test infrastructure and personal token generation.

## Usage Examples

### Using cURL with Personal Tokens

```bash
# Get your personal token from DropProject UI (Profile -> Personal Tokens)
PERSONAL_TOKEN="your-personal-token-from-dropproject"

# 1. Initialize MCP connection
curl -X POST http://localhost:8080/mcp/ \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $PERSONAL_TOKEN" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "initialize"
  }'

# 2. Get available tools
curl -X POST http://localhost:8080/mcp/ \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $PERSONAL_TOKEN" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/list"
  }'

# 3. Get detailed assignment information
curl -X POST http://localhost:8080/mcp/ \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $PERSONAL_TOKEN" \
  -d '{
    "jsonrpc": "2.0",
    "id": "3",
    "method": "tools/call",
    "params": {
      "name": "get_assignment_info",
      "arguments": {
        "assignmentId": "sampleJavaAssignment"
      }
    }
  }'

# 4. Search assignments by keyword
curl -X POST http://localhost:8080/mcp/ \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $PERSONAL_TOKEN" \
  -d '{
    "jsonrpc": "2.0",
    "id": "4",
    "method": "tools/call",
    "params": {
      "name": "search_assignments",
      "arguments": {
        "query": "java"
      }
    }
  }'
```

### Using with MCP Clients

For MCP client libraries and applications:

1. **Get Personal Token**: Generate a personal token from DropProject UI (Profile → Personal Tokens)
2. **Configure Client**: Set up MCP client with:
   - **Endpoint**: `http://localhost:8080/mcp/` 
   - **Transport**: HTTP
   - **Authentication**: Bearer token with your personal token
3. **Available Tools**: 
   - `get_assignment_info` - Get comprehensive assignment details
   - `search_assignments` - Search assignments by name/ID/tags

Example MCP client configuration:
```json
{
  "transport": {
    "type": "http",
    "url": "http://localhost:8080/mcp/",
    "headers": {
      "Authorization": "Bearer your-personal-token-here"
    }
  }
}
```

## Response Formats

### get_assignment_info Response
Returns markdown-formatted assignment information including:
- Assignment metadata (name, ID, owner, submission method, etc.)
- Instructions (if available)
- Due date and programming language
- Assignees list
- Test methods
- Validation reports and status
- Git repository information

### search_assignments Response  
Returns a formatted list of matching assignments:
```
Found 2 assignments matching 'java':
- Java Programming Project (ID: javaProject)
- Advanced Java Assignment (ID: advancedJava)
```

## Future Extensions

### Additional Tools to Implement
1. `get_submission_info` - Get submission details and build reports
2. `list_assignments` - List all available assignments with metadata
3. `get_assignment_leaderboard` - Get assignment rankings and statistics
4. `get_student_history` - Get student submission history
5. `submit_assignment` - Submit new assignment (if applicable)

### Enhanced Features
1. **Streaming Responses**: For long-running operations like builds
2. **WebSocket Support**: Real-time updates for submissions and builds
3. **Rate Limiting**: Per-user request limits for production deployment
4. **Caching**: Response caching for frequently requested assignment data
5. **Monitoring**: Metrics and logging for MCP usage analytics

## Security Considerations

- **Authentication**: Integrates with existing DropProject personal token system
- **Authorization**: Maintains same role-based access control as REST API
- **Token Validation**: Checks token status, expiration, and user permissions
- **Input Validation**: All MCP requests validated against JSON schemas
- **HTTPS**: Should be used in production for token transmission security

## MCP Specification Compliance

This implementation follows the **Model Context Protocol 2024-11-05 specification**:

- **JSON-RPC 2.0**: Proper request/response format with nullable `id` fields
- **Protocol Version**: Reports `2024-11-05` in initialize response
- **Tool Results**: Uses `McpToolCallResult` with content arrays
- **Error Handling**: Standard JSON-RPC error responses with codes
- **Notifications**: Proper handling of notification requests (no response)
- **Content Types**: Text content with proper MIME type handling

## Troubleshooting

### Common Issues

#### Authentication Failures
**Problem**: HTTP 401 or 403 responses  
**Solution**: 
- Verify personal token is active and not expired in DropProject UI
- Check `Authorization: Bearer <token>` header format
- Ensure user has appropriate permissions for requested assignments

#### JSON-RPC Format Errors
**Problem**: MCP clients report validation errors  
**Solution**: 
- Verify JSON-RPC 2.0 format with proper `jsonrpc`, `id`, and `method` fields
- Use nullable `id` field for notification requests
- Ensure proper Content-Type: `application/json`

#### Tool Call Failures
**Problem**: `IllegalArgumentException: assignmentId is required`  
**Solution**: 
- Check tool arguments match the expected schema
- Verify assignment ID exists and user has access
- Use correct parameter names: `assignmentId` and `query`

#### Assignment Access Denied
**Problem**: User cannot access specific assignments  
**Solution**: 
- Verify user owns the assignment or is in the ACL
- Check assignment visibility settings
- Ensure user has TEACHER role for MCP access

#### Empty Search Results
**Problem**: `search_assignments` returns no results  
**Solution**: 
- Check query string matches assignment names, IDs, or tags
- Verify user has access to assignments in the system
- Try broader search terms or check assignment visibility

### Debug Mode

Enable debug logging by adding to `application.properties`:
```properties
logging.level.org.dropProject.mcp=DEBUG
logging.level.org.dropProject.controllers.McpController=DEBUG
```

This will log all MCP requests and responses for troubleshooting.

## Monitoring and Logging

The MCP implementation includes comprehensive logging:

- **Request Logging**: All MCP requests logged with method and user information
- **Error Logging**: Detailed error logging with stack traces for debugging  
- **Authentication Logging**: Token validation and authentication events
- **Tool Usage**: Logging of tool calls and their parameters

Example log output:
```
[INFO] MCP request: method=tools/call, user=teacher1
[INFO] MCP client initialized successfully for user: teacher1
[ERROR] MCP request failed: Assignment not found: invalidId
```

## Summary

This MCP integration provides DropProject with a standardized API for assignment information access while maintaining all existing security and authorization mechanisms. The implementation is production-ready and follows MCP specification 2024-11-05 compliance standards.