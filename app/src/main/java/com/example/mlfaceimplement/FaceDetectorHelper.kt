package com.example.mlfaceimplement

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

@OptIn(ExperimentalGetImage::class)
class FaceDetectorHelper {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(
                FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
            ) // ← change
            .setClassificationMode(
                FaceDetectorOptions.CLASSIFICATION_MODE_ALL
            )
            .build()
    )

    fun detect(imageProxy: ImageProxy, onFace: (Face, Bitmap) -> Unit) {

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        detector.process(image)
            .addOnSuccessListener { faces ->

                Log.d("FACE", "faces found = ${faces.size}")

                if (faces.isNotEmpty()) {

                    val face = faces[0]

                    // convert AFTER detection
                    val bmp = imageProxy.toBitmap()

                    val box = face.boundingBox

                    val marginX = (box.width() * 0.20f).toInt()
                    val marginY = (box.height() * 0.25f).toInt()

                    val left = (box.left - marginX).coerceAtLeast(0)
                    val top = (box.top - marginY).coerceAtLeast(0)
                    val right = (box.right + marginX).coerceAtMost(bmp.width)
                    val bottom = (box.bottom + marginY).coerceAtMost(bmp.height)


                    if (right > left && bottom > top) {
                        val faceBmp = Bitmap.createBitmap(
                            bmp,
                            left,
                            top,
                            right - left,
                            bottom - top
                        )

                        onFace(face, faceBmp)
                    }
                }

                imageProxy.close() // ✅ REQUIRED
            }

            .addOnFailureListener { e ->
                Log.e("FACE", "Detection failed", e)
                imageProxy.close() // ✅ REQUIRED
            }
    }
}
