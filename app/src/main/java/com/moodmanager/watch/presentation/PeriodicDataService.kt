// app/src/main/java/com/moodmanager/watch/presentation/PeriodicDataService.kt
package com.moodmanager.watch.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.moodmanager.watch.R // ◀◀ 앱 아이콘(R)을 가져오기 위해 import

class PeriodicDataService : Service() {

    private val TAG = "PeriodicDataService"
    private val TEST_USER_ID = "testUser"
    private val db = Firebase.firestore

    // 10분마다 실행 (밀리초 단위)
    private val PERIODIC_INTERVAL_MS = 1 * 60 * 1000L

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    private val NOTIFICATION_CHANNEL_ID = "PeriodicDataChannel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        // 알림 채널을 미리 생성합니다.
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 서비스가 시작되면, OS에 "이것은 Foreground Service"임을 알림과 함께 신고합니다.
        startForeground(NOTIFICATION_ID, createNotification())

        Log.d(TAG, "Foreground Service started.")

        // 주기적 작업을 위한 Runnable(실행할 코드 덩어리) 정의
        runnable = Runnable {
            Log.d(TAG, "Runnable executing: Sending periodic data...")
            // 10분마다 이 함수가 호출됩니다.
            sendDummyPeriodicData()

            // 10분 뒤에 자기 자신을 다시 실행하도록 예약합니다.
            handler.postDelayed(runnable, PERIODIC_INTERVAL_MS)
        }

        // 즉시 첫 실행 및 주기적 실행 시작
        handler.post(runnable)

        // START_STICKY: 서비스가 시스템에 의해 종료되더라도,
        // 시스템 여유가 생기면 서비스를 자동으로 다시 시작시킵니다.
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // 서비스가 종료되면, 예약된 모든 작업을 제거합니다.
        handler.removeCallbacks(runnable)
        Log.d(TAG, "Foreground Service stopped.")
    }

    /**
     * [주기적 데이터] (스트레스/수면 분석용 '재료')
     * 10분마다 백그라운드에서 호출되는 함수입니다.
     */
    private fun sendDummyPeriodicData() {
        val periodicData = hashMapOf(
            "timestamp" to System.currentTimeMillis(),
            "heart_rate_avg" to (60..85).random(),
            "hrv_sdnn" to (20..70).random(),
            "respiratory_rate_avg" to (12..20).random(),
            "movement_count" to (0..15).random()
        )

        db.collection("users").document(TEST_USER_ID)
            .collection("raw_periodic")
            .add(periodicData)
            .addOnSuccessListener {
                Log.d(TAG, "Periodic data added successfully via Service!")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding periodic data via Service", e)
            }
    }

    // --- 알림 관련 필수 함수들 ---

    // 알림 채널 생성 (Android 8.0 이상 필수)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Mood Manager Data Collection",
                NotificationManager.IMPORTANCE_LOW // 백그라운드 작업이므로 낮은 중요도
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    // Foreground Service를 위한 영구 알림 생성
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Mood Manager")
            .setContentText("데이터를 수집 중입니다...")
            .setSmallIcon(R.mipmap.ic_launcher) // ◀◀ 앱 아이콘 사용
            .setOngoing(true) // 사용자가 지울 수 없는 알림
            .build()
    }

    // 바인딩은 사용하지 않으므로 null 반환
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}