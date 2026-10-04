package com.example.camera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class CameraActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var btnCapturePro: Button
    private lateinit var btnOpenSettings: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnRawToggle: Button
    private lateinit var btnRatioToggle: Button

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var isRawEnabled = false

    private var currentRatioIndex = 0
    private val ratios = arrayOf("4:3", "16:9")

    // State untuk melacak lensa kamera (Default: Belakang)
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        viewFinder = findViewById(R.id.viewFinder)
        btnCapturePro = findViewById(R.id.btnCapturePro)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnRawToggle = findViewById(R.id.btnRawToggle)
        btnRatioToggle = findViewById(R.id.btnRatioToggle)

        if (allPermissionsGranted()) {
            startProCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        btnOpenSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // Logika Tombol Switch Kamera
        btnSwitchCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            startProCamera()
        }

        btnRawToggle.setOnClickListener {
            isRawEnabled = !isRawEnabled
            if (isRawEnabled) {
                btnRawToggle.text = "RAW: ON"
                Toast.makeText(this, "Format RAW diaktifkan", Toast.LENGTH_SHORT).show()
            } else {
                btnRawToggle.text = "RAW"
                Toast.makeText(this, "Format RAW dimatikan", Toast.LENGTH_SHORT).show()
            }
        }

        btnRatioToggle.setOnClickListener {
            currentRatioIndex = (currentRatioIndex + 1) % ratios.size
            val selectedRatio = ratios[currentRatioIndex]
            btnRatioToggle.text = selectedRatio
            Toast.makeText(this, "Rasio diubah ke $selectedRatio", Toast.LENGTH_SHORT).show()
            startProCamera()
        }

        btnCapturePro.setOnClickListener {
            takeEnhancedPhoto()
        }
    }

    private fun startProCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

                val aspectRatio = if (ratios[currentRatioIndex] == "16:9") {
                    AspectRatio.RATIO_16_9
                } else {
                    AspectRatio.RATIO_4_3
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetAspectRatio(aspectRatio)
                    .build()

                // Menggunakan selector berdasarkan variabel lensFacing aktif
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Gagal menginisialisasi kamera: ${exc.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takeEnhancedPhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(baseContext, "Gagal mengambil foto: ${exc.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = image.toBitmap()
                        image.close()

                        val enhancedBitmap = applyComputationalEnhancement(bitmap)
                        saveBitmapToGallery(enhancedBitmap)
                    } catch (e: Exception) {
                        Toast.makeText(baseContext, "Kesalahan pemrosesan gambar: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun applyComputationalEnhancement(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)

        val canvas = Canvas(dest)
        val paint = Paint()

        val colorMatrix = ColorMatrix().apply {
            setSaturation(1.15f)
        }

        val scaleMatrix = ColorMatrix(
            floatArrayOf(
                1.1f, 0f, 0f, 0f, 10f,
                0f, 1.1f, 0f, 0f, 10f,
                0f, 0f, 1.1f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        colorMatrix.postConcat(scaleMatrix)

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)

        return dest
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Enhanced_$name.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ProCameraApp/Enhanced")
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            val outputStream: OutputStream? = contentResolver.openOutputStream(it)
            outputStream?.let { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                stream.close()
                Toast.makeText(this, "Foto berhasil ditingkatkan & disimpan!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startProCamera()
            } else {
                Toast.makeText(this, "Izin kamera wajib diaktifkan.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
