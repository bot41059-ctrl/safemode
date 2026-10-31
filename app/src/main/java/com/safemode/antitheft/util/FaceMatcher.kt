package com.safemode.antitheft.util

import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.safemode.antitheft.model.FaceVector
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCancellableCoroutine
import kotlin.math.hypot
import kotlin.math.sqrt

object FaceMatcher {

    private val detectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setMinFaceSize(0.15f)
        .build()

    private val detector = FaceDetection.getClient(detectorOptions)

    suspend fun extractFaceVector(bitmap: Bitmap, angleLabel: String): FaceVector? =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val face = faces[0]
                        val vector = computeFaceVector(face, angleLabel)
                        continuation.resume(vector)
                    } else {
                        continuation.resume(null)
                    }
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
        }

    fun computeFaceVector(face: Face, angleLabel: String): FaceVector {
        val bounds = face.boundingBox
        val boxWidth = bounds.width().toFloat().coerceAtLeast(1.0f)
        val boxHeight = bounds.height().toFloat().coerceAtLeast(1.0f)

        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: PointF(0f, 0f)
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: PointF(0f, 0f)
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: PointF(0f, 0f)
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: PointF(0f, 0f)
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: PointF(0f, 0f)

        val mouthCenter = PointF(
            (mouthLeft.x + mouthRight.x) / 2f,
            (mouthLeft.y + mouthRight.y) / 2f
        )

        val eyeDistance = distance(leftEye, rightEye)
        val noseToMouth = distance(nose, mouthCenter)
        val leftEyeToNose = distance(leftEye, nose)
        val rightEyeToNose = distance(rightEye, nose)

        return FaceVector(
            angleLabel = angleLabel,
            eyeDistanceRatio = eyeDistance / boxWidth,
            noseToMouthRatio = noseToMouth / boxHeight,
            faceBoxAspectRatio = boxWidth / boxHeight,
            leftEyeToNoseRatio = leftEyeToNose / boxWidth,
            rightEyeToNoseRatio = rightEyeToNose / boxWidth
        )
    }

    fun isMatch(candidate: FaceVector, registeredList: List<FaceVector>, threshold: Float = 0.28f): Boolean {
        if (registeredList.isEmpty()) return false

        for (reg in registeredList) {
            val dist = calculateDistance(candidate, reg)
            if (dist < threshold) {
                return true
            }
        }
        return false
    }

    private fun calculateDistance(v1: FaceVector, v2: FaceVector): Float {
        val d1 = (v1.eyeDistanceRatio - v2.eyeDistanceRatio)
        val d2 = (v1.noseToMouthRatio - v2.noseToMouthRatio)
        val d3 = (v1.faceBoxAspectRatio - v2.faceBoxAspectRatio)
        val d4 = (v1.leftEyeToNoseRatio - v2.leftEyeToNoseRatio)
        val d5 = (v1.rightEyeToNoseRatio - v2.rightEyeToNoseRatio)

        return sqrt((d1 * d1 + d2 * d2 + d3 * d3 + d4 * d4 + d5 * d5).toDouble()).toFloat()
    }

    private fun distance(p1: PointF, p2: PointF): Float {
        return hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()).toFloat()
    }
}
