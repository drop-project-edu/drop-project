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
fun SomeFunc(Param: String) {

    if (Param == "ola")
        println("ole")

}

fun main(args: Array<String>) {
    println("Introduza o primeiro número")
    val num1 = readLine()!!.toInt()

    println("Introduza o segundo número")
    val num2 = readLine()!!.toInt()

    var Soma = num1 + num2

    println("${num1} + ${num2} = ${Soma}")
}
