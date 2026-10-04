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
import android.widget.TextView
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
    
    // Tombol Fungsional Baru
    private lateinit var btnFlashToggle: Button
    private lateinit var btnHdrToggle: Button
    private lateinit var btnMacroToggle: Button

    // Indikator Mode TextView
    private lateinit var modePortrait: TextView
    private lateinit var modeCamera: TextView
    private lateinit var modeVideo: TextView
    private lateinit var modePro: TextView

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var isRawEnabled = false

    private var currentRatioIndex = 0
    private val ratios = arrayOf("4:3", "16:9")

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var currentMode = "PRO"

    // State untuk fitur baru
    private var flashState = 0 // 0: OFF, 1: ON, 2: AUTO
    private var isHdrEnabled = true
    private var isMacroEnabled = false

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
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

        // Inisialisasi Tombol Baru
        btnFlashToggle = findViewById(R.id.btnFlashToggle)
        btnHdrToggle = findViewById(R.id.btnHdrToggle)
        btnMacroToggle = findViewById(R.id.btnMacroToggle)

        // Inisialisasi TextView Mode
        modePortrait = findViewById(R.id.modePortrait)
        modeCamera = findViewById(R.id.modeCamera)
        modeVideo = findViewById(R.id.modeVideo)
        modePro = findViewById(R.id.modePro)

        if (allPermissionsGranted()) {
            startProCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        btnOpenSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        btnSwitchCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            startProCamera()
        }

        // --- Logika Tombol Flash Fungsional ---
        btnFlashToggle.setOnClickListener {
            flashState = (flashState + 1) % 3
            when (flashState) {
                0 -> {
                    btnFlashToggle.text = "⚡ OFF"
                    imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
                    Toast.makeText(this, "Flash Dimatikan", Toast.LENGTH_SHORT).show()
                }
                1 -> {
                    btnFlashToggle.text = "⚡ ON"
                    imageCapture?.flashMode = ImageCapture.FLASH_MODE_ON
                    Toast.makeText(this, "Flash Dinyalakan", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    btnFlashToggle.text = "⚡ AUTO"
                    imageCapture?.flashMode = ImageCapture.FLASH_MODE_AUTO
                    Toast.makeText(this, "Flash Otomatis (Auto)", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // --- Logika Tombol HDR Fungsional ---
        btnHdrToggle.setOnClickListener {
            isHdrEnabled = !isHdrEnabled
            if (isHdrEnabled) {
                btnHdrToggle.text = "HDR: ON"
                Toast.makeText(this, "HDR Computational Diaktifkan", Toast.LENGTH_SHORT).show()
            } else {
                btnHdrToggle.text = "HDR: OFF"
                Toast.makeText(this, "HDR Dimatikan", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Logika Tombol Macro Fungsional ---
        btnMacroToggle.setOnClickListener {
            isMacroEnabled = !isMacroEnabled
            if (isMacroEnabled) {
                btnMacroToggle.text = "MACRO: ON"
                Toast.makeText(this, "Mode Makro Aktif (Fokus Jarak Dekat)", Toast.LENGTH_SHORT).show()
            } else {
                btnMacroToggle.text = "MACRO"
                Toast.makeText(this, "Mode Makro Nonaktif", Toast.LENGTH_SHORT).show()
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

        modeCamera.setOnClickListener { switchMode("CAMERA") }
        modePro.setOnClickListener { switchMode("PRO") }
        modePortrait.setOnClickListener { switchMode("PORTRAIT") }
        modeVideo.setOnClickListener { switchMode("VIDEO") }

        btnCapturePro.setOnClickListener {
            when (currentMode) {
                "VIDEO" -> Toast.makeText(this, "Mode Video: Perekaman belum dimulai", Toast.LENGTH_SHORT).show()
                else -> takeEnhancedPhoto()
            }
        }
    }

    private fun switchMode(newMode: String) {
        currentMode = newMode
        val inactiveColor = android.graphics.Color.parseColor("#99FFFFFF")
        val activeColor = android.graphics.Color.WHITE

        modeCamera.setTextColor(inactiveColor)
        modePro.setTextColor(inactiveColor)
        modePortrait.setTextColor(inactiveColor)
        modeVideo.setTextColor(inactiveColor)

        when (newMode) {
            "CAMERA" -> {
                modeCamera.setTextColor(activeColor)
                btnCapturePro.text = "SNAP"
            }
            "PRO" -> {
                modePro.setTextColor(activeColor)
                btnCapturePro.text = "PRO"
            }
            "PORTRAIT" -> {
                modePortrait.setTextColor(activeColor)
                btnCapturePro.text = "PORT"
            }
            "VIDEO" -> {
                modeVideo.setTextColor(activeColor)
                btnCapturePro.text = "REC"
            }
        }
        Toast.makeText(this, "Mode: $currentMode", Toast.LENGTH_SHORT).show()
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
                    .setFlashMode(
                        when (flashState) {
                            1 -> ImageCapture.FLASH_MODE_ON
                            2 -> ImageCapture.FLASH_MODE_AUTO
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                    )
                    .build()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

                // Jika mode Macro aktif, terapkan kontrol zoom minimal/fokus jarak dekat jika didukung
                if (isMacroEnabled) {
                    camera.cameraControl.setZoomRatio(1.5f) // Simulasi makro zoom optik/digital ringan
                } else {
                    camera.cameraControl.setZoomRatio(1.0f)
                }

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

                        // Terapkan peningkatan komputasi HDR / Pro / Portrait jika diaktifkan
                        val finalBitmap = if (isHdrEnabled && (currentMode == "PRO" || currentMode == "PORTRAIT")) {
                            applyComputationalEnhancement(bitmap, currentMode)
                        } else {
                            bitmap
                        }

                        saveBitmapToGallery(finalBitmap)
                    } catch (e: Exception) {
                        Toast.makeText(baseContext, "Kesalahan pemrosesan: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun applyComputationalEnhancement(src: Bitmap, mode: String): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)

        val canvas = Canvas(dest)
        val paint = Paint()

        val colorMatrix = ColorMatrix().apply {
            if (mode == "PORTRAIT") {
                setSaturation(1.25f)
            } else {
                setSaturation(1.15f) // Efek HDR-like kontras kaya
            }
        }

        val scaleMatrix = ColorMatrix(
            floatArrayOf(
                1.1f, 0f, 0f, 0f, 15f,
                0f, 1.1f, 0f, 0f, 15f,
                0f, 0f, 1.1f, 0f, 15f,
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
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Ucam_${currentMode}_$name.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UcamApp")
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            val outputStream: OutputStream? = contentResolver.openOutputStream(it)
            outputStream?.let { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                stream.close()
                Toast.makeText(this, "Foto berhasil disimpan!", Toast.LENGTH_SHORT).show()
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
