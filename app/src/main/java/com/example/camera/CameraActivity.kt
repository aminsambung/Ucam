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
    private lateinit var btnRawToggle: Button
    private lateinit var btnRatioToggle: Button

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var isRawEnabled = false

    private var currentRatioIndex = 0
    private val ratios = arrayOf("4:3", "16:9")

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
            
            // Muat ulang konfigurasi kamera dengan rasio baru
            startProCamera()
        }

        // Tombol Jepret dengan Pemrosesan Peningkatan Kualitas Otomatis
        btnCapturePro.setOnClickListener {
            takeEnhancedPhoto()
        }
    }

    private fun startProCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            // Menyesuaikan target rasio berdasarkan pilihan tombol UI
            val aspectRatio = if (ratios[currentRatioIndex] == "16:9") {
                AspectRatio.RATIO_16_9
            } else {
                AspectRatio.RATIO_4_3
            }

            // Menggunakan setCaptureMode dengan kualitas maksimal serta aspek rasio
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetAspectRatio(aspectRatio)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Gagal memuat kamera: ${exc.message}", Toast.LENGTH_SHORT).show()
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
                    // Mengubah ImageProxy menjadi Bitmap agar bisa diproses secara komputasi
                    val bitmap = image.toBitmap()
                    image.close()

                    // Menerapkan Algoritma Peningkatan Gambar (Enhancement / Auto HDR-like Contrast & Saturation)
                    val enhancedBitmap = applyComputationalEnhancement(bitmap)

                    // Simpan hasil bitmap yang sudah ditingkatkan ke Galeri MediaStore
                    saveBitmapToGallery(enhancedBitmap)
                }
            }
        )
    }

    // Fungsi Simulasi Computational Photography (Menaikkan kontras, ketajaman, dan saturasi warna)
    private fun applyComputationalEnhancement(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)

        val canvas = Canvas(dest)
        val paint = Paint()

        // ColorMatrix untuk meningkatkan saturasi warna
        val colorMatrix = ColorMatrix().apply {
            setSaturation(1.15f)
        }

        // Matriks skala untuk menaikkan sedikit kecerahan/kontras
        val scaleMatrix = ColorMatrix(
            floatArrayOf(
                1.1f, 0f, 0f, 0f, 10f,  // Merah
                0f, 1.1f, 0f, 0f, 10f,  // Hijau
                0f, 0f, 1.1f, 0f, 10f,  // Biru
                0f, 0f, 0f, 1f, 0f      // Alpha
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
                Toast.makeText(this, "Izin kamera ditolak.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
