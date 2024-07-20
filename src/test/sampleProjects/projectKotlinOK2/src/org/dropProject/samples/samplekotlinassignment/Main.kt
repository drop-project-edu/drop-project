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

fun someMethod() {
    println("stuff!")
}

fun main() {
    println("Sample copied!")
}

