package com.example.mlfaceimplement

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceDetectorHelper {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    fun detect(bitmap: Bitmap, onFace: (Bitmap) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        detector.process(image).addOnSuccessListener { faces ->
            if (faces.isNotEmpty()) {
                val box = faces[0].boundingBox
                val face = Bitmap.createBitmap(
                    bitmap,
                    box.left.coerceAtLeast(0),
                    box.top.coerceAtLeast(0),
                    box.width().coerceAtMost(bitmap.width),
                    box.height().coerceAtMost(bitmap.height)
                )
                onFace(face)
            }
        }
    }
}
