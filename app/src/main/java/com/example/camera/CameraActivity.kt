package com.example.camera

import android.Manifest
import android.app.Dialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
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
import java.util.concurrent.TimeUnit

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
    private lateinit var btnCapture: View
    private lateinit var shutterInner: View
    private lateinit var btnSwitchCamera: ImageButton

    // ================== STATE ==================
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var currentMode = "PHOTO"

    private var flashState = 0
    private var isHdrEnabled = true

    private var zoomIndex = 1
    private val zoomValues = floatArrayOf(0.6f, 1.0f, 2.0f)

    private var timerState = 0
    private val timerSeconds = intArrayOf(0, 3, 5, 10)

    // Panel settings state
    private var currentRatio = "FULL"     // 1:1, 4:3, 16:9, FULL
    private var currentTimer = 0          // 0=off, 1=3s, 2=5s, 3=10s
    private var currentGrid = 0           // 0=off, 1=3x3, 2=4x4, 3=golden
    private var isLevelOn = false
    private var isStabilizerOn = true
    private var isMasterEffectOn = false
    private var isWatermarkOn = false
    private var isFocusPeakingOn = false

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
        updateZoomUI()

        cameraExecutor.execute { loadLastPhotoThumbnail() }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onResume() {
        super.onResume()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.execute { loadLastPhotoThumbnail() }
        }
    }

    // ================== BIND VIEWS ==================
    private fun bindViews() {
        viewFinder        = findViewById(R.id.viewFinder)
        focusRing         = findViewById(R.id.focusRing)
        topBar            = findViewById(R.id.topBar)
        bottomSection     = findViewById(R.id.bottomSection)

        btnFlash          = findViewById(R.id.btnFlash)
        btnHdr            = findViewById(R.id.btnHdr)
        btnTimer          = findViewById(R.id.btnTimer)
        btnSettings       = findViewById(R.id.btnSettings)
        tvTimerText       = findViewById(R.id.tvTimerText)

        zoom06            = findViewById(R.id.zoom06)
        zoom1x            = findViewById(R.id.zoom1x)
        zoom2x            = findViewById(R.id.zoom2x)

        modeNight         = findViewById(R.id.modeNight)
        modePortrait      = findViewById(R.id.modePortrait)
        modePhoto         = findViewById(R.id.modePhoto)
        modeVideo         = findViewById(R.id.modeVideo)
        modeVlog          = findViewById(R.id.modeVlog)
        modePro           = findViewById(R.id.modePro)

        btnGalleryPreview = findViewById(R.id.btnGalleryPreview)
        btnFilter         = findViewById(R.id.btnFilter)
        btnCapture        = findViewById(R.id.btnCapture)
        shutterInner      = findViewById(R.id.shutterInner)
        btnSwitchCamera   = findViewById(R.id.btnSwitchCamera)
    }

    // ================== LISTENERS ==================
    private fun setupListeners() {
        btnFlash.setOnClickListener {
            flashState = (flashState + 1) % 3
            updateFlashUI()
            applyFlashToCapture()
        }

        btnHdr.setOnClickListener {
            isHdrEnabled = !isHdrEnabled
            btnHdr.alpha = if (isHdrEnabled) 1.0f else 0.4f
            Toast.makeText(this, if (isHdrEnabled) "HDR: ON" else "HDR: OFF", Toast.LENGTH_SHORT).show()
        }

        btnTimer.setOnClickListener {
            timerState = (timerState + 1) % timerSeconds.size
            currentTimer = timerState
            updateTimerUI()
        }

        // ⚙️ Buka panel overlay
        btnSettings.setOnClickListener {
            showSettingsOverlay()
        }

        zoom06.setOnClickListener { setZoomByIndex(0) }
        zoom1x.setOnClickListener  { setZoomByIndex(1) }
        zoom2x.setOnClickListener  { setZoomByIndex(2) }

        modeNight.setOnClickListener    { switchMode("NIGHT") }
        modePortrait.setOnClickListener { switchMode("PORTRAIT") }
        modePhoto.setOnClickListener    { switchMode("PHOTO") }
        modeVideo.setOnClickListener    { switchMode("VIDEO") }
        modeVlog.setOnClickListener     { switchMode("VLOG") }
        modePro.setOnClickListener      { switchMode("PRO") }

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

        btnFilter.setOnClickListener {
            Toast.makeText(this, "Filter belum tersedia", Toast.LENGTH_SHORT).show()
        }

        btnCapture.setOnClickListener {
            if (currentMode == "VIDEO" || currentMode == "VLOG") {
                Toast.makeText(this, "Rekam video (belum diimplementasi)", Toast.LENGTH_SHORT).show()
            } else {
                takePhotoWithTimer()
            }
        }

        btnSwitchCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT
            else
                CameraSelector.LENS_FACING_BACK
            startCamera()
        }

        // ============ TAP TO FOCUS (AF + AE) ============
        viewFinder.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val factory = viewFinder.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)

                val action = FocusMeteringAction.Builder(
                    point,
                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                ).setAutoCancelDuration(5, TimeUnit.SECONDS).build()

                camera?.cameraControl?.startFocusAndMetering(action)
                    ?.addListener({
                        Log.d(TAG, "Focus & metering selesai")
                    }, ContextCompat.getMainExecutor(this))

                showFocusRing(event.x, event.y)
                return@setOnTouchListener true
            }
            false
        }
    }

    // ================== SETTINGS OVERLAY ==================
    private fun showSettingsOverlay() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.activity_settings_overlay)

        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        val btnClose      = dialog.findViewById<ImageButton>(R.id.btnCloseSettings)
        val ratio11       = dialog.findViewById<TextView>(R.id.ratio11)
        val ratio43       = dialog.findViewById<TextView>(R.id.ratio43)
        val ratio169      = dialog.findViewById<TextView>(R.id.ratio169)
        val ratioFull     = dialog.findViewById<TextView>(R.id.ratioFull)
        val timerOff      = dialog.findViewById<TextView>(R.id.timerOff)
        val timer3        = dialog.findViewById<TextView>(R.id.timer3)
        val timer5        = dialog.findViewById<TextView>(R.id.timer5)
        val timer10       = dialog.findViewById<TextView>(R.id.timer10)
        val btnGrid       = dialog.findViewById<TextView>(R.id.btnGrid)
        val btnLevel      = dialog.findViewById<TextView>(R.id.btnLevel)
        val btnStabilizer = dialog.findViewById<TextView>(R.id.btnStabilizer)
        val btnMaster     = dialog.findViewById<TextView>(R.id.btnMasterEffect)
        val btnWatermark  = dialog.findViewById<TextView>(R.id.btnWatermark)
        val btnFocus      = dialog.findViewById<TextView>(R.id.btnFocusPeaking)

        fun styleGroup(view: TextView, active: Boolean, group: List<TextView>) {
            group.forEach {
                it.setBackgroundColor(Color.WHITE)
                it.setTextColor(Color.BLACK)
            }
            if (active) {
                view.setBackgroundColor(Color.parseColor("#FFC107"))
                view.setTextColor(Color.BLACK)
            }
        }

        val ratioGroup = listOf(ratio11, ratio43, ratio169, ratioFull)
        val timerGroup = listOf(timerOff, timer3, timer5, timer10)

        // Set state awal
        when (currentRatio) {
            "1:1" -> styleGroup(ratio11, true, ratioGroup)
            "4:3" -> styleGroup(ratio43, true, ratioGroup)
            "16:9" -> styleGroup(ratio169, true, ratioGroup)
            else -> styleGroup(ratioFull, true, ratioGroup)
        }
        when (currentTimer) {
            0 -> styleGroup(timerOff, true, timerGroup)
            1 -> styleGroup(timer3, true, timerGroup)
            2 -> styleGroup(timer5, true, timerGroup)
            3 -> styleGroup(timer10, true, timerGroup)
        }
        btnGrid.background.setTint(if (currentGrid > 0) Color.parseColor("#FFC107") else Color.WHITE)
        btnLevel.background.setTint(if (isLevelOn) Color.parseColor("#FFC107") else Color.WHITE)
        btnStabilizer.background.setTint(if (isStabilizerOn) Color.parseColor("#FFC107") else Color.WHITE)
        btnMaster.background.setTint(if (isMasterEffectOn) Color.parseColor("#FFC107") else Color.WHITE)
        btnWatermark.background.setTint(if (isWatermarkOn) Color.parseColor("#FFC107") else Color.WHITE)
        btnFocus.background.setTint(if (isFocusPeakingOn) Color.parseColor("#FFC107") else Color.WHITE)

        // Listeners
        btnClose.setOnClickListener { dialog.dismiss() }

        ratio11.setOnClickListener   { currentRatio = "1:1";  styleGroup(ratio11, true, ratioGroup);  applyAspectRatio() }
        ratio43.setOnClickListener   { currentRatio = "4:3";  styleGroup(ratio43, true, ratioGroup);  applyAspectRatio() }
        ratio169.setOnClickListener  { currentRatio = "16:9"; styleGroup(ratio169, true, ratioGroup); applyAspectRatio() }
        ratioFull.setOnClickListener { currentRatio = "FULL"; styleGroup(ratioFull, true, ratioGroup); applyAspectRatio() }

        timerOff.setOnClickListener { currentTimer = 0; timerState = 0; styleGroup(timerOff, true, timerGroup); updateTimerUI() }
        timer3.setOnClickListener   { currentTimer = 1; timerState = 1; styleGroup(timer3, true, timerGroup);   updateTimerUI() }
        timer5.setOnClickListener   { currentTimer = 2; timerState = 2; styleGroup(timer5, true, timerGroup);   updateTimerUI() }
        timer10.setOnClickListener  { currentTimer = 3; timerState = 3; styleGroup(timer10, true, timerGroup);  updateTimerUI() }

        btnGrid.setOnClickListener {
            currentGrid = (currentGrid + 1) % 4
            btnGrid.background.setTint(if (currentGrid > 0) Color.parseColor("#FFC107") else Color.WHITE)
            val names = listOf("Off", "3x3", "4x4", "Golden")
            Toast.makeText(this, "Grid: ${names[currentGrid]}", Toast.LENGTH_SHORT).show()
        }

        btnLevel.setOnClickListener {
            isLevelOn = !isLevelOn
            btnLevel.background.setTint(if (isLevelOn) Color.parseColor("#FFC107") else Color.WHITE)
            Toast.makeText(this, if (isLevelOn) "Level ON" else "Level OFF", Toast.LENGTH_SHORT).show()
        }

        btnStabilizer.setOnClickListener {
            isStabilizerOn = !isStabilizerOn
            btnStabilizer.background.setTint(if (isStabilizerOn) Color.parseColor("#FFC107") else Color.WHITE)
            Toast.makeText(this, if (isStabilizerOn) "Stabilisator ON" else "Stabilisator OFF", Toast.LENGTH_SHORT).show()
        }

        btnMaster.setOnClickListener {
            isMasterEffectOn = !isMasterEffectOn
            btnMaster.background.setTint(if (isMasterEffectOn) Color.parseColor("#FFC107") else Color.WHITE)
            Toast.makeText(this, if (isMasterEffectOn) "Master Efek ON" else "Master Efek OFF", Toast.LENGTH_SHORT).show()
        }

        btnWatermark.setOnClickListener {
            isWatermarkOn = !isWatermarkOn
            btnWatermark.background.setTint(if (isWatermarkOn) Color.parseColor("#FFC107") else Color.WHITE)
            Toast.makeText(this, if (isWatermarkOn) "Tanda Air ON" else "Tanda Air OFF", Toast.LENGTH_SHORT).show()
        }

        btnFocus.setOnClickListener {
            isFocusPeakingOn = !isFocusPeakingOn
            btnFocus.background.setTint(if (isFocusPeakingOn) Color.parseColor("#FFC107") else Color.WHITE)
            Toast.makeText(this, if (isFocusPeakingOn) "Focus Peaking ON" else "Focus Peaking OFF", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun applyAspectRatio() {
        // Rebuild camera dengan aspect ratio baru
        startCamera()
    }

    // ================== UI UPDATES ==================
    private fun updateFlashUI() {
        when (flashState) {
            0 -> btnFlash.setImageResource(R.drawable.ic_flash_off)
            1 -> btnFlash.setImageResource(R.drawable.ic_flash_on)
            2 -> btnFlash.setImageResource(R.drawable.ic_flash_auto)
        }
        btnFlash.alpha = 1.0f
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
                tvTimerText.text = "( 05 , 00 )"
                tvTimerText.alpha = 1.0f
            }
            3 -> {
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
        focusRing.scaleX = 1.3f
        focusRing.scaleY = 1.3f
        focusRing.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(0f)
            .setDuration(1200)
            .withEndAction { focusRing.visibility = View.INVISIBLE }
            .start()
    }

    // ================== MODE ==================
    private fun switchMode(newMode: String) {
        currentMode = newMode
        val inactive = Color.parseColor("#99FFFFFF")
        val active = Color.parseColor("#FFC107")
        val normal = Typeface.DEFAULT
        val bold = Typeface.DEFAULT_BOLD

        listOf(modeNight, modePortrait, modePhoto, modeVideo, modeVlog, modePro).forEach {
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

    // ================== CAMERA ==================
    private fun startCamera() {
        Log.d(TAG, "startCamera() dipanggil — ratio=$currentRatio")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

                val aspectRatio = when (currentRatio) {
                    "16:9" -> AspectRatio.RATIO_16_9
                    else -> AspectRatio.RATIO_4_3
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetAspectRatio(aspectRatio)
                    .build()

                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, selector, preview, imageCapture)

                setSafeZoom(zoomValues[zoomIndex])
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

    // ================== CAPTURE ==================
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
                        ) applyEnhancement(bitmap) else bitmap

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

    // ================== SAVE ==================
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
            stream?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) }
            Toast.makeText(this, "Foto disimpan!", Toast.LENGTH_SHORT).show()
            cameraExecutor.execute { loadLastPhotoThumbnail() }
        }
    }

    // ================== THUMBNAIL ==================
    private fun loadLastPhotoThumbnail() {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            )
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%Pictures/UcamApp%")
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            val cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, sortOrder
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )

                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                        android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.isMutableRequired = false
                            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    }

                    runOnUiThread {
                        try {
                            btnGalleryPreview.setImageDrawable(BitmapDrawable(resources, bitmap))
                            btnGalleryPreview.scaleType = ImageView.ScaleType.CENTER_CROP
                            btnGalleryPreview.background = null
                            btnGalleryPreview.clipToOutline = true
                            btnGalleryPreview.outlineProvider = object : ViewOutlineProvider() {
                                override fun getOutline(view: View, outline: Outline) {
                                    outline.setOval(0, 0, view.width, view.height)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Thumbnail error", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadLastPhotoThumbnail error", e)
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
