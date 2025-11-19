# 무드매니저 (Mood Manager) - Wear OS

'무드매니저' 프로젝트 - Data Collection 담당 Wear OS 네이티브 앱.
 
`Health Service`, `AudioRecord` 등을 통해 사용자의 생체 신호(스트레스, 수면 패턴 등)와 음성 데이터를 수집 후 **Firebase Firestore**로 전송.

---

## 🛠️ 핵심 기술 스택 (Tech Stack)

* **Language**: `Kotlin`
* **Platform**: `Android / Wear OS SDK`
* **Key APIs/SDKs**: `Firebase SDK (Kotlin)`, `Health Service`
* **Background**: `ForegroundService`
* *(예정)*: `AudioRecord`, `Porcupine SDK`

---

# 1. Firestore Collection 구조

users/
└── testUser/
    ├── raw_periodic/ ← 1분 간격 바이탈 데이터
    └── raw_events/ ← 웃음/한숨 등 오디오 이벤트

---


---

# 2. raw_periodic (1분 간격 생체 정보)

WearOS `PeriodicDataService`가 기록.

**문서 ID:** timestamp(ms) 문자열  
**예시 경로:** `users/testUser/raw_periodic/1763532000123`

### 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| timestamp | number | Unix time(ms) |
| heart_rate_avg | number | 평균 심박 |
| heart_rate_min | number | 최소 심박 |
| heart_rate_max | number | 최대 심박 |
| hrv_sdnn | number | 20–70 랜덤(임시), 향후 RR 기반 |
| respiratory_rate_avg | number | 호흡수 |
| movement_count | number | 움직임 수 |
| is_fallback | boolean | 센서 실측 여부 플래그 |

---

# 3. raw_events (웃음/한숨 오디오 이벤트)

WearOS `AudioEventService`가 1분마다 2초 녹음 →  
유효 이벤트만 저장(조용/unknown 제외).

이벤트가 **1시간 동안 없으면 더미 데이터 자동 생성**.

**예시 경로:**  
`users/testUser/raw_events/autoDocId12345`

### 필드
| 필드 | 타입 | 설명 |
|------|------|------|
| timestamp | number | 이벤트 시간 |
| event_type_guess | string | `"laughter"` / `"sigh"` |
| event_dbfs | number | 상대 음량 (0–100) |
| event_duration_ms | number | 보통 2000ms |
| audio_base64 | string? | Base64 WAV(무음 시 null) |
| is_fallback | boolean | 현재 휴리스틱 기반 |

---

# 4. WAV(Base64) 포맷 설명

WearOS는:

- PCM 16bit
- mono
- sample rate = **8000 Hz**
- WAV 헤더 + PCM body를 합쳐 Base64로 인코딩한다.

### 디코딩 규칙 (Python)

```python
import base64
import io
import soundfile as sf

def decode_base64_wav(base64_str):
    wav_bytes = base64.b64decode(base64_str)
    return sf.read(io.BytesIO(wav_bytes))  # (audio, samplerate)
```

ML 모델은 이를 바로 numpy waveform으로 사용하면 된다.

---

# 5. ML 파이프 라인 예시

```python
audio, sr = decode_base64_wav(doc["audio_base64"])
prediction = model(audio, sr)
print(prediction)
```
---

# 6. 백엔드 전처리 규칙 (Next.js)
- raw_periodic -> 수면 판정/스트레스 지수 계산
- raw_events -> 감정 타임라인 구성

- 심박+움직임 ↓ 장시간 → 수면으로 가정 
- 아침~저녁 변화량 기반 스트레스 스코어 
- sigh 증가 → 스트레스 민감도 증가 
- laughter 증가 → 긍정 이벤트 지표

---
# 7. 정리

| 항목           | 규칙                              |
| ------------ | ------------------------------- |
| 오디오 unknown  | 저장하지 않음                         |
| 무음           | 저장하지 않음                         |
| 실제 이벤트 발생    | 저장                              |
| 1시간 이벤트 없음   | 랜덤 더미 1개                        |
| Base64.wav   | ML에서 다시 WAV로 디코딩 가능             |
| Firestore 경로 | 반드시 `users/testUser/raw_events` |

---
## 🚀 시작하기 (Getting Started)

1.  본 프로젝트를 Android Studio (권장 버전: Otter | 2025.2.1)에서 연다.
2.  Firebase Console에서 발급받은 `google-service.json` 파일을 `app/` 폴더 내에 위치시킨다.
3.  Gradle 동기화 (Sync) 후, Wear OS 에뮬레이터 또는 실제 기기에서 앱을 실행한다.