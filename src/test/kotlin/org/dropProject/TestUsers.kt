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

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import java.util.Base64

/**
 * Builds the basic-auth authorization header used by the API endpoints (username + personal token).
 */
fun basicAuthHeader(username: String, personalToken: String) =
    "basic ${Base64.getEncoder().encodeToString("$username:$personalToken".toByteArray())}"

/**
 * The users shared by all tests. They match the users defined in the test users.csv.
 */
object TestUsers {
    val STUDENT_1 = User("student1", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
    val STUDENT_2 = User("student2", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
    val STUDENT_3 = User("student3", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
    val STUDENT_4 = User("student4", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
    val STUDENT_5 = User("student5", "", mutableListOf(SimpleGrantedAuthority("ROLE_STUDENT")))
    val TEACHER_1 = User("teacher1", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))
    val TEACHER_2 = User("teacher2", "", mutableListOf(SimpleGrantedAuthority("ROLE_TEACHER")))
}
