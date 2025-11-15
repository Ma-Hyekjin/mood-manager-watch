// app/src/main/java/com/moodmanager/watch/presentation/MainActivity.kt
package com.moodmanager.watch.presentation

import android.Manifest
import android.content.Intent
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
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.scrollAway
import com.moodmanager.watch.presentation.theme.MoodManagerWatchTheme

/**
    MainActivity - 워치에서 앱을 실행했을 때 가장 먼저 뜨는 화면.

        1) 알림 권한 요청
        2) 심박 등을 측정하기 위한 센서 권한 요청
        3) 사용자가 권한을 준 후 PeriodicDataService를 시작
        4) "Mood Manager \n 데이터 수집 중..." UI 표시 (불필요한 화면이지만 없으면 어색해서 추가)

 */
class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    /**
     알림 권한 요청 런처
     - Android 13 에서 알림을 보여주기 위해 필요한 런타임 권한
     - 포그라운드 서비스 알림을 사용자에게 노출하기 위해 사용
     */
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Log.w(TAG, "Notification permission denied.")
        }
        // 허용/거부 여부 상관 없이 → 다음 단계인 센서 권한 요청으로 진행
        checkAndRequestBodySensorPermission()
    }

    /**
     신체 센서 권한 요청 런처
     - Health Services를 통해 심박/활동 데이터에 접근하기 위해 필요한 런타임 권한
     - 허용되지 않으면 실제 생체 데이터 수집이 불가능
     */
    private val requestBodySensorPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "BODY_SENSORS permission granted.")
        } else {
            Log.w(TAG, "BODY_SENSORS permission denied. Using only dummy data.")
        }
        // 센서 권한 허용 여부와 관계 없이 일단 서비스는 시작 (더미 데이터라도 전송 가능)
        startPeriodicService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 앱 실행과 동시에 권한 플로우 시작:
        // 1) 알림 권한 → 2) 센서 권한 → 3) PeriodicDataService 실행
        checkAndRequestNotificationPermission()

        // 워치 UI 설정
        setContent {
            MoodManagerWatchTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    timeText = {
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

    /**
     권한 플로우 1단계: 알림 권한 확인 및 요청
     - Android 13 이상에서만 런타임 권한 요청 필요
     - 그 이하 버전에서는 바로 센서 권한 요청 단계로 넘어감
     */
    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // 알림 권한이 별도로 필요 없는 버전 → 바로 센서 권한 요청
            checkAndRequestBodySensorPermission()
        }
    }

    /**
     권한 플로우 2단계: 센서 권한 확인 및 요청

     - Health Services를 통해 심박/활동 데이터를 사용하려면 반드시 필요
     - 지금은 간단히 "요청만 하고, 결과는 콜백에서 처리"하도록 구현
     */
    private fun checkAndRequestBodySensorPermission() {
        // BODY_SENSORS는 "위험 권한"이므로 런타임 요청이 필요하다.
        requestBodySensorPermissionLauncher.launch(Manifest.permission.BODY_SENSORS)
    }

    /**
     최종: 주기적 데이터 수집 시작
     - PeriodicDataService: 포그라운드 서비스로 동작하면서, 일정 주기마다 raw_periodic 데이터를 Firestore에 기록
     */
    private fun startPeriodicService() {
        Log.d(TAG, "Starting PeriodicDataService...")
        val intent = Intent(this, PeriodicDataService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
