package com.example.camera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    // ================== VIEWS ==================
    private lateinit var viewFinder: PreviewView
    private lateinit var focusRing: View
    private lateinit var topBar: View
    private lateinit var bottomSection: View

    private lateinit var btnFlash: ImageButton
    private lateinit var btnHdr: TextView
    private lateinit var btnTimer: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var tvTimerText: TextView

    private lateinit var zoom06: TextView
    private lateinit var zoom1x: TextView
    private lateinit var zoom2x: TextView

    private lateinit var modeNight: TextView
    private lateinit var modePortrait: TextView
    private lateinit var modePhoto: TextView
    private lateinit var modeVideo: TextView
    private lateinit var modeVlog: TextView
    private lateinit var modePro: TextView

    private lateinit var btnGalleryPreview: ImageButton
    private lateinit var btnFilter: ImageButton
    private lateinit var btnCapture: View          // FrameLayout
    private lateinit var shutterInner: View
    private lateinit var btnSwitchCamera: ImageButton

    // ================== STATE ==================
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var currentMode = "PHOTO"

    private var flashState = 0            // 0=OFF, 1=ON, 2=AUTO
    private var isHdrEnabled = true
    private var isRawActive = false

    private var zoomIndex = 1             // 0=0.6x, 1=1x, 2=2x
    private val zoomValues = floatArrayOf(0.6f, 1.0f, 2.0f)

    private var timerState = 0            // 0=off, 1=3s, 2=10s
    private val timerSeconds = intArrayOf(0, 3, 10)

    // Parameter Pro
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

    // Camera executor
    private lateinit var cameraExecutor: ExecutorService

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        private const val TAG = "CameraActivity"
    }

    // ================== LIFECYCLE ==================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        cameraExecutor = Executors.newSingleThreadExecutor()

        bindViews()
        setupListeners()

        if (allPermissionsGranted()) {
            viewFinder.postDelayed({ startCamera() }, 300)
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        switchMode("PHOTO")
        updateFlashUI()
        updateTimerUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // ================== BIND VIEWS ==================
    private fun bindViews() {
        viewFinder       = findViewById(R.id.viewFinder)
        focusRing        = findViewById(R.id.focusRing)
        topBar           = findViewById(R.id.topBar)
        bottomSection    = findViewById(R.id.bottomSection)

        btnFlash         = findViewById(R.id.btnFlash)
        btnHdr           = findViewById(R.id.btnHdr)
        btnTimer         = findViewById(R.id.btnTimer)
        btnSettings      = findViewById(R.id.btnSettings)
        tvTimerText      = findViewById(R.id.tvTimerText)

        zoom06           = findViewById(R.id.zoom06)
        zoom1x           = findViewById(R.id.zoom1x)
        zoom2x           = findViewById(R.id.zoom2x)

        modeNight        = findViewById(R.id.modeNight)
        modePortrait     = findViewById(R.id.modePortrait)
        modePhoto        = findViewById(R.id.modePhoto)
        modeVideo        = findViewById(R.id.modeVideo)
        modeVlog         = findViewById(R.id.modeVlog)
        modePro          = findViewById(R.id.modePro)

        btnGalleryPreview = findViewById(R.id.btnGalleryPreview)
        btnFilter         = findViewById(R.id.btnFilter)
        btnCapture        = findViewById(R.id.btnCapture)
        shutterInner      = findViewById(R.id.shutterInner)
        btnSwitchCamera   = findViewById(R.id.btnSwitchCamera)
    }

    // ================== LISTENERS ==================
    private fun setupListeners() {
        // Flash: cycle OFF → ON → AUTO
        btnFlash.setOnClickListener {
            flashState = (flashState + 1) % 3
            updateFlashUI()
            applyFlashToCapture()
        }

        // HDR toggle
        btnHdr.setOnClickListener {
            isHdrEnabled = !isHdrEnabled
            btnHdr.alpha = if (isHdrEnabled) 1.0f else 0.4f
            Toast.makeText(
                this,
                if (isHdrEnabled) "HDR: ON" else "HDR: OFF",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Timer: cycle 0s → 3s → 10s
        btnTimer.setOnClickListener {
            timerState = (timerState + 1) % 3
            updateTimerUI()
        }

        // Settings
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Zoom buttons
        zoom06.setOnClickListener { setZoomByIndex(0) }
        zoom1x.setOnClickListener  { setZoomByIndex(1) }
        zoom2x.setOnClickListener  { setZoomByIndex(2) }

        // Mode buttons
        modeNight.setOnClickListener    { switchMode("NIGHT") }
        modePortrait.setOnClickListener { switchMode("PORTRAIT") }
        modePhoto.setOnClickListener    { switchMode("PHOTO") }
        modeVideo.setOnClickListener    { switchMode("VIDEO") }
        modeVlog.setOnClickListener     { switchMode("VLOG") }
        modePro.setOnClickListener      { switchMode("PRO") }

        // Gallery
        btnGalleryPreview.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Tidak ada galeri", Toast.LENGTH_SHORT).show()
            }
        }

        // Filter (placeholder)
        btnFilter.setOnClickListener {
            Toast.makeText(this, "Filter belum tersedia", Toast.LENGTH_SHORT).show()
        }

        // Capture / Shutter
        btnCapture.setOnClickListener {
            if (currentMode == "VIDEO" || currentMode == "VLOG") {
                Toast.makeText(this, "Rekam video (belum diimplementasi)", Toast.LENGTH_SHORT).show()
            } else {
                takePhotoWithTimer()
            }
        }

        // Switch camera
        btnSwitchCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT
            else
                CameraSelector.LENS_FACING_BACK
            startCamera()
        }

        // Tap to focus
        viewFinder.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                showFocusRing(event.x, event.y)
            }
            false
        }
    }

    // ================== UI UPDATES ==================
    private fun updateFlashUI() {
        when (flashState) {
            0 -> { // OFF
                btnFlash.setImageResource(R.drawable.ic_flash_off)
                btnFlash.alpha = 1.0f
            }
            1 -> { // ON
                btnFlash.setImageResource(R.drawable.ic_flash_on)
                btnFlash.alpha = 1.0f
            }
            2 -> { // AUTO
                btnFlash.setImageResource(R.drawable.ic_flash_auto)
                btnFlash.alpha = 1.0f
            }
        }
    }

    private fun updateTimerUI() {
        when (timerState) {
            0 -> {
                btnTimer.setImageResource(R.drawable.ic_timer_off)
                tvTimerText.text = "( 00 , 00 )"
                tvTimerText.alpha = 0.5f
            }
            1 -> {
                btnTimer.setImageResource(R.drawable.ic_timer_on)
                tvTimerText.text = "( 03 , 00 )"
                tvTimerText.alpha = 1.0f
            }
            2 -> {
                btnTimer.setImageResource(R.drawable.ic_timer_on)
                tvTimerText.text = "( 10 , 00 )"
                tvTimerText.alpha = 1.0f
            }
        }
    }

    private fun updateZoomUI() {
        val inactive = Color.parseColor("#99FFFFFF")
        val active = Color.parseColor("#FFC107")
        val normal = Typeface.DEFAULT
        val bold = Typeface.DEFAULT_BOLD

        listOf(zoom06, zoom1x, zoom2x).forEach {
            it.setTextColor(inactive)
            it.setTypeface(normal)
            it.textSize = 12f
        }

        val activeView = when (zoomIndex) {
            0 -> zoom06
            1 -> zoom1x
            else -> zoom2x
        }
        activeView.setTextColor(active)
        activeView.setTypeface(bold)
        activeView.textSize = 13f
    }

    private fun showFocusRing(x: Float, y: Float) {
        focusRing.x = x - focusRing.width / 2f
        focusRing.y = y - focusRing.height / 2f
        focusRing.visibility = View.VISIBLE
        focusRing.alpha = 1f
        focusRing.animate()
            .alpha(0f)
            .setDuration(1000)
            .withEndAction { focusRing.visibility = View.INVISIBLE }
            .start()
    }

    // ================== MODE SWITCH ==================
    private fun switchMode(newMode: String) {
        currentMode = newMode
        val inactive = Color.parseColor("#99FFFFFF")
        val active = Color.parseColor("#FFC107")
        val normal = Typeface.DEFAULT
        val bold = Typeface.DEFAULT_BOLD

        val allModes = listOf(modeNight, modePortrait, modePhoto, modeVideo, modeVlog, modePro)
        allModes.forEach {
            it.setTextColor(inactive)
            it.setTypeface(normal)
            it.textSize = 13f
        }

        val activeView = when (newMode) {
            "NIGHT" -> modeNight
            "PORTRAIT" -> modePortrait
            "PHOTO" -> modePhoto
            "VIDEO" -> modeVideo
            "VLOG" -> modeVlog
            "PRO" -> modePro
            else -> modePhoto
        }
        activeView.setTextColor(active)
        activeView.setTypeface(bold)
        activeView.textSize = 15f
    }

    // ================== ZOOM ==================
    private fun setZoomByIndex(index: Int) {
        zoomIndex = index
        updateZoomUI()
        setSafeZoom(zoomValues[index])
    }

    private fun setSafeZoom(ratio: Float) {
        val cam = camera ?: return
        val state = cam.cameraInfo.zoomState.value ?: return
        val clamped = ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        cam.cameraControl.setZoomRatio(clamped)
    }

    // ================== CAMERA START ==================
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build()

                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, selector, preview, imageCapture)

                // Terapkan zoom sesuai state
                setSafeZoom(zoomValues[zoomIndex])

                // Terapkan flash
                applyFlashToCapture()

                Log.d(TAG, "Camera bind SUCCESS")

            } catch (e: Exception) {
                Log.e(TAG, "Camera bind FAILED", e)
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun applyFlashToCapture() {
        val ic = imageCapture ?: return
        ic.flashMode = when (flashState) {
            0 -> ImageCapture.FLASH_MODE_OFF
            1 -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_AUTO
        }
    }

    // ================== TIMER + CAPTURE ==================
    private fun takePhotoWithTimer() {
        val seconds = timerSeconds[timerState]
        if (seconds == 0) {
            takePhoto()
        } else {
            Toast.makeText(this, "Timer: $seconds detik", Toast.LENGTH_SHORT).show()
            object : CountDownTimer(seconds * 1000L, 1000L) {
                override fun onTick(millisUntilFinished: Long) {
                    tvTimerText.text = "( 0${millisUntilFinished / 1000 + 1} , 00 )"
                }
                override fun onFinish() {
                    updateTimerUI()
                    takePhoto()
                }
            }.start()
        }
    }

    private fun takePhoto() {
        val ic = imageCapture ?: run {
            Toast.makeText(this, "Kamera belum siap", Toast.LENGTH_SHORT).show()
            return
        }

        // Efek shutter visual
        shutterInner.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80)
            .withEndAction {
                shutterInner.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }.start()

        ic.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(baseContext, "Gagal: ${exc.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = image.toBitmap()
                        image.close()

                        val finalBitmap = if (isHdrEnabled &&
                            (currentMode == "PRO" || currentMode == "PORTRAIT" || currentMode == "PHOTO")
                        ) {
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
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())
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
            stream?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            Toast.makeText(this, "Foto disimpan!", Toast.LENGTH_SHORT).show()
        }
    }

    // ================== PERMISSIONS ==================
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewFinder.postDelayed({ startCamera() }, 300)
            } else {
                Toast.makeText(this, "Izin diperlukan.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
