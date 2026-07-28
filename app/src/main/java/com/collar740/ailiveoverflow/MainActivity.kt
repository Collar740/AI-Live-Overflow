package com.collar740.ailiveoverflow

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.collar740.ailiveoverflow.service.PetOverlayService

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val statusText = findViewById<TextView>(R.id.statusText)
        val startBtn = findViewById<Button>(R.id.startBtn)
        val stopBtn = findViewById<Button>(R.id.stopBtn)
        updateStatus(statusText)
        startBtn.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
                Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
            } else {
                startForegroundService(Intent(this, PetOverlayService::class.java))
                Toast.makeText(this, "桌宠已启动", Toast.LENGTH_SHORT).show()
                updateStatus(statusText)
            }
        }
        stopBtn.setOnClickListener {
            stopService(Intent(this, PetOverlayService::class.java))
            Toast.makeText(this, "桌宠已停止", Toast.LENGTH_SHORT).show()
            updateStatus(statusText)
        }
    }
    private fun updateStatus(textView: TextView) {
        textView.text = if (Settings.canDrawOverlays(this)) "悬浮窗权限: 已授予" else "悬浮窗权限: 未授予"
    }
}
