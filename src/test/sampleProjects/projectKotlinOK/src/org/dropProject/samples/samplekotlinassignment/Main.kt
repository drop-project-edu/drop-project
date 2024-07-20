package org.dropproject.samples.samplekotlinassignment

fun findMax(numbers: Array<Int>): Int {
    var result = Int.MIN_VALUE
    for (number in numbers) {
        if (number > result) {
            result = number
        }
    }

    return result
}

fun main() {
    println("Sample!")
}

