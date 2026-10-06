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
import android.view.ViewGroup
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

// =====================================================
// DATA CLASS & PRESET LIBRARY
// =====================================================
data class CameraPreset(
    val id: String,
    val name: String,
    val description: String,
    val saturation: Float = 1.0f,
    val contrast: Float = 1.0f,
    val brightness: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val shadowLift: Float = 0f,
    val highlightRolloff: Float = 0f,
    val vibrance: Float = 0f,
    val isBlackAndWhite: Boolean = false,
    val isVintage: Boolean = false
)

object PresetLibrary {
    val presets = listOf(
        CameraPreset("natural", "Natural", "Warna asli"),
        CameraPreset(
            "soft_pastel", "Soft Pastel", "Dreamy, cream tone",
            saturation = 0.85f, contrast = 0.85f, brightness = 12f,
            warmth = 8f, tint = 5f, shadowLift = 0.25f,
            highlightRolloff = 0.15f, vibrance = 10f
        ),
        CameraPreset("vivid", "Vivid", "Warna pop",
            saturation = 1.35f, contrast = 1.15f, brightness = 5f, warmth = 5f),
        CameraPreset("cinematic", "Cinematic", "Teal-orange Sony",
            saturation = 1.15f, contrast = 1.25f, brightness = -5f,
            warmth = 10f, tint = -10f, shadowLift = 0.15f, highlightRolloff = 0.2f),
        CameraPreset("soft_iphone", "Soft iPhone", "Hangat, lembut",
            saturation = 1.10f, contrast = 1.08f, brightness = 5f,
            warmth = 8f, shadowLift = 0.1f),
        CameraPreset("fuji", "Fuji Classic", "Filmic hijau",
            saturation = 0.95f, contrast = 1.12f, warmth = -5f, tint = 5f,
            shadowLift = 0.2f),
        CameraPreset("bw", "B&W", "Hitam putih",
            saturation = 0f, contrast = 1.3f, isBlackAndWhite = true),
        CameraPreset("vintage", "Vintage", "Sepia old",
            saturation = 0.8f, contrast = 1.1f, warmth = 15f, isVintage = true),
        CameraPreset("sunset_glow", "Sunset Glow", "Golden sunset",
            saturation = 1.15f, contrast = 1.05f, brightness = 8f,
            warmth = 20f, tint = 5f, shadowLift = 0.2f,
            highlightRolloff = 0.15f, vibrance = 12f),
        CameraPreset("cool_ocean", "Cool Ocean", "Biru dingin",
            saturation = 1.10f, contrast = 1.10f, brightness = 3f,
            warmth = -15f, tint = -8f, shadowLift = 0.15f, vibrance = 10f),
        CameraPreset("pink_dream", "Pink Dream", "Pink intens",
            saturation = 0.95f, contrast = 0.90f, brightness = 15f,
            warmth = 5f, tint = 18f, shadowLift = 0.3f,
            highlightRolloff = 0.2f, vibrance = 15f),
        CameraPreset("sepia_gold", "Sepia Gold", "Old money golden",
            saturation = 0.75f, contrast = 1.05f, brightness = 5f,
            warmth = 25f, tint = 8f, shadowLift = 0.25f, isVintage = true),
        CameraPreset("cyber_neon", "Cyber Neon", "Purple-blue neon",
            saturation = 1.30f, contrast = 1.20f, brightness = -8f,
            warmth = -10f, tint = -15f, shadowLift = 0.1f,
            highlightRolloff = 0.25f, vibrance = 20f)
    )
    fun getById(id: String): CameraPreset =
        presets.find { it.id == id } ?: presets[0]
}

// =====================================================
// CAMERA ACTIVITY
// =====================================================
class CameraActivity : AppCompatActivity() {

    // ================== VIEWS ==================
    private lateinit var viewFinder: PreviewView
    private lateinit var touchOverlay: View
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
    private lateinit var modePolaroid: TextView
    private lateinit var modePro: TextView
    private lateinit var btnGalleryPreview: ImageButton
    private lateinit var btnFilter: ImageButton
    private lateinit var btnCapture: View
    private lateinit var shutterInner: View
    private lateinit var btnSwitchCamera: ImageButton

    // Filter panel views
    private lateinit var filterPanel: android.widget.HorizontalScrollView
    private lateinit var filterList: android.widget.LinearLayout
    private lateinit var tvFilterLabel: TextView
    private lateinit var bottomRow: androidx.constraintlayout.widget.ConstraintLayout
    private lateinit var zoomBar: android.widget.LinearLayout
    private lateinit var modeBarContainer: android.widget.HorizontalScrollView
    private var isFilterPanelVisible = false

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
    private var currentPreset: CameraPreset = PresetLibrary.getById("soft_pastel")
    private var isPresetEnabled = true
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
        touchOverlay       = findViewById(R.id.touchOverlay)
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
        modePolaroid       = findViewById(R.id.modePolaroid)
        modePro            = findViewById(R.id.modePro)
        btnGalleryPreview  = findViewById(R.id.btnGalleryPreview)
        btnFilter          = findViewById(R.id.btnFilter)
        btnCapture         = findViewById(R.id.btnCapture)
        shutterInner       = findViewById(R.id.shutterInner)
        btnSwitchCamera    = findViewById(R.id.btnSwitchCamera)

        // Filter panel views
        filterPanel        = findViewById(R.id.filterPanel)
        filterList         = findViewById(R.id.filterList)
        tvFilterLabel      = findViewById(R.id.tvFilterLabel)
        bottomRow          = findViewById(R.id.bottomRow)
        zoomBar            = findViewById(R.id.zoomBar)
        modeBarContainer   = findViewById(R.id.modeBarContainer)
    }

    // ================== LISTENERS ==================
    private fun setupListeners() {
        btnFlash.setOnClickListener {
            flashState = (flashState + 1) % 3
            updateFlashUI()
            applyFlashToCapture()
            try { camera?.cameraControl?.enableTorch(flashState == 1) } catch (_: Exception) {}
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
        modePolaroid.setOnClickListener { switchMode("POLAROID") }
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

        // ✅ Tombol ✨ filter — hide/show elemen
        btnFilter.setOnClickListener {
            isFilterPanelVisible = !isFilterPanelVisible
            if (isFilterPanelVisible) {
                zoomBar.visibility = View.GONE
                modeBarContainer.visibility = View.GONE
                bottomRow.visibility = View.GONE
                filterPanel.visibility = View.VISIBLE
                tvFilterLabel.visibility = View.VISIBLE
                populateFilterList()
            } else {
                zoomBar.visibility = View.VISIBLE
                modeBarContainer.visibility = View.VISIBLE
                bottomRow.visibility = View.VISIBLE
                filterPanel.visibility = View.GONE
                tvFilterLabel.visibility = View.GONE
            }
        }

        btnCapture.setOnClickListener {
            if (currentMode == "VIDEO") {
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

        touchOverlay.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val factory = viewFinder.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(
                    point,
                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                ).setAutoCancelDuration(5, TimeUnit.SECONDS).build()
                camera?.cameraControl?.startFocusAndMetering(action)
                    ?.addListener({ Log.d(TAG, "Focus & metering selesai") }, ContextCompat.getMainExecutor(this))
                showFocusRing(event.x, event.y)
                if (isFocusPeakingOn) {
                    focusPeakingRing.translationX = event.x - focusPeakingRing.width / 2f
                    focusPeakingRing.translationY = event.y - focusPeakingRing.height / 2f
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

    // ================== POPULATE FILTER LIST ==================
    private fun populateFilterList() {
        filterList.removeAllViews()

        PresetLibrary.presets.forEach { preset ->
            val container = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(12, 4, 12, 4)
            }

            val thumb = View(this).apply {
                val size = 140
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size)
                setBackgroundColor(getColorForPreset(preset))
                foreground = ContextCompat.getDrawable(
                    this@CameraActivity,
                    if (preset.id == currentPreset.id) R.drawable.filter_thumb_active
                    else R.drawable.filter_thumb_inactive
                )
            }

            val label = TextView(this).apply {
                text = preset.name
                setTextColor(if (preset.id == currentPreset.id) Color.parseColor("#FFC107") else Color.WHITE)
                textSize = 10f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 6, 0, 0)
            }

            container.addView(thumb)
            container.addView(label)

            container.setOnClickListener {
                currentPreset = preset
                isPresetEnabled = (preset.id != "natural")
                populateFilterList()
                Toast.makeText(this, "Filter: ${preset.name}", Toast.LENGTH_SHORT).show()

                // ✅ AUTO-CLOSE — tutup panel & munculkan elemen kembali
                btnFilter.postDelayed({
                    isFilterPanelVisible = false
                    zoomBar.visibility = View.VISIBLE
                    modeBarContainer.visibility = View.VISIBLE
                    bottomRow.visibility = View.VISIBLE
                    filterPanel.visibility = View.GONE
                    tvFilterLabel.visibility = View.GONE
                }, 400)
            }

            filterList.addView(container)
        }
    }

    private fun getColorForPreset(preset: CameraPreset): Int {
        return when (preset.id) {
            "natural"      -> Color.parseColor("#AAAAAA")
            "soft_pastel"  -> Color.parseColor("#F5D7D7")
            "vivid"        -> Color.parseColor("#FF6B6B")
            "cinematic"    -> Color.parseColor("#2C5F7F")
            "soft_iphone"  -> Color.parseColor("#F5C99A")
            "fuji"         -> Color.parseColor("#8BAA7A")
            "bw"           -> Color.parseColor("#555555")
            "vintage"      -> Color.parseColor("#B8885A")
            "sunset_glow"  -> Color.parseColor("#FF8C42")
            "cool_ocean"   -> Color.parseColor("#4A90C2")
            "pink_dream"   -> Color.parseColor("#FFB6D9")
            "sepia_gold"   -> Color.parseColor("#C2A56B")
            "cyber_neon"   -> Color.parseColor("#8B00FF")
            else           -> Color.GRAY
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
        ratio11.setOnClickListener   { currentRatio = "1:1";  styleGroup(ratio11, true, ratioGroup);  applyAspectRatio() }
        ratio43.setOnClickListener   { currentRatio = "4:3";  styleGroup(ratio43, true, ratioGroup);  applyAspectRatio() }
        ratio169.setOnClickListener  { currentRatio = "16:9"; styleGroup(ratio169, true, ratioGroup); applyAspectRatio() }
        ratioFull.setOnClickListener { currentRatio = "FULL"; styleGroup(ratioFull, true, ratioGroup); applyAspectRatio() }
        timerOff.setOnClickListener  { currentTimer = 0; timerState = 0; styleGroup(timerOff, true, timerGroup); updateTimerUI() }
        timer3.setOnClickListener    { currentTimer = 1; timerState = 1; styleGroup(timer3, true, timerGroup);   updateTimerUI() }
        timer5.setOnClickListener    { currentTimer = 2; timerState = 2; styleGroup(timer5, true, timerGroup);   updateTimerUI() }
        timer10.setOnClickListener   { currentTimer = 3; timerState = 3; styleGroup(timer10, true, timerGroup);  updateTimerUI() }
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
        }
        btnMaster.setOnClickListener {
            isMasterEffectOn = !isMasterEffectOn
            btnMaster.background.setTint(if (isMasterEffectOn) Color.parseColor("#FFC107") else Color.WHITE)
        }
        btnWatermark.setOnClickListener {
            isWatermarkOn = !isWatermarkOn
            btnWatermark.background.setTint(if (isWatermarkOn) Color.parseColor("#FFC107") else Color.WHITE)
            applyWatermark()
        }
        btnFocus.setOnClickListener {
            isFocusPeakingOn = !isFocusPeakingOn
            btnFocus.background.setTint(if (isFocusPeakingOn) Color.parseColor("#FFC107") else Color.WHITE)
        }
        dialog.show()
    }

    // ================== APPLY VISUAL ==================
    private fun applyAspectRatio() { startCamera() }

    private fun applyGrid() {
        when (currentGrid) {
            0 -> gridOverlay.visibility = View.GONE
            1 -> { gridOverlay.background = ContextCompat.getDrawable(this, R.drawable.grid_overlay); gridOverlay.visibility = View.VISIBLE }
            2 -> { gridOverlay.background = ContextCompat.getDrawable(this, R.drawable.grid_overlay_4x4); gridOverlay.visibility = View.VISIBLE }
            3 -> { gridOverlay.background = ContextCompat.getDrawable(this, R.drawable.grid_overlay_golden); gridOverlay.visibility = View.VISIBLE }
        }
    }

    private fun applyLevel() {
        if (isLevelOn) { levelLine.visibility = View.VISIBLE; registerLevelSensor() }
        else { levelLine.visibility = View.GONE; unregisterLevelSensor() }
    }

    private fun applyWatermark() {
        watermarkPreview.visibility = if (isWatermarkOn) View.VISIBLE else View.GONE
    }

    // ================== UI UPDATES ==================
    private fun updateFlashUI() {
        when (flashState) {
            0 -> btnFlash.setImageResource(R.drawable.ic_flash_off)
            1 -> btnFlash.setImageResource(R.drawable.ic_flash_on)
            2 -> btnFlash.setImageResource(R.drawable.ic_flash_auto)
        }
    }

    private fun updateTimerUI() {
        when (timerState) {
            0 -> { btnTimer.setImageResource(R.drawable.ic_timer_off); tvTimerText.text = "( 00 , 00 )"; tvTimerText.alpha = 0.5f }
            1 -> { btnTimer.setImageResource(R.drawable.ic_timer_on);  tvTimerText.text = "( 03 , 00 )"; tvTimerText.alpha = 1.0f }
            2 -> { btnTimer.setImageResource(R.drawable.ic_timer_on);  tvTimerText.text = "( 05 , 00 )"; tvTimerText.alpha = 1.0f }
            3 -> { btnTimer.setImageResource(R.drawable.ic_timer_on);  tvTimerText.text = "( 10 , 00 )"; tvTimerText.alpha = 1.0f }
        }
    }

    private fun updateZoomUI() {
        val inactive = Color.parseColor("#99FFFFFF")
        val active = Color.parseColor("#FFC107")
        listOf(zoom06, zoom1x, zoom2x).forEach {
            it.setTextColor(inactive)
            it.setTypeface(Typeface.DEFAULT)
            it.textSize = 12f
        }
        val activeView = when (zoomIndex) { 0 -> zoom06; 1 -> zoom1x; else -> zoom2x }
        activeView.setTextColor(active)
        activeView.setTypeface(Typeface.DEFAULT_BOLD)
        activeView.textSize = 13f
    }

    private fun showFocusRing(touchX: Float, touchY: Float) {
        if (focusRing.width == 0) {
            focusRing.post { showFocusRing(touchX, touchY) }
            return
        }
        focusRing.translationX = touchX - focusRing.width / 2f
        focusRing.translationY = touchY - focusRing.height / 2f
        focusRing.visibility = View.VISIBLE
        focusRing.alpha = 1f
        focusRing.scaleX = 1.5f
        focusRing.scaleY = 1.5f
        focusRing.animate()
            .scaleX(1f).scaleY(1f).setDuration(300)
            .withEndAction {
                focusRing.animate().alpha(0f).setDuration(800)
                    .withEndAction { focusRing.visibility = View.INVISIBLE }
                    .start()
            }.start()
    }

    // ================== MODE ==================
    private fun switchMode(newMode: String) {
        currentMode = newMode
        val inactive = Color.parseColor("#99FFFFFF")
        val active = Color.parseColor("#FFC107")
        listOf(modeNight, modePortrait, modePhoto, modeVideo, modePolaroid, modePro).forEach {
            it.setTextColor(inactive); it.setTypeface(Typeface.DEFAULT); it.textSize = 13f
        }
        val activeView = when (newMode) {
            "NIGHT" -> modeNight; "PORTRAIT" -> modePortrait; "PHOTO" -> modePhoto
            "VIDEO" -> modeVideo; "POLAROID" -> modePolaroid; "PRO" -> modePro
            else -> modePhoto
        }
        activeView.setTextColor(active); activeView.setTypeface(Typeface.DEFAULT_BOLD); activeView.textSize = 15f
    }

    // ================== ZOOM ==================
    private fun setZoomByIndex(index: Int) {
        zoomIndex = index; updateZoomUI(); setSafeZoom(zoomValues[index])
    }

    private fun setSafeZoom(ratio: Float) {
        val cam = camera ?: return
        val state = cam.cameraInfo.zoomState.value ?: return
        val clamped = ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        cam.cameraControl.setZoomRatio(clamped)
    }

    // ================== CAMERA ==================
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                val aspectRatio = when (currentRatio) { "16:9" -> AspectRatio.RATIO_16_9; else -> AspectRatio.RATIO_4_3 }
                val preview = Preview.Builder().setTargetAspectRatio(aspectRatio).build().also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetAspectRatio(aspectRatio).build()
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, selector, preview, imageCapture)
                setSafeZoom(zoomValues[zoomIndex])
                applyFlashToCapture()
                updatePreviewScaleType()
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind FAILED", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updatePreviewScaleType() {
        when (currentRatio) {
            "1:1" -> {
                val size = viewFinder.width.coerceAtMost(viewFinder.height)
                val params = viewFinder.layoutParams
                params.width = size; params.height = size
                viewFinder.layoutParams = params
                viewFinder.scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            "4:3", "16:9" -> {
                val params = viewFinder.layoutParams
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                viewFinder.layoutParams = params
                viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER
            }
            "FULL" -> {
                val params = viewFinder.layoutParams
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                viewFinder.layoutParams = params
                viewFinder.scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
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
        if (seconds == 0) takePhoto()
        else {
            Toast.makeText(this, "Timer: $seconds detik", Toast.LENGTH_SHORT).show()
            object : CountDownTimer(seconds * 1000L, 1000L) {
                override fun onTick(millisUntilFinished: Long) {
                    tvTimerText.text = "( 0${millisUntilFinished / 1000 + 1} , 00 )"
                }
                override fun onFinish() { updateTimerUI(); takePhoto() }
            }.start()
        }
    }

    private fun takePhoto() {
        val ic = imageCapture ?: run {
            Toast.makeText(this, "Kamera belum siap", Toast.LENGTH_SHORT).show()
            return
        }
        shutterInner.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80)
            .withEndAction { shutterInner.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }.start()

        ic.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exc: ImageCaptureException) {}
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        var bitmap = image.toBitmap()
                        image.close()

                        if (currentRatio == "FULL") bitmap = cropToFullScreen(bitmap)

                        var finalBitmap = applyMiniISP(bitmap)

                        if (isPresetEnabled && currentPreset.id != "natural") {
                            finalBitmap = applyPreset(finalBitmap, currentPreset)
                        }

                        if (isWatermarkOn) finalBitmap = applyWatermarkToBitmap(finalBitmap)

                        if (currentMode == "POLAROID") finalBitmap = applyPolaroid(finalBitmap)

                        saveBitmapToGallery(finalBitmap)
                    } catch (e: Exception) {
                        Toast.makeText(baseContext, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun cropToFullScreen(src: Bitmap): Bitmap {
        val srcRatio = src.width.toFloat() / src.height.toFloat()
        return if (srcRatio > FULL_SCREEN_RATIO) {
            val newWidth = (src.height * FULL_SCREEN_RATIO).toInt()
            Bitmap.createBitmap(src, (src.width - newWidth) / 2, 0, newWidth, src.height)
        } else {
            val newHeight = (src.width / FULL_SCREEN_RATIO).toInt()
            Bitmap.createBitmap(src, 0, (src.height - newHeight) / 2, src.width, newHeight)
        }
    }

    // ================== MINI-ISP (OPTIMIZED) ==================
    private fun applyMiniISP(src: Bitmap): Bitmap {
        val wbMatrix = calculateWhiteBalanceMatrix(src)
        val toneMatrix = ColorMatrix(floatArrayOf(
            1.10f, 0f, 0f, 0f, 14f,
            0f, 1.10f, 0f, 0f, 14f,
            0f, 0f, 1.10f, 0f, 14f,
            0f, 0f, 0f, 1f, 0f
        ))
        val satMatrix = ColorMatrix().apply { setSaturation(1.12f) }
        val microContrast = ColorMatrix(floatArrayOf(
            1.05f, 0f, 0f, 0f, 0f,
            0f, 1.05f, 0f, 0f, 0f,
            0f, 0f, 1.05f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        wbMatrix.postConcat(toneMatrix)
        wbMatrix.postConcat(satMatrix)
        wbMatrix.postConcat(microContrast)

        val dest = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(wbMatrix)
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    private fun calculateWhiteBalanceMatrix(src: Bitmap): ColorMatrix {
        var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0
        val step = 30
        for (x in 0 until src.width step step) {
            for (y in 0 until src.height step step) {
                val p = src.getPixel(x, y)
                rSum += (p shr 16) and 0xFF
                gSum += (p shr 8) and 0xFF
                bSum += p and 0xFF
                count++
            }
        }
        if (count == 0) return ColorMatrix()
        val rAvg = rSum.toFloat() / count
        val gAvg = gSum.toFloat() / count
        val bAvg = bSum.toFloat() / count
        if (rAvg == 0f || gAvg == 0f || bAvg == 0f) return ColorMatrix()
        val gray = (rAvg + gAvg + bAvg) / 3f
        val rScale = (gray / rAvg).coerceIn(0.85f, 1.15f)
        val gScale = (gray / gAvg).coerceIn(0.85f, 1.15f)
        val bScale = (gray / bAvg).coerceIn(0.85f, 1.15f)
        return ColorMatrix(floatArrayOf(
            rScale, 0f, 0f, 0f, 0f,
            0f, gScale, 0f, 0f, 0f,
            0f, 0f, bScale, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    // ================== APPLY PRESET ==================
    private fun applyPreset(src: Bitmap, preset: CameraPreset): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()

        val c = preset.contrast
        val b = preset.brightness
        val t = (-0.5f * c + 0.5f) * 255f + b
        val matrix = ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f
        ))
        if (preset.saturation != 1f) matrix.postConcat(ColorMatrix().apply { setSaturation(preset.saturation) })
        if (preset.warmth != 0f) {
            val w = preset.warmth
            matrix.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, w, 0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, -w, 0f, 0f, 0f, 1f, 0f
            )))
        }
        if (preset.tint != 0f) {
            val tn = preset.tint
            matrix.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, tn * 0.5f, 0f, 1f, 0f, 0f, -tn,
                0f, 0f, 1f, 0f, tn, 0f, 0f, 0f, 1f, 0f
            )))
        }
        if (preset.shadowLift > 0f) {
            val lift = preset.shadowLift * 40f
            matrix.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, lift, 0f, 1f, 0f, 0f, lift,
                0f, 0f, 1f, 0f, lift, 0f, 0f, 0f, 1f, 0f
            )))
        }
        if (preset.vibrance > 0f) {
            matrix.postConcat(ColorMatrix().apply { setSaturation(1f + preset.vibrance / 100f) })
        }
        if (preset.isBlackAndWhite) matrix.postConcat(ColorMatrix().apply { setSaturation(0f) })
        if (preset.isVintage) {
            matrix.postConcat(ColorMatrix(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    // ================== WATERMARK ==================
    private fun applyWatermarkToBitmap(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        canvas.drawBitmap(src, 0f, 0f, null)
        val paint = Paint().apply {
            color = Color.WHITE; alpha = 180; textSize = src.width / 25f
            isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }
        val text = "Ucam"
        val textWidth = paint.measureText(text)
        canvas.drawText(text, src.width - textWidth - (src.width * 0.03f), src.height - (src.height * 0.04f), paint)
        return dest
    }

    // ================== POLAROID ==================
    private fun applyPolaroid(src: Bitmap): Bitmap {
        val vintageMatrix = ColorMatrix().apply { setSaturation(0.85f) }
        val warmMatrix = ColorMatrix(floatArrayOf(
            1.05f, 0f, 0f, 0f, 8f,
            0f, 1.02f, 0f, 0f, 4f,
            0f, 0f, 0.95f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        vintageMatrix.postConcat(warmMatrix)

        val borderSide = (src.width * 0.08f).toInt()
        val borderTop = (src.width * 0.08f).toInt()
        val borderBottom = (src.width * 0.25f).toInt()

        val polaroidWidth = src.width + borderSide * 2
        val polaroidHeight = src.height + borderTop + borderBottom

        val polaroid = Bitmap.createBitmap(polaroidWidth, polaroidHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(polaroid)
        canvas.drawColor(Color.parseColor("#FFFEF7"))

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(vintageMatrix)
            isAntiAlias = true
            isFilterBitmap = true
        }
        val photoRect = android.graphics.Rect(
            borderSide, borderTop,
            borderSide + src.width,
            borderTop + src.height
        )
        canvas.drawBitmap(src, null, photoRect, paint)

        val textPaint = Paint().apply {
            color = Color.parseColor("#333333")
            textSize = src.width / 18f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            textAlign = Paint.Align.CENTER
        }
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
        val text = "Ucam • ${dateFormat.format(Date())}"
        val textX = polaroidWidth / 2f
        val textY = polaroidHeight - (borderBottom * 0.4f)
        canvas.drawText(text, textX, textY, textPaint)

        return polaroid
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
            stream?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) }
            try {
                contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                    val exif = ExifInterface(pfd.fileDescriptor)
                    exif.setAttribute(ExifInterface.TAG_MAKE, "Ucam")
                    exif.setAttribute(ExifInterface.TAG_MODEL, "Ucam Camera App")
                    exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Ucam v1.0")
                    val df = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                    val dt = df.format(now)
                    exif.setAttribute(ExifInterface.TAG_DATETIME, dt)
                    exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dt)
                    exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                    exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, bitmap.width.toString())
                    exif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, bitmap.height.toString())
                    exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "25/1")
                    exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, "25")
                    exif.setAttribute(ExifInterface.TAG_F_NUMBER, "18/10")
                    exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "1/60")
                    exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, "400")
                    exif.saveAttributes()
                }
            } catch (e: Exception) { Log.e(TAG, "EXIF error", e) }
            Toast.makeText(this, "Foto disimpan!", Toast.LENGTH_SHORT).show()
            cameraExecutor.execute { loadLastPhotoThumbnail() }
        }
    }

    // ================== THUMBNAIL ==================
    private fun loadLastPhotoThumbnail() {
        try {
            val cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED),
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
                arrayOf("%Pictures/UcamApp%"),
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
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
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
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
