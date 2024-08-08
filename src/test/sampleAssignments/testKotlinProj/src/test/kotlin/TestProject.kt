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

import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class TestProject {

    val outContent = ByteArrayOutputStream()
    val originalOut = System.out
    val originalIn = System.`in`

    @Before
    fun setupStdout() {
        System.setOut(PrintStream(outContent))
    }

    @After
    fun restoreStdoutAndStdin() {
        System.setOut(originalOut)
        System.setIn(originalIn)
    }

    @Test
    fun whenAdding1and3_thenAnswerIs4() {

        val inContent = ByteArrayInputStream("1\n3\n".toByteArray())
        System.setIn(inContent)

        main(emptyArray())

        Assert.assertEquals("Introduza o primeiro número\nIntroduza o segundo número\n1 + 3 = 4\n", outContent.toString())
    }

    @Test
    fun whenAdding2and5_thenAnswerIs7() {

        val inContent = ByteArrayInputStream("2\n5\n".toByteArray())
        System.setIn(inContent)

        main(emptyArray())

        Assert.assertEquals("Introduza o primeiro número\nIntroduza o segundo número\n2 + 5 = 7\n", outContent.toString())
    }
}
