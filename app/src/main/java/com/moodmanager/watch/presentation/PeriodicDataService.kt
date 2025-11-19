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

// Health Services (센서 접근용)
import androidx.health.services.client.HealthServices
import androidx.health.services.client.HealthServicesClient
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType

/**
 * 🩺 Mood Manager – 주기적 생체 데이터 수집 서비스
 *
 * 이 서비스는 Wear OS에서 1분마다 다음 데이터를 Firestore에 전송한다.
 *
 *  - heart_rate_avg        : 평균 심박수 (bpm)
 *  - heart_rate_min        : 최소 심박수 (bpm, 임시 계산)
 *  - heart_rate_max        : 최대 심박수 (bpm, 임시 계산)
 *  - hrv_sdnn              : 심박 변이도 SDNN (ms, 현재는 임시값)
 *  - respiratory_rate_avg  : 평균 호흡수 (rpm, 현재는 랜덤값)
 *  - movement_count        : 움직임 횟수 (현재는 랜덤/임시값)
 *  - is_fallback           : true 이면 전부 랜덤값 기반, false 이면 심박은 실제 센서 기반
 *  - timestamp             : 수집 시각 (ms)
 *
 * Firestore 경로:
 *   users/{TEST_USER_ID}/raw_periodic/{timestamp 문자열을 문서 ID로 사용}
 */
class PeriodicDataService : Service() {

    private val TAG = "PeriodicDataService"

    // TODO: 실제에서는 Firebase Auth uid 등으로 대체
    private val TEST_USER_ID = "testUser"

    // Cloud Firestore 인스턴스
    private val db = Firebase.firestore

    /**
     * 데이터 수집 간격 (밀리초)
     * - 현재: 1분 (테스트용)
     * - 실제 서비스에서는 10분(10 * 60 * 1000) 등으로 조정 가능
     */
    private val PERIODIC_INTERVAL_MS =  60 * 1000L   // 5분 주기 수집

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    // Foreground Service 알림용 설정
    private val NOTIFICATION_CHANNEL_ID = "PeriodicDataChannel"
    private val NOTIFICATION_ID = 1

    // ----------------------------
    // 🧠 Health Services 관련 필드
    // ----------------------------

    private lateinit var healthServicesClient: HealthServicesClient
    private lateinit var measureClient: MeasureClient
    private lateinit var measureCallback: MeasureCallback

    /**
     * 센서 콜백에서 갱신되는 최근 심박 값 (bpm)
     *  - 실제 워치에서만 의미 있는 값.
     *  - 에뮬레이터에서는 거의 항상 null → fallback 로직이 작동.
     */
    @Volatile
    private var latestHeartRate: Double? = null

    override fun onCreate() {
        super.onCreate()

        // Foreground 알림 채널 생성
        createNotificationChannel()

        // Health Services 클라이언트 초기화
        healthServicesClient = HealthServices.getClient(this)
        measureClient = healthServicesClient.measureClient

        Log.d(TAG, "PeriodicDataService created. Firestore instance=$db")

        // 센서 콜백 구현
        measureCallback = object : MeasureCallback {
            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability
            ) {
                if (availability is DataTypeAvailability) {
                    Log.d(TAG, "Sensor availability changed: $dataType = $availability")
                }
            }

            override fun onDataReceived(data: DataPointContainer) {
                // ✅ 심박 데이터(HEART_RATE_BPM)가 들어왔을 때 마지막 샘플 사용
                val heartRatePoints = data.getData(DataType.HEART_RATE_BPM)
                if (heartRatePoints.isNotEmpty()) {
                    val lastSample = heartRatePoints.last()
                    val value = lastSample.value
                    val bpm = when (value) {
                        is Double -> value
                        is Float -> value.toDouble()
                        is Int -> value.toDouble()
                        is Long -> value.toDouble()
                        else -> null
                    }

                    if (bpm != null) {
                        latestHeartRate = bpm
                        Log.d(TAG, "Measured heart rate from sensor: $bpm bpm")
                    } else {
                        Log.w(TAG, "Heart rate data point has unsupported value type: $value")
                    }
                }
            }
        }

        // 심박 측정 콜백 등록 (기기에서 지원할 경우 실시간 업데이트)
        try {
            measureClient.registerMeasureCallback(
                DataType.HEART_RATE_BPM,
                measureCallback
            )
            Log.d(TAG, "MeasureCallback registered for HEART_RATE_BPM.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register MeasureCallback. Will use fallback values only.", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Foreground 서비스로 승격 (상단바에 항상 표시)
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "Foreground Service started.")

        // 1분마다 실행할 작업 정의
        runnable = Runnable {
            Log.d(TAG, "Runnable executing: collecting periodic data and sending to Firestore...")
            collectAndSendPeriodicData()
            handler.postDelayed(runnable, PERIODIC_INTERVAL_MS)
        }

        // 즉시 한 번 실행 후, 이후부터 주기적으로 반복
        handler.post(runnable)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
        Log.d(TAG, "Foreground Service stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------
    // 🔁 1분마다 실행되는 "수집 → Firestore 전송" 메인 로직
    // -------------------------------------------------

    private fun collectAndSendPeriodicData() {
        val timestamp = System.currentTimeMillis()

        // 1) 센서 기반 데이터 구성 시도
        val sensorPayload = buildSensorBasedPayloadOrNull(timestamp)

        // 2) 센서 값이 없으면 fallback 랜덤값 사용
        val payload: Map<String, Any> = sensorPayload ?: buildFallbackPayload(timestamp)

        // 문서 ID를 timestamp 문자열로 고정해서 디버깅/정렬 쉽게
        val docId = timestamp.toString()

        Log.d(TAG, ">>> WILL SAVE PERIODIC DATA to Firestore: docId=$docId, data=$payload")

        // 🔥 여기 부분에서 뭐가 터지는지 보기 위해 try/catch + onComplete 추가
        try {
            val colRef = db.collection("users")
                .document(TEST_USER_ID)
                .collection("raw_periodic")

            Log.d(TAG, "Firestore collection path: ${colRef.path}")

            colRef
                .document(docId)
                .set(payload)
                .addOnSuccessListener {
                    Log.d(
                        TAG,
                        "✅ Periodic data saved to Firestore. docId=$docId"
                    )
                }
                .addOnFailureListener { e ->
                    Log.e(
                        TAG,
                        "❌ Error adding periodic data to Firestore (docId=$docId): ${e.message}",
                        e
                    )
                }
                .addOnCompleteListener { task ->
                    Log.d(
                        TAG,
                        "🔥 Firestore write COMPLETE (raw_periodic). success=${task.isSuccessful}, docId=$docId"
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Synchronous exception before Firestore write (raw_periodic)", e)
        }
    }

    /**
     * ✅ 센서 기반 payload 구성
     *
     * - latestHeartRate가 null이면 → 센서 값이 아직 없다고 판단하고 null 리턴 → fallback 사용
     * - 호흡수 / HRV / 움직임은 지금은 간단한 파생/랜덤값으로 채우고,
     *   나중에 가속도 센서/추가 API 붙이면 실제 계산 로직으로 교체 가능.
     */
    private fun buildSensorBasedPayloadOrNull(timestamp: Long): Map<String, Any>? {
        val hr = latestHeartRate ?: return null

        val heartRateAvg = hr.toInt()
        val heartRateMin = (heartRateAvg - 5).coerceAtLeast(40)
        val heartRateMax = (heartRateAvg + 10).coerceAtMost(150)

        // TODO: 나중에 실제 HRV 계산 로직으로 교체 (연속적인 rr-interval 기반)
        val hrvSdnn = (30..70).random()

        // TODO: 나중에 호흡/움직임도 실제 센서에서 추출
        val respiratoryRateAvg = (12..20).random()
        val movementCount = (0..10).random()

        return mapOf(
            "timestamp" to timestamp,
            "heart_rate_avg" to heartRateAvg,
            "heart_rate_min" to heartRateMin,
            "heart_rate_max" to heartRateMax,
            "hrv_sdnn" to hrvSdnn,
            "respiratory_rate_avg" to respiratoryRateAvg,
            "movement_count" to movementCount,
            // 심박은 실제 센서 측정값을 기반으로 했다는 표시
            "is_fallback" to false
        )
    }

    /**
     * ✅ fallback payload
     *
     * - 에뮬레이터, 센서 미지원, 초기 구동 등에서 사용되는 정상 범위 랜덤값.
     * - Next.js / ML 서버에서는 is_fallback=true 인 레코드는
     *   “테스트/시뮬레이션용”으로 구분해서 처리 가능.
     */
    private fun buildFallbackPayload(timestamp: Long): Map<String, Any> {
        val heartRateAvg = (60..85).random()
        val heartRateMin = (45..60).random()
        val heartRateMax = (90..120).random()

        val hrvSdnn = (20..70).random()
        val respiratoryRateAvg = (12..20).random()
        val movementCount = (0..15).random()

        return mapOf(
            "timestamp" to timestamp,
            "heart_rate_avg" to heartRateAvg,
            "heart_rate_min" to heartRateMin,
            "heart_rate_max" to heartRateMax,
            "hrv_sdnn" to hrvSdnn,
            "respiratory_rate_avg" to respiratoryRateAvg,
            "movement_count" to movementCount,
            "is_fallback" to true
        )
    }

    // ----------------------------
    // 🔔 Foreground 알림 관련 코드
    // ----------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Mood Manager Data Collection",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Mood Manager")
            .setContentText("데이터를 수집 중입니다...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }
}
