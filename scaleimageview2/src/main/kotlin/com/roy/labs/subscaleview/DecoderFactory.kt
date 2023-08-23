package com.roy.labs.subscaleview

interface DecoderFactory<T> {
    fun make(): T
}
