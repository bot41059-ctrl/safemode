package com.safemode.antitheft.model

data class FaceVector(
    val angleLabel: String,
    val eyeDistanceRatio: Float,
    val noseToMouthRatio: Float,
    val faceBoxAspectRatio: Float,
    val leftEyeToNoseRatio: Float,
    val rightEyeToNoseRatio: Float
)
