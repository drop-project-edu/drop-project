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
package org.dropProject

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.ApiInfo
import springfox.documentation.service.BasicAuth
import springfox.documentation.service.Contact
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger2.annotations.EnableSwagger2
import java.security.Principal
import javax.servlet.ServletContext

/**
 * Configuration of Swagger, responsible for generating the API documentation
 * accessible on <HOST_URL>/swagger-ui/
 */
@Configuration
@EnableSwagger2
class SwaggerConfig {

    @Bean
    fun api(servletContext: ServletContext): Docket {
        return Docket(DocumentationType.SWAGGER_2)
            .select()
            .apis(RequestHandlerSelectors.any())
            .paths(PathSelectors.ant("${servletContext.contextPath}/api/**"))
            .build()
            .apiInfo(metadata())
            .securitySchemes(listOf(BasicAuth("basicAuth")))
            .ignoredParameterTypes(Principal::class.java)
    }

    fun metadata() = ApiInfo("Drop Project API", "All endpoints of the Drop Project API", "1", "#",
        Contact("Pedro Alves", "https://github.com/drop-project-edu/drop-project", "pedro.alves@ulusofona.pt"),
        "Apache 2.0", "http://www.apache.org/licenses/LICENSE-2.0",
        emptyList())
}
