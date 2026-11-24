# Parental AI Shield

Machine-learning-based Android parental control application (*Edge AI*) designed to protect children in digital environments.

## Edge AI Architecture (Python → TensorFlow Lite `.tflite` → Android Studio)

The system runs **on-device inference (Edge AI)** directly on the Android device, ensuring sensitive screen captures and text are never sent to external servers:

1. **Training & Conversion (Python):** AI models were trained in **Python** and exported to **TensorFlow Lite (`.tflite`)** for efficient mobile execution.
2. **Local Inference in Android Studio:** Both models are bundled in `app/src/main/assets/` (with `noCompress.add("tflite")` in `app/build.gradle.kts`) and executed locally via the TensorFlow Lite interpreter:
   - `modelo_riesgo_infantil_v3.tflite` (`AnalizadorTextoRiesgoso.kt`): NLP model for risky-text and harmful language detection (**2 threads**, **60s cooldown**).
   - `modelo_vision_nsfw_PRO.tflite` (`AnalizadorVisualNSFW.kt`): Computer-vision model (`224x224x3` input tensor) for NSFW visual content detection (**4 threads**, **60s cooldown**).

## Core Features

- **Real-Time Screen Monitoring:** Background `AccessibilityService` (`ServicioMonitoreoNSFW.kt`) powered by **Kotlin Coroutines** and **Executors** to analyze on-screen text and visual content in real time.
- **Tamper-Resistant Device Protection:** `DeviceAdminReceiver` integration (`AdminReceiver.kt`) to safeguard parental control settings against unauthorized removal.
- **Local Alert Logging:** Persistent SQLite event log via `BitacoraDbHelper.kt` (`BitacoraActivity.kt`, `BitacoraAdapter.kt`, `RegistroBitacora.kt`) and visual asset management (`GestorImagenes.kt`).
- **Modern UI & Parent Access:** Built with **Kotlin** and **Jetpack Compose (Material 3)** with **Firebase Auth `24.0.1`** for parent/guardian authentication.

## Repository Structure

```text
Parental-AI-Shield/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   ├── modelo_riesgo_infantil_v3.tflite
│       │   └── modelo_vision_nsfw_PRO.tflite
│       └── java/
│           ├── ServicioMonitoreoNSFW.kt
│           ├── BitacoraDbHelper.kt
│           ├── BitacoraAdapter.kt
│           ├── GestorImagenes.kt
│           └── com/example/aplicaciondetesis/
│               ├── MainActivity.kt
│               ├── AdminReceiver.kt
│               ├── AnalizadorTextoRiesgoso.kt
│               ├── AnalizadorVisualNSFW.kt
│               ├── BitacoraActivity.kt
│               ├── RegistroBitacora.kt
│               └── ui/theme/
├── gradle/
└── build.gradle.kts
```

## Tech Stack & Build Configuration

- **Language & Build:** Kotlin `2.0.21`, Android Gradle Plugin (AGP) `9.0.1`, `compileSdk` / `targetSdk 36`, `minSdk 24`, `noCompress.add("tflite")`
- **UI & Android APIs:** Jetpack Compose (Material 3), `AccessibilityService`, `DeviceAdminReceiver`, Kotlin Coroutines & Executors
- **Edge AI / ML:** Python (training), TensorFlow Lite (`.tflite`), on-device inference
- **Persistence & Auth:** SQLite (`BitacoraDbHelper`), Room `2.6.1`, Firebase Auth `24.0.1`

## Research Thesis

This project is part of the thesis research documented at [Croximity/Tesis](https://github.com/Croximity/Tesis), covering dataset preparation, model training, and evaluation (**96.88%** training accuracy).

## Author

Juan José Chávez Lazo ([@Croximity](https://github.com/Croximity))
