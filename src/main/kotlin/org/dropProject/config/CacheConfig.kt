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
package org.dropproject.config

import org.dropproject.Constants
import org.springframework.cache.CacheManager
import org.springframework.cache.jcache.JCacheCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.cache.Caching
import javax.cache.configuration.MutableConfiguration

@Configuration
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        val cachingProvider = Caching.getCachingProvider()
        val cacheManager = cachingProvider.cacheManager
        val cacheConfiguration = MutableConfiguration<Any, Any>()
        if (cacheManager.getCache<Any, Any>(Constants.CACHE_ARCHIVED_ASSIGNMENTS_KEY) == null) {
            cacheManager.createCache(Constants.CACHE_ARCHIVED_ASSIGNMENTS_KEY, cacheConfiguration)
        }
        return JCacheCacheManager(cacheManager)
    }
}