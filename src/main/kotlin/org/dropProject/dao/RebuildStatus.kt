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
package org.dropproject.dao

import jakarta.persistence.*
import java.util.*

/**
 * Tracks when a teacher-triggered rebuild started for a [Submission].
 *
 * This exists separately from [Submission.statusDate] because entering the REBUILDING status
 * deliberately doesn't update statusDate (so that "rebuild without changing anything" really doesn't
 * change the submission's visible date, which is used for leaderboard ordering and "most recent submission"
 * selection). Without a separate timestamp, a stale-submission sweep would have no reliable way to tell
 * how long a rebuild has actually been running.
 *
 * @property id is a primary-key like generated value
 * @property submission is the [Submission] being rebuilt. This is a @OneToOne only on this side (unidirectional),
 * so loading a Submission never has to fetch or join against this table.
 * @property startedAt is the Date when the rebuild started
 */
@Entity
data class RebuildStatus(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long = 0,

        @OneToOne
        @JoinColumn(name = "submission_id", unique = true, nullable = false)
        val submission: Submission,

        val startedAt: Date = Date()
)