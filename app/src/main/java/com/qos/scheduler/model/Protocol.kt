package com.qos.scheduler.model

enum class Protocol(val number: Int) {
    TCP(6),
    UDP(17),
    OTHER(-1);

    companion object {
        fun fromNumber(n: Int) = entries.find { it.number == n } ?: OTHER
    }
}
