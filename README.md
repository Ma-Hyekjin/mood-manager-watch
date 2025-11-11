# 무드매니저 (Mood Manager) - Wear OS

'무드매니저' AI 웰니스 플랫폼의 1계층(Data Collection)을 담당하는 Wear OS 네이티브 앱.
 
`Health Connect API`, `AudioRecord` 등을 통해 사용자의 생체 신호(스트레스, 수면 패턴 등)와 음성 데이터를 수집 후 **Firebase Firestore**로 전송.

---

## 🛠️ 핵심 기술 스택 (Tech Stack)

* **Language**: `Kotlin`
* **Platform**: `Android / Wear OS SDK`
* **Key APIs/SDKs**: `Firebase SDK (Kotlin)`, `Health Connect API`
* **Background**: `ForegroundService`
* *(예정)*: `AudioRecord`, `Porcupine SDK`

---

## 🌟 현재 상태 (Current Status)

### P1: 기본 파이프라인 구축 완료 (2025-11-11)
 
`[Wear OS]` -> `[Firebase]` -> `[Web App]`으로 이어지는 기본 데이터 파이프라인의 정상 작동을 확인.

Wear OS 앱의 버튼을 클릭하면, 더미(dummy) 생체 신호가 Firestore에 실시간으로 전송(`set`)되며, 이는 Next.js 웹 앱에서 성공적으로 수신(`onSnapshot`)되는 것을 확인.

* **P1 파이프라인 기여자**: 마혁진, 안준성

---

## ✅ 앞으로 해야 할 일 (Next Steps)

P1의 기본 파이프라인이 완성됨에 따라, P2/P3의 핵심 기능 개발을 시작. (우선순위 순으로 정렬)

### P1: 앱 안정화 (Stabilization)
- [ ] **필수 권한 요청 로직**: `Health Connect` 및 `마이크` 사용을 위한 런타임 권한 요청 팝업 로직을 구현.
- [ ] **`ForegroundService` 적용**: 현재는 `MainActivity`가 켜져 있을 때만 작동. 앱이 백그라운드에 있거나 꺼져있을 때도 데이터를 수집할 수 있도록 `ForegroundService`로 로직 이전 필요.

### P2: 핵심 데이터 연동 (Core Data Integration)
- [ ] **`Health Connect` 연동**: `(60..100).random()` 같은 더미 데이터 대신, `Health Connect API`를 통해 실제 **스트레스 지수** 및 **수면 패턴** 데이터를 특정 주기 가져오도록 로직 교체 필요.
- [ ] **`Porcupine SDK` 연동 (Wake Word)**: 한숨, 웃음 같은 웨이크 워드를 감지하고, 감지 이벤트를 Firestore로 전송하는 기능을 구현.

### P3: 기능 고도화 (Enhancement)
- [ ] **음성 데이터 수집**: 웨이크 워드 감지 후, `AudioRecord` API를 통해 사용자의 음성 데이터를 수집하고 전송.

---

## 🚀 시작하기 (Getting Started)

1.  본 프로젝트를 Android Studio (권장 버전: Otter | 2025.2.1)에서 연다.
2.  Firebase Console에서 발급받은 `google-service.json` 파일을 `app/` 폴더 내에 위치시킨다.
3.  Gradle 동기화 (Sync) 후, Wear OS 에뮬레이터 또는 실제 기기에서 앱을 실행한다.