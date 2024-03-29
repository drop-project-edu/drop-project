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

import net.sf.ehcache.config.CacheConfiguration
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.ehcache.EhCacheCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.PropertySource
import org.springframework.scheduling.annotation.EnableScheduling


@SpringBootApplication
@EnableScheduling
@EnableCaching
@PropertySource("classpath:drop-project.properties")
class DropProjectApplication : SpringBootServletInitializer() {

    @Bean
    fun cacheManager(): CacheManager {
        val archivedAssignmentsCacheConfig = CacheConfiguration(Constants.CACHE_ARCHIVED_ASSIGNMENTS_KEY, 10)
        val generalConfig = net.sf.ehcache.config.Configuration()
        generalConfig.addCache(archivedAssignmentsCacheConfig)
        return EhCacheCacheManager(net.sf.ehcache.CacheManager.newInstance(generalConfig))
    }

    override fun configure(application: SpringApplicationBuilder): SpringApplicationBuilder {
        // to prevent "Cannot forward to error page for request [...] as the response has already been committed"
        setRegisterErrorPageFilter(false);

        return application.sources(DropProjectApplication::class.java).properties("spring.config.name: drop-project")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            System.setProperty("spring.config.name", "drop-project")
            SpringApplication.run(DropProjectApplication::class.java, *args)
        }
    }
}


//fun main(args: Array<String>) {
//    System.setProperty("spring.config.name", "drop-project")
//    SpringApplication.run(DropProjectApplication::class.java, *args)
//}




