package com.example.mlfaceimplement

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var imageCapture: ImageCapture
    private lateinit var db: AppDatabase
    private lateinit var faceHelper: FaceEmbeddingHelper

    private lateinit var homeLayout: LinearLayout
    private lateinit var okButton: Button
    private lateinit var btnRegister: Button
    private lateinit var btnFind: Button

    private val CAMERA_REQUEST = 100

    enum class Mode { REGISTER, FIND }
    private var currentMode: Mode? = null

    private val faceDetector = FaceDetectorHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Views
        previewView = findViewById(R.id.previewView)
        homeLayout = findViewById(R.id.homeLayout)
        okButton = findViewById(R.id.btnOk)
        btnRegister = findViewById(R.id.btnRegister)
        btnFind = findViewById(R.id.btnFind)

        // Initial UI state
        previewView.visibility = View.GONE
        okButton.visibility = View.GONE

        // DB & ML
        db = AppDatabase.get(this)
        faceHelper = FaceEmbeddingHelper(this)

        // Permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_REQUEST
            )
        }

        // Register button
        btnRegister.setOnClickListener {
            currentMode = Mode.REGISTER
            showCamera()
        }

        // Find button
        btnFind.setOnClickListener {
            currentMode = Mode.FIND
            showCamera()
        }

        // OK button
        okButton.setOnClickListener {
            capture { bitmap ->
                faceDetector.detect(bitmap) { face ->
                    when (currentMode) {
                        Mode.REGISTER -> showNameDialogAndRegister(face)
                        Mode.FIND -> findNameAndShowDialog(face)
                        else -> {}
                    }
                }
            }
        }
    }

    // Permission callback
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            // permission granted, but camera starts ONLY on button click
        }
    }

    private fun showCamera() {
        homeLayout.visibility = View.GONE
        previewView.visibility = View.VISIBLE
        okButton.visibility = View.VISIBLE
        startCamera()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val selector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

            provider.unbindAll()
            provider.bindToLifecycle(this, selector, preview, imageCapture)

        }, ContextCompat.getMainExecutor(this))
    }

    private fun capture(onBitmap: (Bitmap) -> Unit) {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bmp = image.toBitmap()
                    image.close()
                    onBitmap(bmp)
                }
            }
        )
    }

    private fun showNameDialogAndRegister(face: Bitmap) {
        val input = EditText(this)

        AlertDialog.Builder(this)
            .setTitle("Enter your name")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val embedding = faceHelper.getEmbedding(face)
                db.userDao().insert(
                    UserEntity(
                        name = input.text.toString(),
                        embedding = embedding
                    )
                )
                Toast.makeText(this, "Registered successfully", Toast.LENGTH_SHORT).show()
                resetUI()
            }
            .show()
    }

    private fun findNameAndShowDialog(face: Bitmap) {
        val embedding = faceHelper.getEmbedding(face)
        val users = db.userDao().getAll()

        val match = users.minByOrNull {
            cosineDistance(it.embedding, embedding)
        }

        val name =
            if (match != null && cosineDistance(match.embedding, embedding) < 0.6f)
                match.name
            else "Unknown"

        AlertDialog.Builder(this)
            .setTitle("Result")
            .setMessage("Your name is: $name")
            .setPositiveButton("OK") { _, _ -> resetUI() }
            .show()
    }

    private fun resetUI() {
        previewView.visibility = View.GONE
        okButton.visibility = View.GONE
        homeLayout.visibility = View.VISIBLE
    }

    private fun cosineDistance(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return 1 - (dot / (sqrt(na) * sqrt(nb)))
    }
}
