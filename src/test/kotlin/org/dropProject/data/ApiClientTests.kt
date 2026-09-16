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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApiClientTests {

    @Test
    fun `identify the plugin and its version`() {
        assertEquals(
            ApiClient.Plugin(PluginVersion(0, 9, 15)),
            ApiClient.fromUserAgent("DropProjectPlugin/0.9.15"))
        assertEquals(
            ApiClient.Plugin(PluginVersion(0, 9, 15)),
            ApiClient.fromUserAgent("DropProjectPlugin/0.9.15 (IntelliJ IDEA 2024.3; okhttp/4.12.0)"))
        assertEquals(
            ApiClient.Plugin(PluginVersion(0, 9, 15, "beta")),
            ApiClient.fromUserAgent("DropProjectPlugin/0.9.15-beta (IntelliJ IDEA 2024.3)"))
    }

    @Test
    fun `identify a plugin that is too old to report a version`() {
        // up to 0.9.14 the plugin set no user agent, so okhttp filled in its own
        assertEquals(ApiClient.UnidentifiedPlugin, ApiClient.fromUserAgent("okhttp/4.12.0"))
        assertEquals(ApiClient.UnidentifiedPlugin, ApiClient.fromUserAgent("okhttp/5.0.0-alpha.14"))
    }

    @Test
    fun `a plugin that reports a version that cannot be read is treated as outdated`() {
        assertEquals(ApiClient.UnidentifiedPlugin, ApiClient.fromUserAgent("DropProjectPlugin/unknown"))
        assertEquals(ApiClient.UnidentifiedPlugin, ApiClient.fromUserAgent("DropProjectPlugin/0.9.x"))
    }

    @Test
    fun `everything else is not a plugin`() {
        assertEquals(ApiClient.Other, ApiClient.fromUserAgent(null))
        assertEquals(ApiClient.Other, ApiClient.fromUserAgent(""))
        assertEquals(ApiClient.Other, ApiClient.fromUserAgent("curl/8.7.1"))
        assertEquals(ApiClient.Other, ApiClient.fromUserAgent("PostmanRuntime/7.39.0"))
        assertEquals(ApiClient.Other, ApiClient.fromUserAgent(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131"))
        // the product token only counts at the front of the header, where the client names itself
        assertEquals(ApiClient.Other, ApiClient.fromUserAgent("curl/8.7.1 DropProjectPlugin/9.9.9"))
    }
}
