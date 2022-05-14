package org.dropProject


interface AsyncTimeoutConfigurer {

    fun getTimeout(): Int
    fun setTimeout(timeout: Int)
}