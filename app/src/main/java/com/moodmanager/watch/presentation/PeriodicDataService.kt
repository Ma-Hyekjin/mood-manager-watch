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
import com.moodmanager.watch.R
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureClient

/**
 * PeriodicDataService
 *
 * - 워치에서 백그라운드로 돌아가는 "포그라운드 서비스".
 * - 하는 일:
 *   1) 일정 주기(현재: 1분)에 한 번씩 실행된다.
 *   2) 그 시점의 생체 정보를 바탕으로 raw_periodic 문서를 만든다.
 *   3) Firestore(users/{userId}/raw_periodic)에 add()로 누적 저장한다.
 *
 * - Next.js 백엔드는 이 raw_periodic 스트림을 구독(onSnapshot)해서,
 *   - 스트레스 지수(1~100)
 *   - 수면 점수(1~100)
 *   를 계산/전처리하게 된다.
 *
 * 현재 단계:
 *   - Health Services의 MeasureClient까지는 초기화해두고,
 *   - 실제 데이터는 아직 "더미 랜덤 값"을 넣는 상태.
 *   - TODO: 추후 MeasureClient를 사용해 심박/호흡 데이터 등을 실제로 채워 넣는다.
 */
class PeriodicDataService : Service() {

    private val TAG = "PeriodicDataService"

    // TODO: 추후 실제 사용자 ID와 연결 (워치-폰 페어링 or 로그인 기반)
    private val TEST_USER_ID = "testUser"

    // Firestore 인스턴스
    private val db = Firebase.firestore

    /**
     * Health Services 측정 클라이언트
     *
     * - 심박 등 "실시간 측정 데이터"를 읽을 때 사용할 클라이언트.
     * - 지금은 스켈레톤만 초기화해두고, 실제 값은 더미로 생성한다.
     */
    private lateinit var measureClient: MeasureClient

    /**
     * 데이터 수집 주기 (밀리초)
     * - 실제 목표: 10분(10 * 60 * 1000L)
     * - 개발/테스트 편의를 위해 현재는 1분(1 * 60 * 1000L)으로 설정.
     */
    private val PERIODIC_INTERVAL_MS = 1 * 60 * 1000L

    // 주기 실행을 위한 Handler와 Runnable
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    // 포그라운드 서비스 알림용 채널/ID
    private val NOTIFICATION_CHANNEL_ID = "PeriodicDataChannel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()

        // Health Services MeasureClient 초기화
        // - 이 클라이언트를 통해 나중에 실제 심박/호흡/활동 데이터를 읽게 된다.
        val healthServicesClient = HealthServices.getClient(this)
        measureClient = healthServicesClient.measureClient

        // 포그라운드 알림 채널 생성
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 포그라운드 서비스 시작 (항상 상단 알림 유지)
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "Foreground Service started.")

        // 주기적으로 실행할 작업 정의
        runnable = Runnable {
            Log.d(TAG, "Runnable executing: Sending periodic data...")

            // 1. (현재는) 더미 데이터 생성 후 Firestore로 전송
            // 2. (향후) measureClient를 통해 실제 센서 데이터를 읽고, 아래 함수 내부를 교체 예정
            sendDummyPeriodicData()

            // 다음 실행 예약
            handler.postDelayed(runnable, PERIODIC_INTERVAL_MS)
        }

        // 서비스 시작 시 즉시 한 번 실행하고, 이후 주기적으로 반복
        handler.post(runnable)

        // START_STICKY:
        // - 시스템이 메모리 부족으로 서비스를 제거해도,
        //   여유가 생기면 다시 자동으로 재시작하도록 요청.
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // 예약된 Runnable 제거 (메모리 릭 방지)
        handler.removeCallbacks(runnable)
        Log.d(TAG, "Foreground Service stopped.")
    }

    /**
     [주기적 데이터] raw_periodic 문서를 생성하여 Firestore에 업로드

     지금은 개발 단계이므로 "랜덤 더미 값"을 사용하고, 나중에 Health Services 측정값으로 교체 예정

     필드 요약 (Next.js/ML 관점)
     - timestamp            : 수집 시각(ms)
     - heart_rate_avg       : 10분 평균 심박수 (bpm)
     - hrv_sdnn             : 심박 변이도 SDNN (ms), 낮을수록 스트레스↑
     - respiratory_rate_avg : 평균 호흡수 (회/분), 높을수록 긴장 상태 가능성↑
     - movement_count       : 해당 구간 내 움직임 횟수, 수면 중에는 낮을수록 깊은 수면
     */
    private fun sendDummyPeriodicData() {
        val periodicData = hashMapOf(
            "timestamp" to System.currentTimeMillis(),

            // (더미) 10분 평균 심박수 (60~85 bpm)
            "heart_rate_avg" to (60..85).random(),

            // (더미) 심박 변이도 SDNN (20~70 ms)
            "hrv_sdnn" to (20..70).random(),

            // (더미) 평균 호흡수 (12~20 회/분)
            "respiratory_rate_avg" to (12..20).random(),

            // (더미) 움직임 횟수 (0~15 회)
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

    // 포그라운드 서비스용 알림 채널 생성 - Android 8.0 이상에서 알림을 표시하기 위해 채널 필요

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Mood Manager Data Collection",    // 설정 화면에 보이는 채널 이름
                NotificationManager.IMPORTANCE_LOW // 백그라운드 작업 → 낮은 중요도
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    // 포그라운드 서비스 유지용 알림 - 해당 알림을 통해 "현재 워치가 Mood Manager 데이터를 수집 중"이라는 사실을 인지 가능
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Mood Manager")
            .setContentText("데이터를 수집 중입니다...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    // 바인딩 방식은 사용하지 않으므로 null 반환
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
