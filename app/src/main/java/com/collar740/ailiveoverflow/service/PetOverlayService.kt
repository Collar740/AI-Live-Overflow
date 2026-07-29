package com.collar740.ailiveoverflow.service

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.*
import android.webkit.*
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.*
import java.net.*
import java.util.*
import kotlin.concurrent.thread

private const val SUPABASE_URL = "https://kehhbwgfhjmvkxneqlyd.supabase.co"
private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtlaGhid2dmaGptdmt4bmVxbHlkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODUyNDU4NDksImV4cCI6MjEwMDgyMTg0OX0.ye5HAZkgnH6_aqPpX5y4fcAtOagehB6aqX7WE7EjBXQ"
private const val MACHINE_ID = "qisli"
