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
package org.dropProject

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Description
import org.thymeleaf.extras.springsecurity4.dialect.SpringSecurityDialect
import org.thymeleaf.spring4.SpringTemplateEngine
import org.thymeleaf.templateresolver.TemplateResolver
import org.dom4j.dom.DOMNodeHelper.setPrefix
import org.springframework.context.ApplicationContext
import org.thymeleaf.spring4.templateresolver.SpringResourceTemplateResolver

@Configuration
class ThymeleafConfig() {

    @Bean
    @Description("Thymeleaf template engine with Spring integration")
    fun templateEngine(templateResolvers: Set<TemplateResolver>): SpringTemplateEngine {
        val engine = SpringTemplateEngine()
        engine.templateResolvers = templateResolvers

        engine.addDialect(SpringSecurityDialect())
        engine.afterPropertiesSet()
        return engine
    }


    @Bean
    fun xmlTemplateResolver(appCtx: ApplicationContext): SpringResourceTemplateResolver {
        val templateResolver = SpringResourceTemplateResolver()
        templateResolver.setApplicationContext(appCtx)
        templateResolver.prefix = "classpath:/templatesXML/"
        templateResolver.suffix = ".xml"
        templateResolver.templateMode = "XML"
        templateResolver.characterEncoding = "UTF-8"
        templateResolver.isCacheable = true
        return templateResolver
    }
}
