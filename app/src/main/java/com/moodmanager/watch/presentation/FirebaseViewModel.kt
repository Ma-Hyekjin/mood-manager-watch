// app/src/main/java/com/moodmanager/watch/presentation/FirebaseViewModel.kt
package com.moodmanager.watch.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class FirebaseViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val TAG = "FirebaseViewModel"
    private val TEST_USER_ID = "testUser" // 테스트용 사용자 ID

    /**
     * [주기적 데이터] (스트레스/수면 분석용 '재료')
     * - Next.js 백엔드는 이 'raw_periodic' 컬렉션의 데이터를 누적/분석하여
     * '스트레스 지수(1~100)', '수면 점수(1~100)'를 계산(전처리)합니다.
     */
    fun sendDummyPeriodicData() {
        val periodicData = hashMapOf(
            // --- 공통 ---
            "timestamp" to System.currentTimeMillis(), // 데이터 수집 시간 (모든 분석의 기준)

            // --- 스트레스 지수 (P2) 분석용 재료 ---
            "heart_rate_avg" to (60..85).random(), // (더미) 10분간 평균 심박수
            "heart_rate_max" to (90..120).random(), // (더미) 10분간 최고 심박수 (급격한 스트레스 감지용)
            "hrv_sdnn" to (20..70).random(),         // (더미) 10분간 심박 변이도 (SDNN)
            "respiratory_rate_avg" to (12..20).random(), // (더미) 10분간 평균 호흡수

            // --- 수면 패턴 (P2/P3) 분석용 재료 ---
            "heart_rate_min" to (45..55).random(), // (더미) 10분간 최저 심박수 (깊은 수면 감지용)
            "movement_count" to (0..15).random()   // (더미) 10분간 움직임 감지 횟수

            // TO-DO (P2): Health Connect API 연동
            // - [ ] 'HeartRateRecord'에서 10분간의 avg, max, min 값 집계
            // - [ ] 'HeartRateVariabilityRmssdRecord' (또는 SDNN)에서 hrv_sdnn 값 집계
            // - [ ] 'RespiratoryRateRecord'에서 avg 값 집계
            // - [ ] 'SleepSessionRecord' (또는 가속도계)에서 movement_count 집계
        )

        // [설명]
        // Next.js가 이 데이터를 바탕으로 '규칙'을 만듭니다.
        // 예: "IF (hrv_sdnn < 30) AND (respiratory_rate_avg > 18) THEN stress_score += 20"
        // 예: "IF (movement_count == 0) AND (heart_rate_min < 50) THEN sleep_status = 'DEEP_SLEEP'"

        db.collection("users").document(TEST_USER_ID)
            .collection("raw_periodic") // 'biometrics' -> 'raw_periodic'로 이름 변경 (전처리 전임을 명시)
            .add(periodicData) // 데이터를 덮어쓰지 않고 '누적' (add)
            .addOnSuccessListener {
                Log.d(TAG, "Dummy Periodic Raw Data added successfully!")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding periodic data", e)
            }
    }

    /**
     * [이벤트 데이터] (웃음/한숨 분석용 '재료')
     * - Next.js 백엔드는 'raw_events'에 새 문서가 생기면,
     * ML 서버 API에 'storage_path'의 파일 분석을 요청합니다.
     * @param eventType "laughter" (웃음) 또는 "sigh" (한숨)
     */
    fun sendDummyAudioEvent(eventType: String) {
        val audioEventData = hashMapOf(
            // --- 공통 ---
            "timestamp" to System.currentTimeMillis(), // 이벤트 발생 시간

            // --- ML 서버 요청용 재료 ---
            "storage_path" to "gs://mood-manager-storage/audio/dummy_${eventType}_${System.currentTimeMillis()}.mp3", // (더미) Storage 파일 경로

            // --- 1차 특징 (Wear OS가 직접 추출) ---
            "event_type_guess" to eventType,         // (더미) Porcupine이 추측한 이벤트 타입
            "event_dbfs" to (50..85).random(),       // (더미) 소리의 평균 크기 (dBFS) - (오탐 필터링용)
            "event_duration_ms" to (500..4000).random() // (더미) 소리 지속 시간 (ms) - (오탐 필터링용)

            // TO-DO (P3): Porcupine & AudioRecord 연동
            // - [ ] 'Porcupine SDK'로 "웃음", "한숨" 키워드(소리) 감지
            // - [ ] 감지 시, 'AudioRecord'로 3~5초간 녹음
            // - [ ] 녹음 파일을 Firebase Storage에 업로드하고 'storage_path' 획득
            // - [ ] 녹음 중 'AudioRecord'에서 'event_dbfs', 'event_duration_ms' 추출
        )

        // [설명]
        // Next.js가 이 데이터를 ML 서버로 보냅니다.
        // ML 서버는 "IF (event_dbfs < 60) OR (event_duration_ms < 1000) THEN result = 'noise'"
        // 처럼 1차 필터링 후, 'storage_path'의 파일을 분석하여 "웃음 점수(95)"를 반환합니다.

        db.collection("users").document(TEST_USER_ID)
            .collection("raw_events") // 'audio_events' -> 'raw_events'로 이름 변경
            .add(audioEventData) // 데이터를 '누적' (add)
            .addOnSuccessListener {
                Log.d(TAG, "Dummy Audio Event ($eventType) added successfully!")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding audio event", e)
            }
    }
}