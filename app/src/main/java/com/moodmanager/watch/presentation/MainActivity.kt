// app/src/main/java/com/moodmanager/watch/presentation/theme/MainActivity.kt
package com.moodmanager.watch.presentation.theme

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
// import androidx.activity.viewModels // ◀ 1. ViewModel은 이제 Service가 담당하므로 주석 처리 (또는 삭제)
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
// import com.moodmanager.watch.presentation.FirebaseViewModel // ◀ 2. ViewModel은 이제 Service가 담당하므로 주석 처리 (또는 삭제)
import com.moodmanager.watch.presentation.PeriodicDataService

class MainActivity : ComponentActivity() {

    // 3. ViewModel 관련 코드 삭제 (이제 Service가 Firebase와 통신)
    // private val viewModel: FirebaseViewModel by viewModels()

    // 알림 권한 요청 런처 (Android 13 이상용)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 권한이 승인되면 서비스 시작
            startPeriodicService()
        } else {
            Log.w("MainActivity", "Notification permission denied.")
            // (선택) 여기에 "권한이 거부되어 앱이 작동하지 않습니다"라는 UI를 보여줄 수 있습니다.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- 4. 앱이 켜지면 '자동으로' 권한 확인 및 서비스 시작 ---
        checkAndRequestPermission()
        // ---------------------------------------------------

        setContent {
            MoodManagerWatchTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    timeText = { TimeText(modifier = Modifier.scrollAway(ScalingLazyListState())) }
                ) {
                    // --- 5. 말씀하신 간단한 UI로 변경 ---
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
                    // --- 여기까지 UI 변경 ---
                }
            }
        }
    }

    // 권한 확인 및 서비스 시작 로직 (변경 없음)
    private fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startPeriodicService()
        }
    }

    // 서비스 시작 함수 (변경 없음)
    private fun startPeriodicService() {
        Log.d("MainActivity", "Starting periodic service...")
        val serviceIntent = Intent(this, PeriodicDataService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}