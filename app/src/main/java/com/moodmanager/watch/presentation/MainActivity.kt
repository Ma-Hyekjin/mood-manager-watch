// app/src/main/java/com/moodmanager/watch/presentation/MainActivity.kt
package com.moodmanager.watch.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.scrollAway
import com.moodmanager.watch.presentation.theme.MoodManagerWatchTheme

/**
 * 🕒 Mood Manager – 메인 액티비티
 *
 * 역할 정리:
 *  - 앱이 실행되면:
 *      1) 알림 권한(POST_NOTIFICATIONS) 확인 및 요청
 *      2) 생체 데이터 수집용 Foreground Service (PeriodicDataService) 시작
 *      3) 마이크 권한(RECORD_AUDIO) 확인 및 요청
 *      4) 오디오 이벤트 수집용 Foreground Service (AudioEventService) 시작
 *  - 화면에는 간단히 "Mood Manager / 데이터 수집 중..." 만 표시
 *
 * 실제 데이터 수집 로직은 모두 Service 쪽(PeriodicDataService / AudioEventService)에 있고,
 * 이 액티비티는 "권한 요청 + 서비스 시작 + 간단한 UI"만 담당한다.
 */
class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    // ---------------------------------------------------
    // 🔔 알림 권한 런타임 요청 (POST_NOTIFICATIONS)
    //   - Wear OS에서도 알림 채널을 통해 FGS 알림을 제대로 보여주기 위해 사용
    //   - 거절되어도 치명적이진 않아서, 결과와 상관 없이 다음 단계로 진행
    // ---------------------------------------------------
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean ->
        Log.d(TAG, "Notification permission result received.")
        // 알림 권한 절차가 끝났으니 → 서비스 시작 단계로 진입
        startServicesAfterNotificationStep()
    }

    // ---------------------------------------------------
    // 🎙 마이크 권한 런타임 요청 (RECORD_AUDIO)
    //   - 허용되면: AudioEventService에서 실제 AudioRecord 사용 가능
    //   - 거절되면: AudioEventService가 fallback(랜덤 이벤트)만 사용하게 됨
    // ---------------------------------------------------
    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "RECORD_AUDIO permission granted.")
            startAudioEventService()
        } else {
            Log.w(TAG, "RECORD_AUDIO permission denied. AudioEventService will not start.")
            // 필요하다면 여기서 UI로 "마이크 권한이 없어 웃음/한숨 이벤트는 기록되지 않습니다" 안내 가능
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) 앱 시작 시 권한/서비스 플로우 시작
        checkAndRequestNotificationPermission()

        // 2) 워치 화면 UI 구성
        setContent {
            MoodManagerWatchTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    timeText = {
                        // 상단에 현재 시간 표시 (Wear OS 기본 구성요소)
                        TimeText(
                            modifier = Modifier.scrollAway(ScalingLazyListState())
                        )
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Mood Manager",
                            style = MaterialTheme.typography.title1,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "데이터 수집 중...",
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------
    // 1단계: 알림 권한 확인 → 필요하면 요청
    //
    //  - Android 13(TIRAMISU) 이상: POST_NOTIFICATIONS 런타임 권한 필요
    //  - 이하 버전: 권한 개념이 없으므로 바로 다음 단계로 진행
    // ---------------------------------------------------
    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                Log.d(TAG, "POST_NOTIFICATIONS already granted.")
                startServicesAfterNotificationStep()
            } else {
                Log.d(TAG, "Requesting POST_NOTIFICATIONS...")
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // Android 12 이하: 별도 알림 권한 없음 → 바로 진행
            startServicesAfterNotificationStep()
        }
    }

    // ---------------------------------------------------
    // 2단계: 알림 권한 절차 끝난 후 → 실제 서비스들 시작
    //
    //  - (1) PeriodicDataService : 1분마다 생체 데이터 수집 → raw_periodic 저장
    //  - (2) AudioEventService   : 마이크 기반 웃음/한숨 탐지 → raw_events 저장
    // ---------------------------------------------------
    private fun startServicesAfterNotificationStep() {
        // (1) 생체 데이터 주기 수집 서비스 시작
        startPeriodicService()

        // (2) 오디오 이벤트 수집 서비스 시작 (마이크 권한 체크 포함)
        checkAndRequestAudioPermission()
    }

    // ---------------------------------------------------
    // 🎙 마이크 권한 확인 & 요청
    //
    //  - Android 6.0(M) 이상: RECORD_AUDIO 런타임 권한 필요
    //  - 허용되면: AudioEventService 시작
    //  - 거절되면: AudioEventService 미시작 (필요 시 fallback 전략만 사용 가능)
    // ---------------------------------------------------
    private fun checkAndRequestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                Log.d(TAG, "RECORD_AUDIO already granted.")
                startAudioEventService()
            } else {
                Log.d(TAG, "Requesting RECORD_AUDIO permission...")
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            // 구버전은 바로 시작 (실제 Wear OS에서는 거의 의미 없지만 형식상 처리)
            startAudioEventService()
        }
    }

    // ---------------------------------------------------
    // ⏱ 생체 데이터 수집 서비스 시작 (PeriodicDataService)
    //
    //  - Foreground Service 로 실행
    //  - Health Services 기반으로 심박/HRV/호흡/움직임 등 수집
    //  - 1분마다 Firestore `raw_periodic`에 문서 추가
    // ---------------------------------------------------
    private fun startPeriodicService() {
        Log.d(TAG, "Starting PeriodicDataService...")
        val intent = Intent(this, PeriodicDataService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // ---------------------------------------------------
    // 🎤 오디오 이벤트 수집 서비스 시작 (AudioEventService)
    //
    //  - Foreground Service 로 실행
    //  - RECORD_AUDIO 권한이 있으면:
    //      • AudioRecord로 상시 마이크 수집
    //      • 간단한 규칙으로 웃음/한숨 이벤트 감지
    //      • 이벤트 구간을 WAV로 저장 후 Firestore `raw_events`에 메타데이터 기록
    //  - 권한이 없으면:
    //      • (서비스 내부 로직에 따라) 랜덤 fallback 이벤트만 전송하는 등 처리 가능
    // ---------------------------------------------------
    private fun startAudioEventService() {
        Log.d(TAG, "Starting AudioEventService...")
        val intent = Intent(this, AudioEventService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
