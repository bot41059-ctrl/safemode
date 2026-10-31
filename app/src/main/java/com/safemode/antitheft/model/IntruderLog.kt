package com.safemode.antitheft.model

data class IntruderLog(
    val id: String,
    val timestamp: String,
    val photoPath: String,
    val batteryLevel: Int,
    val reason: String
)
