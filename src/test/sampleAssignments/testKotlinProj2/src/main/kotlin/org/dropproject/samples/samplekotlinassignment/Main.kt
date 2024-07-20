package org.dropproject.samples.samplekotlinassignment

fun findMax(numbers: Array<Int>): Int {
    var max = Int.MIN_VALUE
    for (number in numbers) {
        if (number > max) {
            max = number
        }
    }

    return max
}

fun main(args: Array<String>) {
    val numbers = arrayOf( 1, 3, 7, 4, 2)
    println("max = ${findMax(numbers)}")
}