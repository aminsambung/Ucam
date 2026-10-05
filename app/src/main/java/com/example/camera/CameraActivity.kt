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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.exifinterface.media.ExifInterface
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class CameraActivity : AppCompatActivity() {

    // ================== VIEWS ==================
    private lateinit var viewFinder: PreviewView
    private lateinit var focusRing: View
    private lateinit var topBar: View
    private lateinit var bottomSection: View

    private lateinit var gridOverlay: View
    private lateinit var levelLine: View
    private lateinit var focusPeakingRing: View
    private lateinit var watermarkPreview: TextView

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

    private var currentRatio = "FULL"
    private var currentTimer = 0
    private var currentGrid = 0
    private var isLevelOn = false
    private var isStabilizerOn = true
    private var isMasterEffectOn = false
    private var isWatermarkOn = false
    private var isFocusPeakingOn = false

    private var currentEffectProfile = "NONE"

    private val FULL_SCREEN_RATIO = 9f / 19.9f

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gravity = FloatArray(3)
    private var isLevelSensorRegistered = false

    private lateinit var cameraExecutor: ExecutorService

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10

        private val REQUIRED_PERMISSIONS: Array<String> = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()

        private const val TAG = "CameraActivity"
    }

    // ================== LIFECYCLE ==================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        cameraExecutor = Executors.newSingleThreadExecutor()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

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
        unregisterLevelSensor()
    }

    override fun onResume() {
        super.onResume()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.execute { loadLastPhotoThumbnail() }
        }
        if (isLevelOn) registerLevelSensor()
    }

    override fun onPause() {
        super.onPause()
        unregisterLevelSensor()
    }

    // ================== BIND VIEWS ==================
    private fun bindViews() {
        viewFinder         = findViewById(R.id.viewFinder)
        focusRing          = findViewById(R.id.focusRing)
        topBar             = findViewById(R.id.topBar)
        bottomSection      = findViewById(R.id.bottomSection)

        gridOverlay        = findViewById(R.id.gridOverlay)
        levelLine          = findViewById(R.id.levelLine)
        focusPeakingRing   = findViewById(R.id.focusPeakingRing)
        watermarkPreview   = findViewById(R.id.watermarkPreview)

        btnFlash           = findViewById(R.id.btnFlash)
        btnHdr             = findViewById(R.id.btnHdr)
        btnTimer           = findViewById(R.id.btnTimer)
        btnSettings        = findViewById(R.id.btnSettings)
        tvTimerText        = findViewById(R.id.tvTimerText)

        zoom06             = findViewById(R.id.zoom06)
        zoom1x             = findViewById(R.id.zoom1x)
        zoom2x             = findViewById(R.id.zoom2x)

        modeNight          = findViewById(R.id.modeNight)
        modePortrait       = findViewById(R.id.modePortrait)
        modePhoto          = findViewById(R.id.modePhoto)
        modeVideo          = findViewById(R.id.modeVideo)
        modeVlog           = findViewById(R.id.modeVlog)
        modePro            = findViewById(R.id.modePro)

        btnGalleryPreview  = findViewById(R.id.btnGalleryPreview)
        btnFilter          = findViewById(R.id.btnFilter)
        btnCapture         = findViewById(R.id.btnCapture)
        shutterInner       = findViewById(R.id.shutterInner)
        btnSwitchCamera    = findViewById(R.id.btnSwitchCamera)
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

        btnSettings.setOnClickListener { showSettingsOverlay() }

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
            try { startActivity(intent) } catch (_: Exception) {
                Toast.makeText(this, "Tidak ada galeri", Toast.LENGTH_SHORT).show()
            }
        }

        btnFilter.setOnClickListener {
            val effects = listOf("NONE", "IPHONE", "SONY", "FUJI", "LEICA", "BW", "VINTAGE")
            val idx = effects.indexOf(currentEffectProfile)
            currentEffectProfile = effects[(idx + 1) % effects.size]
            Toast.makeText(this, "Efek: $currentEffectProfile", Toast.LENGTH_SHORT).show()
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

                if (isFocusPeakingOn) {
                    focusPeakingRing.x = event.x - focusPeakingRing.width / 2f
                    focusPeakingRing.y = event.y - focusPeakingRing.height / 2f
                    focusPeakingRing.visibility = View.VISIBLE
                    focusPeakingRing.animate()
                        .alpha(1f).setDuration(150)
                        .withEndAction {
                            focusPeakingRing.animate().alpha(0f).setDuration(400)
                                .withEndAction { focusPeakingRing.visibility = View.GONE }
                                .start()
                        }.start()
                }

                return@setOnTouchListener true
            }
            false
        }
    }

    // ================== SENSOR LEVEL ==================
    private val levelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            gravity = event.values.clone()
            updateLevelLine()
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun registerLevelSensor() {
        if (!isLevelSensorRegistered && accelerometer != null) {
            sensorManager.registerListener(levelListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            isLevelSensorRegistered = true
        }
    }

    private fun unregisterLevelSensor() {
        if (isLevelSensorRegistered) {
            sensorManager.unregisterListener(levelListener)
            isLevelSensorRegistered = false
        }
    }

    private fun updateLevelLine() {
        if (!isLevelOn) return
        val roll = gravity[0]
        levelLine.rotation = -roll * 2f
        val tint = if (abs(roll) < 1.5f) "#00FF00" else "#FFFFFF"
        levelLine.setBackgroundColor(Color.parseColor(tint))
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

        when (currentRatio) {
            "1:1"  -> styleGroup(ratio11, true, ratioGroup)
            "4:3"  -> styleGroup(ratio43, true, ratioGroup)
            "16:9" -> styleGroup(ratio169, true, ratioGroup)
            else   -> styleGroup(ratioFull, true, ratioGroup)
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

        btnClose.setOnClickListener { dialog.dismiss() }

        ratio11.setOnClickListener {
            currentRatio = "1:1"; styleGroup(ratio11, true, ratioGroup); applyAspectRatio()
        }
        ratio43.setOnClickListener {
            currentRatio = "4:3"; styleGroup(ratio43, true, ratioGroup); applyAspectRatio()
        }
        ratio169.setOnClickListener {
            currentRatio = "16:9"; styleGroup(ratio169, true, ratioGroup); applyAspectRatio()
        }
        ratioFull.setOnClickListener {
            currentRatio = "FULL"; styleGroup(ratioFull, true, ratioGroup); applyAspectRatio()
        }

        timerOff.setOnClickListener {
            currentTimer = 0; timerState = 0; styleGroup(timerOff, true, timerGroup); updateTimerUI()
        }
        timer3.setOnClickListener {
            currentTimer = 1; timerState = 1; styleGroup(timer3, true, timerGroup); updateTimerUI()
        }
        timer5.setOnClickListener {
            currentTimer = 2; timerState = 2; styleGroup(timer5, true, timerGroup); updateTimerUI()
        }
        timer10.setOnClickListener {
            currentTimer = 3; timerState = 3; styleGroup(timer10, true, timerGroup); updateTimerUI()
        }

        btnGrid.setOnClickListener {
            currentGrid = (currentGrid + 1) % 4
            btnGrid.background.setTint(if (currentGrid > 0) Color.parseColor("#FFC107") else Color.WHITE)
            applyGrid()
        }

        btnLevel.setOnClickListener {
            isLevelOn = !isLevelOn
            btnLevel.background.setTint(if (isLevelOn) Color.parseColor("#FFC107") else Color.WHITE)
            applyLevel()
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
            applyWatermark()
        }

        btnFocus.setOnClickListener {
            isFocusPeakingOn = !isFocusPeakingOn
            btnFocus.background.setTint(if (isFocusPeakingOn) Color.parseColor("#FFC107") else Color.WHITE)
            Toast.makeText(this, if (isFocusPeakingOn) "Focus Peaking ON" else "Focus Peaking OFF", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    // ================== APPLY VISUAL ==================
    private fun applyAspectRatio() {
        startCamera()
    }

    private fun applyGrid() {
        when (currentGrid) {
            0 -> gridOverlay.visibility = View.GONE
            1 -> {
                gridOverlay.background = ContextCompat.getDrawable(this, R.drawable.grid_overlay)
                gridOverlay.visibility = View.VISIBLE
            }
            2 -> {
                gridOverlay.background = ContextCompat.getDrawable(this, R.drawable.grid_overlay_4x4)
                gridOverlay.visibility = View.VISIBLE
            }
            3 -> {
                gridOverlay.background = ContextCompat.getDrawable(this, R.drawable.grid_overlay_golden)
                gridOverlay.visibility = View.VISIBLE
            }
        }
        val names = listOf("Off", "3x3", "4x4", "Golden")
        Toast.makeText(this, "Grid: ${names[currentGrid]}", Toast.LENGTH_SHORT).show()
    }

    private fun applyLevel() {
        if (isLevelOn) {
            levelLine.visibility = View.VISIBLE
            registerLevelSensor()
            Toast.makeText(this, "Level ON", Toast.LENGTH_SHORT).show()
        } else {
            levelLine.visibility = View.GONE
            unregisterLevelSensor()
            Toast.makeText(this, "Level OFF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyWatermark() {
        if (isWatermarkOn) {
            watermarkPreview.visibility = View.VISIBLE
            Toast.makeText(this, "Tanda Air ON", Toast.LENGTH_SHORT).show()
        } else {
            watermarkPreview.visibility = View.GONE
            Toast.makeText(this, "Tanda Air OFF", Toast.LENGTH_SHORT).show()
        }
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
            .scaleX(1f).scaleY(1f).alpha(0f)
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
            "NIGHT"    -> modeNight
            "PORTRAIT" -> modePortrait
            "PHOTO"    -> modePhoto
            "VIDEO"    -> modeVideo
            "VLOG"     -> modeVlog
            "PRO"      -> modePro
            else       -> modePhoto
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
        Log.d(TAG, "startCamera() — ratio=$currentRatio")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

                val aspectRatio = when (currentRatio) {
                    "16:9" -> AspectRatio.RATIO_16_9
                    else   -> AspectRatio.RATIO_4_3
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
                        var bitmap = image.toBitmap()
                        image.close()

                        if (currentRatio == "FULL") {
                            bitmap = cropToFullScreen(bitmap)
                        }

                        var finalBitmap = if (isMasterEffectOn) {
                            applyEffectProfile(bitmap, currentEffectProfile)
                        } else bitmap

                        if (isHdrEnabled && (currentMode == "PRO" || currentMode == "PORTRAIT" || currentMode == "PHOTO")) {
                            finalBitmap = applyEnhancement(finalBitmap)
                        }

                        if (isWatermarkOn) {
                            finalBitmap = applyWatermarkToBitmap(finalBitmap)
                        }

                        saveBitmapToGallery(finalBitmap)
                    } catch (e: Exception) {
                        Toast.makeText(baseContext, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // ================== CROP 9:19 ==================
    private fun cropToFullScreen(src: Bitmap): Bitmap {
        val srcRatio = src.width.toFloat() / src.height.toFloat()

        return if (srcRatio > FULL_SCREEN_RATIO) {
            val newWidth = (src.height * FULL_SCREEN_RATIO).toInt()
            val xOffset = (src.width - newWidth) / 2
            Bitmap.createBitmap(src, xOffset, 0, newWidth, src.height)
        } else {
            val newHeight = (src.width / FULL_SCREEN_RATIO).toInt()
            val yOffset = (src.height - newHeight) / 2
            Bitmap.createBitmap(src, 0, yOffset, src.width, newHeight)
        }
    }

    // ================== EFEK / COLOR GRADING ==================
    private fun applyEffectProfile(src: Bitmap, profile: String): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()

        val matrix = when (profile) {
            "IPHONE" -> ColorMatrix(floatArrayOf(
                1.10f, 0.00f, 0.00f, 0f, 8f,
                0.00f, 1.05f, 0.00f, 0f, 4f,
                0.00f, 0.00f, 0.95f, 0f, 0f,
                0.00f, 0.00f, 0.00f, 1f, 0f
            ))
            "SONY" -> ColorMatrix(floatArrayOf(
                1.15f, 0.05f, -0.05f, 0f, 5f,
                0.00f, 1.08f, 0.00f, 0f, 0f,
                0.00f, 0.02f, 1.10f, 0f, -5f,
                0.00f, 0.00f, 0.00f, 1f, 0f
            ))
            "FUJI" -> ColorMatrix(floatArrayOf(
                0.95f, 0.05f, 0.05f, 0f, 10f,
                0.05f, 0.95f, 0.05f, 0f, 8f,
                0.05f, 0.05f, 0.90f, 0f, 5f,
                0.00f, 0.00f, 0.00f, 1f, 0f
            ))
            "LEICA" -> ColorMatrix(floatArrayOf(
                1.05f, 0.02f, -0.02f, 0f, 3f,
                0.00f, 1.02f, 0.00f, 0f, 3f,
                0.00f, 0.00f, 1.05f, 0f, 3f,
                0.00f, 0.00f, 0.00f, 1f, 0f
            ))
            "BW" -> ColorMatrix().apply { setSaturation(0f) }
            "VINTAGE" -> ColorMatrix(floatArrayOf(
                1.10f, 0.10f, 0.10f, 0f, -10f,
                0.05f, 1.05f, 0.05f, 0f, -5f,
                0.00f, 0.00f, 0.90f, 0f, 10f,
                0.00f, 0.00f, 0.00f, 1f, 0f
            ))
            else -> ColorMatrix()
        }

        if (profile == "IPHONE" || profile == "SONY") {
            val sat = ColorMatrix().apply { setSaturation(1.15f) }
            matrix.postConcat(sat)
        }

        if (profile != "NONE") {
            val contrast = when (profile) {
                "SONY" -> 1.18f
                "IPHONE" -> 1.08f
                "FUJI" -> 1.10f
                else -> 1.05f
            }
            val t = (-0.5f * contrast + 0.5f) * 255f
            val cm = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, t,
                0f, contrast, 0f, 0f, t,
                0f, 0f, contrast, 0f, t,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(cm)
        }

        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
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

    // ================== WATERMARK ==================
    private fun applyWatermarkToBitmap(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        canvas.drawBitmap(src, 0f, 0f, null)

        val paint = Paint().apply {
            color = Color.WHITE
            alpha = 180
            textSize = src.width / 25f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }

        val text = "Ucam"
        val textWidth = paint.measureText(text)
        val x = src.width - textWidth - (src.width * 0.03f)
        val y = src.height - (src.height * 0.04f)

        canvas.drawText(text, x, y, paint)
        return dest
    }

    // ================== SAVE + EXIF ==================
    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val now = Date()
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(now)
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

            try {
                contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                    val exif = ExifInterface(pfd.fileDescriptor)

                    exif.setAttribute(ExifInterface.TAG_MAKE, "Ucam")
                    exif.setAttribute(ExifInterface.TAG_MODEL, "Ucam Camera App")
                    exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Ucam v1.0")

                    val dateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                    val dateTime = dateFormat.format(now)
                    exif.setAttribute(ExifInterface.TAG_DATETIME, dateTime)
                    exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTime)
                    exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateTime)

                    exif.setAttribute(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL.toString()
                    )

                    exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, bitmap.width.toString())
                    exif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, bitmap.height.toString())

                    exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "50/10")
                    exif.setAttribute(ExifInterface.TAG_F_NUMBER, "18/10")
                    exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "1/60")
                    exif.setAttribute(ExifInterface.TAG_ISO_SPEED, "400")
                    exif.setAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, "0/6")
                    exif.setAttribute(ExifInterface.TAG_FLASH, "0")
                    exif.setAttribute(ExifInterface.TAG_WHITE_BALANCE, "0")

                    exif.saveAttributes()
                    Log.d(TAG, "EXIF metadata ditulis")
                }
            } catch (e: Exception) {
                Log.e(TAG, "EXIF write error", e)
            }

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
