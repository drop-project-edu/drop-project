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
package org.dropproject.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.dropproject.filters.ControllerRequestsLoggingFilter


@Configuration
class ControllerRequestsLoggingFilterConfig {

    // ********** IMPORTANT **********
    // don't forget to include the following line in drop-project.properties:
    // logging.level.org.springframework.web.filter.ControllerRequestsLoggingFilter=INFO

    @Bean
    fun logFilter(): ControllerRequestsLoggingFilter {
        val filter = ControllerRequestsLoggingFilter()
        return filter
    }
}
