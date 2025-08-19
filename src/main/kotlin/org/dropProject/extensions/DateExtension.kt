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
package org.dropproject.extensions

import java.text.SimpleDateFormat
import java.util.*

/**
 * This file contains extensions to the default [Date] class.
 */

fun Date.formatJustDate(): String {
    val sdf= SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return sdf.format(this)
}

fun Date.formatDefault(): String {
    val sdf= SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(this)
}

fun Date.format(format: String): String {
    val sdf= SimpleDateFormat(format, Locale.getDefault())
    return sdf.format(this)
}
    
    
