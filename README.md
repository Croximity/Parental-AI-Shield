# Parental AI Shield

Control parental basado en aprendizaje automático (*Edge AI*) para la protección infantil en el entorno digital, desarrollado para Android.

## Arquitectura y Funcionamiento (Python → TensorFlow Lite → Android)

El sistema implementa inferencia **on-device (Edge AI)** directamente en el dispositivo Android, evitando enviar capturas o textos sensibles a servidores externos y protegiendo la privacidad del menor:

1. **Entrenamiento y Conversión (Python):** Los modelos de Inteligencia Artificial fueron entrenados en **Python** y convertidos al formato **TensorFlow Lite (`.tflite`)** para su ejecución eficiente en dispositivos móviles.
2. **Inferencia Local en Android Studio:** La aplicación carga ambos modelos desde `app/src/main/assets/` mediante el intérprete de TensorFlow Lite:
   - `modelo_riesgo_infantil_v3.tflite`: modelo de análisis de texto (NLP) para la detección de lenguaje o contenido riesgoso (`AnalizadorTextoRiesgoso.kt`).
   - `modelo_vision_nsfw_PRO.tflite`: modelo de visión por computadora para la clasificación y detección de contenido visual NSFW (`AnalizadorVisualNSFW.kt`).

## Funcionalidades Principales

- **Monitoreo en Tiempo Real:** Servicio en segundo plano (`ServicioMonitoreoNSFW.kt`) basado en `AccessibilityService` para evaluar texto y contenido visual en pantalla.
- **Protección de Dispositivo:** Integración con `DeviceAdminReceiver` (`AdminReceiver.kt`) para resguardar la configuración de control parental.
- **Bitácora de Alertas Local:** Registro persistente de eventos detectados mediante SQLite (`BitacoraDbHelper.kt`, `BitacoraActivity.kt`, `BitacoraAdapter.kt`, `RegistroBitacora.kt`) y gestión de recursos visuales (`GestorImagenes.kt`).
- **Interfaz Moderna y Acceso:** Desarrollada en **Kotlin** con **Jetpack Compose (Material 3)** e integración con **Firebase Authentication** para el acceso de padres/tutores.

## Estructura del Repositorio

- `app/src/main/assets/`: modelos TFLite (`modelo_riesgo_infantil_v3.tflite`, `modelo_vision_nsfw_PRO.tflite`).
- `app/src/main/java/`:
  - `ServicioMonitoreoNSFW.kt`, `BitacoraDbHelper.kt`, `BitacoraAdapter.kt`, `GestorImagenes.kt`
  - `com/example/aplicaciondetesis/`: `MainActivity.kt`, `AdminReceiver.kt`, `AnalizadorTextoRiesgoso.kt`, `AnalizadorVisualNSFW.kt`, `BitacoraActivity.kt`, `RegistroBitacora.kt`, `ui/theme/`

## Stack Tecnológico

- **Móvil:** Kotlin, Android Studio (SDK 36 / minSdk 24), Jetpack Compose (Material 3), AccessibilityService, DeviceAdminReceiver
- **IA / ML:** Python (entrenamiento), TensorFlow Lite (`.tflite`), inferencia on-device (Edge AI)
- **Datos y Autenticación:** SQLite, Firebase Authentication

## Tesis de Investigación

Este proyecto forma parte de la investigación documentada en [Croximity/Tesis](https://github.com/Croximity/Tesis), donde se detalla el entrenamiento y la evaluación de los modelos (96.88 % de precisión de entrenamiento).

## Autor

**Juan José Chávez Lazo** ([@Croximity](https://github.com/Croximity))
