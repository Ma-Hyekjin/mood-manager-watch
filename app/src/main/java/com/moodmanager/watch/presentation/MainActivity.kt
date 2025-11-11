// app/src/main/java/com/moodmanager/watch/presentation/theme/MainActivity.kt
package com.moodmanager.watch.presentation.theme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.moodmanager.watch.presentation.FirebaseViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: FirebaseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoodManagerWatchTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    timeText = { TimeText(modifier = Modifier.scrollAway(ScalingLazyListState())) }
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
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Button(
                            onClick = {
                                // ◀◀ 4. sendDummyData도 인식됩니다.
                                viewModel.sendDummyData()
                            }
                        ) {
                            Text("Send Dummy Data")
                        }
                    }
                }
            }
        }
    }
}