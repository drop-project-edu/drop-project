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
package org.dropProject.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration of Swagger, responsible for generating the API documentation
 * accessible on <HOST_URL>/swagger-ui.html
 */
@Configuration
class SwaggerConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .components(Components().addSecuritySchemes("basicScheme", SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
            .info(Info().title("Drop Project API").version("1").description("All endpoints of the Drop Project API"))
            .addSecurityItem(SecurityRequirement().addList("basicScheme"))
    }

    @Bean
    fun studentAndTeacherApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("student-teacher-public")
            .pathsToMatch("/api/student/**", "/api/teacher/**")
            .build()
    }
}