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
package org.dropproject

import org.dropproject.config.PendingTasks
import org.dropproject.services.CooloffOverrideService
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.cache.CacheManager
import org.springframework.test.context.junit.jupiter.SpringExtension
import javax.sql.DataSource

/**
 * Resets all state shared between tests: database contents (including identity counters),
 * spring caches and in-memory service state. Much cheaper than @DirtiesContext, which throws
 * away the whole spring context and forces the next test to boot the application again.
 */
class ResetStateExtension : AfterEachCallback {

    override fun afterEach(context: ExtensionContext) {
        val appContext = SpringExtension.getApplicationContext(context)

        // truncate all tables, restarting identity counters since some tests
        // rely on ids being generated from 1
        val dataSource = appContext.getBean(DataSource::class.java)
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE")
                val tables = mutableListOf<String>()
                statement.executeQuery(
                    "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                            "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'"
                ).use { rs ->
                    while (rs.next()) {
                        tables.add(rs.getString(1))
                    }
                }
                for (table in tables) {
                    statement.executeUpdate("TRUNCATE TABLE \"$table\" RESTART IDENTITY")
                }
                statement.execute("SET REFERENTIAL_INTEGRITY TRUE")
            }
        }

        // clear spring caches (e.g. the archived assignments cache)
        val cacheManager = appContext.getBean(CacheManager::class.java)
        cacheManager.cacheNames.forEach { cacheManager.getCache(it)?.clear() }

        // clear in-memory service state
        appContext.getBean(CooloffOverrideService::class.java).clearAll()
        appContext.getBean(PendingTasks::class.java).clear()

        // restore the real git and maven behavior for tests that don't use the fakes
        appContext.getBean(FakeGitClient::class.java).clearRemotes()
        appContext.getBean(FakeBuildRunner::class.java).reset()
    }
}
