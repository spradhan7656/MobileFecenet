package com.example.mlfaceimplement

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
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
import com.google.mlkit.vision.face.Face
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView

    private val CAMERA_PERMISSION_CODE = 1001

    private lateinit var imageCapture: ImageCapture
    private lateinit var db: AppDatabase
    private lateinit var faceHelper: FaceEmbeddingHelper

    private lateinit var homeLayout: LinearLayout
    private lateinit var okButton: Button
    private lateinit var btnRegister: Button
    private lateinit var btnFind: Button

    private lateinit var progressLayout: LinearLayout
    private lateinit var registerProgress: ProgressBar
    private lateinit var registerPercent: TextView

    enum class Mode { REGISTER, FIND }
    private var currentMode: Mode? = null

    private val faceDetector = FaceDetectorHelper()

    private val registerBuffer = mutableListOf<FloatArray>()
    private var collecting = false
    private var lastCaptureTime = 0L

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
//                showCamera()
            } else {
                Toast.makeText(
                    this,
                    "Camera permission required",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        homeLayout = findViewById(R.id.homeLayout)
        okButton = findViewById(R.id.btnOk)
        btnRegister = findViewById(R.id.btnRegister)
        btnFind = findViewById(R.id.btnFind)

        progressLayout = findViewById(R.id.progressLayout)
        registerProgress = findViewById(R.id.registerProgress)
        registerPercent = findViewById(R.id.registerPercent)

        db = AppDatabase.get(this)
        faceHelper = FaceEmbeddingHelper(this)

        if (!hasCameraPermission()) {
            requestCameraPermission()
        }

        btnRegister.setOnClickListener {
            currentMode = Mode.REGISTER
            registerBuffer.clear()
            collecting = true
            lastCaptureTime = 0
            registerProgress.progress = 0
            registerPercent.text = "0%"

            if (hasCameraPermission()) {
                showCamera()
            } else {
                requestCameraPermission()
            }
        }

        btnFind.setOnClickListener {
            currentMode = Mode.FIND
            collecting = false
            if (hasCameraPermission()) {
                showCamera()
            } else {
                requestCameraPermission()
            }
        }

        okButton.setOnClickListener {
            captureAndDetect()
        }
    }

    private fun captureAndDetect() {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(img: ImageProxy) {

                    faceDetector.detect(img) { faceObj, faceBmp ->
                        if (!isFullFaceSimple(faceObj)) return@detect
                        findNameAndShowDialog(faceBmp)
                    }
                }
            })
    }


    private fun showCamera() {
        homeLayout.visibility = View.GONE
        previewView.visibility = View.VISIBLE

        if (currentMode == Mode.REGISTER) {
            okButton.visibility = View.GONE
            progressLayout.visibility = View.VISIBLE
        } else {
            okButton.visibility = View.VISIBLE
            progressLayout.visibility = View.GONE
        }

        startCamera()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({

            val provider = future.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(
                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                )
                .build()


            analysis.setAnalyzer(ContextCompat.getMainExecutor(this), frameAnalyzer)

            provider.unbindAll()

            if (currentMode == Mode.REGISTER)
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            else
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageCapture)

        }, ContextCompat.getMainExecutor(this))
    }

    private fun frameProgressUpdate() {
        val percent = (registerBuffer.size * 100) / 10
        registerProgress.progress = percent
        registerPercent.text = "$percent%"
    }

    private val frameAnalyzer = ImageAnalysis.Analyzer { imageProxy ->

        if (!collecting) {
            imageProxy.close()
            return@Analyzer
        }

        faceDetector.detect(imageProxy) { faceObj, faceBmp ->

            if (!isFullFaceSimple(faceObj)) return@detect

            val now = System.currentTimeMillis()
            if (now - lastCaptureTime < 600) return@detect
            lastCaptureTime = now

            registerBuffer.add(faceHelper.getEmbedding(faceBmp))

            runOnUiThread { frameProgressUpdate() }

            if (registerBuffer.size >= 10) {
                collecting = false
                runOnUiThread { showNameDialogAndRegisterMultiple() }
            }
        }
    }


    private fun showNameDialogAndRegisterMultiple() {
        val input = EditText(this)

        AlertDialog.Builder(this)
            .setTitle("Enter your name")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val name = input.text.toString().trim()
                registerBuffer.forEach {
                    db.userDao().insert(UserEntity(name = name, embedding = it))
                }
                resetUI()
            }
            .show()
    }

    private fun findNameAndShowDialog(face: Bitmap) {

        val probe = faceHelper.getEmbedding(face)
        val rows = db.userDao().getAll()
        val grouped = rows.groupBy { it.name }

        var bestName = "Unknown"
        var bestScore = Float.MAX_VALUE

        for ((name, list) in grouped) {
            val dists = list.map { cosineDistance(it.embedding, probe) }

            Log.d("MATCH", "$name -> $dists")

            val score = dists.sorted().take(3).average().toFloat()

            if (score < bestScore) {
                bestScore = score
                bestName = name
            }
        }


        val result = if (bestScore < 0.70f) bestName else "Unknown"


        AlertDialog.Builder(this)
            .setTitle("Result")
            .setMessage("Your name is: $result")
            .setPositiveButton("OK") { _, _ -> resetUI() }
            .show()
    }

    private fun resetUI() {
        previewView.visibility = View.GONE
        okButton.visibility = View.GONE
        progressLayout.visibility = View.GONE
        homeLayout.visibility = View.VISIBLE
        registerBuffer.clear()
    }

    private fun cosineDistance(a: FloatArray, b: FloatArray): Float {

        if (a.size != b.size) return Float.MAX_VALUE

        var dot = 0f
        var na = 0f
        var nb = 0f

        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }

        val denom = sqrt(na) * sqrt(nb)

        if (denom == 0f || denom.isNaN()) return Float.MAX_VALUE

        val cos = (dot / denom).coerceIn(-1f, 1f)

        return 1f - cos
    }


    private fun isFullFaceSimple(face: Face): Boolean {
        if (kotlin.math.abs(face.headEulerAngleY) > 15) return false
        if (kotlin.math.abs(face.headEulerAngleZ) > 15) return false
        return (face.leftEyeOpenProbability ?: 0f) > 0.5f &&
                (face.rightEyeOpenProbability ?: 0f) > 0.5f
    }

}
