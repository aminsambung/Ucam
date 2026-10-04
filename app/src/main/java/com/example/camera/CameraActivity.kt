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
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
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
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnInfo: ImageButton

    // Panel Atas
    private lateinit var topPhotoControls: LinearLayout
    private lateinit var topVideoControls: LinearLayout
    private lateinit var topProControls: LinearLayout
    private lateinit var proParametersBar: LinearLayout

    // Tombol Kontrol Foto
    private lateinit var btnFlashToggle: Button
    private lateinit var btnHdrToggle: Button
    private lateinit var btnMacroToggle: Button
    private lateinit var btnRatioToggle: Button
    private lateinit var btnRawToggle: Button
    private lateinit var btnOpenSettings: ImageButton

    // Tombol Kontrol Video
    private lateinit var btnVideoFlash: Button
    private lateinit var btnVideoHdr: Button
    private lateinit var btnVideoStabilization: Button
    private lateinit var btnVideoResolution: Button
    private lateinit var btnVideoSettings: ImageButton

    // Tombol Kontrol Pro
    private lateinit var btnProFlash: ImageButton
    private lateinit var tvRawIndicator: TextView
    private lateinit var btnProSettings: ImageButton

    // 5 Tombol Parameter Pro
    private lateinit var btnEvParam: Button
    private lateinit var btnIsoParam: Button
    private lateinit var btnShutterParam: Button
    private lateinit var btnWbParam: Button
    private lateinit var btnMfParam: Button

    // Indikator Mode TextView
    private lateinit var modePortrait: TextView
    private lateinit var modeCamera: TextView
    private lateinit var modeVideo: TextView
    private lateinit var modePro: TextView

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var currentMode = "PRO"

    // States
    private var flashState = 0 // 0: OFF, 1: ON, 2: AUTO
    private var isHdrEnabled = true
    private var isMacroEnabled = false
    private var isStabilizationEnabled = true
    private var videoResIndex = 0
    private val videoResolutions = arrayOf("720p 30", "1080p 30", "1080p 60", "4K 30")
    private var currentRatioIndex = 0
    private val ratios = arrayOf("4:3", "16:9")
    private var isRawActive = true

    // Parameter Pro States
    private var evIndex = 1
    private val evValues = arrayOf("EV\n-1", "EV\n0", "EV\n+1")
    private var isoIndex = 0
    private val isoValues = arrayOf("ISO\nAuto", "ISO\n100", "ISO\n400", "ISO\n1600")
    private var shutterIndex = 0
    private val shutterValues = arrayOf("S\nAuto", "S\n1/125", "S\n1/500", "S\n1/2000")
    private var wbIndex = 0
    private val wbValues = arrayOf("WB\nAuto", "WB\nDaylight", "WB\nCloudy")
    private var mfIndex = 0
    private val mfValues = arrayOf("MF\nAuto", "MF\nMacro", "MF\nInfinity")

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        viewFinder = findViewById(R.id.viewFinder)
        btnCapturePro = findViewById(R.id.btnCapturePro)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnInfo = findViewById(R.id.btnInfo)

        topPhotoControls = findViewById(R.id.topPhotoControls)
        topVideoControls = findViewById(R.id.topVideoControls)
        topProControls = findViewById(R.id.topProControls)
        proParametersBar = findViewById(R.id.proParametersBar)

        btnFlashToggle = findViewById(R.id.btnFlashToggle)
        btnHdrToggle = findViewById(R.id.btnHdrToggle)
        btnMacroToggle = findViewById(R.id.btnMacroToggle)
        btnRatioToggle = findViewById(R.id.btnRatioToggle)
        btnRawToggle = findViewById(R.id.btnRawToggle)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)

        btnVideoFlash = findViewById(R.id.btnVideoFlash)
        btnVideoHdr = findViewById(R.id.btnVideoHdr)
        btnVideoStabilization = findViewById(R.id.btnVideoStabilization)
        btnVideoResolution = findViewById(R.id.btnVideoResolution)
        btnVideoSettings = findViewById(R.id.btnVideoSettings)

        btnProFlash = findViewById(R.id.btnProFlash)
        tvRawIndicator = findViewById(R.id.tvRawIndicator)
        btnProSettings = findViewById(R.id.btnProSettings)

        btnEvParam = findViewById(R.id.btnEvParam)
        btnIsoParam = findViewById(R.id.btnIsoParam)
        btnShutterParam = findViewById(R.id.btnShutterParam)
        btnWbParam = findViewById(R.id.btnWbParam)
        btnMfParam = findViewById(R.id.btnMfParam)

        modePortrait = findViewById(R.id.modePortrait)
        modeCamera = findViewById(R.id.modeCamera)
        modeVideo = findViewById(R.id.modeVideo)
        modePro = findViewById(R.id.modePro)

        if (allPermissionsGranted()) {
            startProCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        val settingsAction = View.OnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        btnOpenSettings.setOnClickListener(settingsAction)
        btnVideoSettings.setOnClickListener(settingsAction)
        btnProSettings.setOnClickListener(settingsAction)

        btnSwitchCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            startProCamera()
        }

        btnInfo.setOnClickListener {
            Toast.makeText(this, "Ucam Pro Mode Aktif", Toast.LENGTH_SHORT).show()
        }

        // Interaksi Flash
        val flashClickListener = View.OnClickListener {
            flashState = (flashState + 1) % 3
            val flashText = when(flashState) { 0 -> "⚡ OFF"; 1 -> "⚡ ON"; else -> "⚡ AUTO" }
            btnFlashToggle.text = flashText
            btnVideoFlash.text = flashText
            Toast.makeText(this, "Flash: $flashText", Toast.LENGTH_SHORT).show()
        }
        btnFlashToggle.setOnClickListener(flashClickListener)
        btnVideoFlash.setOnClickListener(flashClickListener)
        btnProFlash.setOnClickListener(flashClickListener)

        // Interaksi Tombol Lainnya
        btnHdrToggle.setOnClickListener {
            isHdrEnabled = !isHdrEnabled
            btnHdrToggle.text = if (isHdrEnabled) "HDR: ON" else "HDR: OFF"
        }
        btnVideoHdr.setOnClickListener {
            isHdrEnabled = !isHdrEnabled
            btnVideoHdr.text = if (isHdrEnabled) "HDR: ON" else "HDR: OFF"
        }
        btnMacroToggle.setOnClickListener {
            isMacroEnabled = !isMacroEnabled
            btnMacroToggle.text = if (isMacroEnabled) "MACRO: ON" else "MACRO"
            startProCamera()
        }
        btnRatioToggle.setOnClickListener {
            currentRatioIndex = (currentRatioIndex + 1) % ratios.size
            btnRatioToggle.text = ratios[currentRatioIndex]
            startProCamera()
        }
        btnRawToggle.setOnClickListener {
            isRawActive = !isRawActive
            btnRawToggle.text = if (isRawActive) "RAW: ON" else "RAW"
        }
        tvRawIndicator.setOnClickListener {
            isRawActive = !isRawActive
            tvRawIndicator.text = if (isRawActive) "RAW" else "JPEG"
        }
        btnVideoStabilization.setOnClickListener {
            isStabilizationEnabled = !isStabilizationEnabled
            btnVideoStabilization.text = if (isStabilizationEnabled) "STAB: ON" else "STAB: OFF"
        }
        btnVideoResolution.setOnClickListener {
            videoResIndex = (videoResIndex + 1) % videoResolutions.size
            btnVideoResolution.text = videoResolutions[videoResIndex]
        }

        // 5 Tombol Parameter Pro Fungsional
        btnEvParam.setOnClickListener {
            evIndex = (evIndex + 1) % evValues.size
            btnEvParam.text = evValues[evIndex]
        }
        btnIsoParam.setOnClickListener {
            isoIndex = (isoIndex + 1) % isoValues.size
            btnIsoParam.text = isoValues[isoIndex]
        }
        btnShutterParam.setOnClickListener {
            shutterIndex = (shutterIndex + 1) % shutterValues.size
            btnShutterParam.text = shutterValues[shutterIndex]
        }
        btnWbParam.setOnClickListener {
            wbIndex = (wbIndex + 1) % wbValues.size
            btnWbParam.text = wbValues[wbIndex]
        }
        btnMfParam.setOnClickListener {
            mfIndex = (mfIndex + 1) % mfValues.size
            btnMfParam.text = mfValues[mfIndex]
            if (mfIndex == 1) camera?.cameraControl?.setZoomRatio(1.5f)
            else camera?.cameraControl?.setZoomRatio(1.0f)
        }

        // Navigasi Mode
        modePortrait.setOnClickListener { switchMode("PORTRAIT") }
        modeCamera.setOnClickListener { switchMode("CAMERA") }
        modeVideo.setOnClickListener { switchMode("VIDEO") }
        modePro.setOnClickListener { switchMode("PRO") }

        btnCapturePro.setOnClickListener {
            if (currentMode == "VIDEO") {
                Toast.makeText(this, "Simulasi Perekaman Video Dimulai...", Toast.LENGTH_SHORT).show()
            } else {
                takePhoto()
            }
        }
        
        // Inisialisasi awal ke mode Pro
        switchMode("PRO")
    }

    private fun switchMode(newMode: String) {
        currentMode = newMode
        val inactiveColor = android.graphics.Color.parseColor("#99FFFFFF")
        val activeColor = android.graphics.Color.WHITE

        modePortrait.setTextColor(inactiveColor)
        modeCamera.setTextColor(inactiveColor)
        modeVideo.setTextColor(inactiveColor)
        modePro.setTextColor(inactiveColor)

        // Sembunyikan semua panel atas & bawah pro dulu
        topPhotoControls.visibility = View.GONE
        topVideoControls.visibility = View.GONE
        topProControls.visibility = View.GONE
        proParametersBar.visibility = View.GONE

        when (newMode) {
            "PORTRAIT" -> {
                modePortrait.setTextColor(activeColor)
                topPhotoControls.visibility = View.VISIBLE
                btnCapturePro.text = "PORT"
            }
            "CAMERA" -> {
                modeCamera.setTextColor(activeColor)
                topPhotoControls.visibility = View.VISIBLE
                btnCapturePro.text = "SNAP"
            }
            "VIDEO" -> {
                modeVideo.setTextColor(activeColor)
                topVideoControls.visibility = View.VISIBLE
                btnCapturePro.text = "REC"
            }
            "PRO" -> {
                modePro.setTextColor(activeColor)
                topProControls.visibility = View.VISIBLE
                proParametersBar.visibility = View.VISIBLE
                btnCapturePro.text = "PRO"
            }
        }
        Toast.makeText(this, "Mode: $newMode", Toast.LENGTH_SHORT).show()
    }

    private fun startProCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

                val aspectRatio = if (ratios[currentRatioIndex] == "16:9") AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetAspectRatio(aspectRatio)
                    .build()

                val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

                if (isMacroEnabled) camera?.cameraControl?.setZoomRatio(1.5f)
                else camera?.cameraControl?.setZoomRatio(1.0f)

            } catch (exc: Exception) {
                Toast.makeText(this, "Gagal memuat kamera: ${exc.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(baseContext, "Gagal: ${exc.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = image.toBitmap()
                        image.close()

                        val finalBitmap = if (isHdrEnabled && (currentMode == "PRO" || currentMode == "PORTRAIT")) {
                            applyEnhancement(bitmap)
                        } else {
                            bitmap
                        }
                        saveBitmapToGallery(finalBitmap)
                    } catch (e: Exception) {
                        Toast.makeText(baseContext, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun applyEnhancement(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()

        val colorMatrix = ColorMatrix().apply { setSaturation(1.2f) }
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
            val stream: OutputStream? = contentResolver.openOutputStream(it)
            stream?.let { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.close()
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
            if (allPermissionsGranted()) startProCamera()
            else { Toast.makeText(this, "Izin diperlukan.", Toast.LENGTH_LONG).show(); finish() }
        }
    }
}
