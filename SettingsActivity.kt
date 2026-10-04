package com.example.camera

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.title = "Pengaturan lainnya"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val sharedPrefs = getSharedPreferences("CameraSettings", Context.MODE_PRIVATE)

        val switchShutterSound = findViewById<Switch>(R.id.switchShutterSound)
        val switchLocation = findViewById<Switch>(R.id.switchLocation)
        val switchMirror = findViewById<Switch>(R.id.switchMirror)
        val switchWatermark = findViewById<Switch>(R.id.switchWatermark)
        val btnResetDefault = findViewById<Button>(R.id.btnResetDefault)

        // Muat data pengaturan tersimpan
        switchShutterSound.isChecked = sharedPrefs.getBoolean("shutter_sound", false)
        switchLocation.isChecked = sharedPrefs.getBoolean("location", false)
        switchMirror.isChecked = sharedPrefs.getBoolean("mirror", true)
        switchWatermark.isChecked = sharedPrefs.getBoolean("watermark", false)

        // Simpan otomatis saat tombol switch diubah
        switchShutterSound.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("shutter_sound", isChecked).apply()
        }
        switchLocation.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("location", isChecked).apply()
        }
        switchMirror.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("mirror", isChecked).apply()
        }
        switchWatermark.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("watermark", isChecked).apply()
        }

        // Tombol Reset Default
        btnResetDefault.setOnClickListener {
            sharedPrefs.edit().clear().apply()
            switchShutterSound.isChecked = false
            switchLocation.isChecked = false
            switchMirror.isChecked = true
            switchWatermark.isChecked = false
            Toast.makeText(this, "Pengaturan dipulihkan ke default", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}




app/src/main/java/com/example/camera/SettingsActivity.kt