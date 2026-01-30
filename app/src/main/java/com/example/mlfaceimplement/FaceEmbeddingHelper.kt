package com.example.mlfaceimplement

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class FaceEmbeddingHelper(context: Context) {

    private val interpreter: Interpreter

    init {
        interpreter = Interpreter(loadModelFile(context))
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("mobilefacenet.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )
    }

    fun getEmbedding(bitmap: Bitmap): FloatArray {

        // Resize to 112x112
        val resized = Bitmap.createScaledBitmap(bitmap, 112, 112, true)

        // Input tensor [1,112,112,3]
        val input = Array(1) { Array(112) { Array(112) { FloatArray(3) } } }

        for (y in 0 until 112) {
            for (x in 0 until 112) {
                val px = resized.getPixel(x, y)
                input[0][y][x][0] = (Color.red(px) - 128f) / 128f
                input[0][y][x][1] = (Color.green(px) - 128f) / 128f
                input[0][y][x][2] = (Color.blue(px) - 128f) / 128f
            }
        }

        // Output embedding (MobileFaceNet = 192)
        val output = Array(1) { FloatArray(192) }

        interpreter.run(input, output)

        return output[0]
    }
}


