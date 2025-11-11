// app/src/main/java/com/moodmanager/watch/presentation/FirebaseViewModel.kt
package com.moodmanager.watch.presentation // ◀◀ 1. 패키지 이름을 presentation으로 지정

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class FirebaseViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val TAG = "FirebaseViewModel"

    fun sendDummyData() {
        // README의 P1 로드맵: users/{userId}/moodData 구조
        val moodData = hashMapOf(
            "heartRate" to (60..100).random(), // 60~100 사이 랜덤 값
            "stressLevel" to (1..10).random(), // 1~10 사이 랜덤 값
            "timestamp" to System.currentTimeMillis()
        )

        // users 컬렉션 -> testUser 문서 -> moodData 컬렉션 -> latest 문서에 데이터 '덮어쓰기'
        db.collection("users").document("testUser")
            .collection("moodData").document("latest")
            .set(moodData) // set()은 덮어쓰기 (Next.js에서 onSnapshot으로 감지하기 좋음)
            .addOnSuccessListener {
                Log.d(TAG, "Dummy data sent successfully!")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error sending dummy data", e)
            }
    }
}