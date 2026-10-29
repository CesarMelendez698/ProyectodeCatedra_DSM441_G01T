# FinanzasUDB 📱💰

Aplicación móvil desarrollada en **Kotlin** para Android, diseñada para la gestión financiera de estudiantes universitarios (control de transacciones, presupuestos, aranceles universitarios, horarios y noticias).

---

## 🚀 Requisitos para ejecutar en Android Studio

Para que cualquier persona o miembro del equipo pueda abrir y ejecutar el proyecto sin inconvenientes:

1. **Android Studio**: Versión recomendada **Hedgehog (2023.1.1)**, **Iguana (2023.2.1)**, **Jellyfish (2023.3.1)**, **Koala (2024.1.1)** o superior.
2. **JDK**: **Java 17** (viene predeterminado y embebido en Android Studio como *jbr-17*).
3. **Android SDK**:
   - `compileSdk`: **34** (Android 14)
   - `targetSdk`: **34**
   - `minSdk`: **24** (Compatible con Android 7.0 o superior)
4. **Dispositivo/Emulador**:
   - Cualquier emulador AVD o teléfono físico con Android 7.0 (API 24) o superior.

---

## 🛠️ Pasos para abrir y ejecutar el proyecto

1. **Abrir el proyecto:**
   - Abre Android Studio.
   - Selecciona **File > Open...** (o *Open* en la pantalla de bienvenida).
   - Navega hasta la carpeta raíz del proyecto y selecciónala.

2. **Sincronización de Gradle:**
   - Android Studio detectará automáticamente el archivo `gradlew` y ejecutará la sincronización (`Sync Project with Gradle Files`).
   - El archivo `local.properties` con la ruta de tu SDK local se generará automáticamente.
   - El archivo `google-services.json` ya se encuentra incluido en la carpeta `app/`.

3. **Ejecutar la aplicación:**
   - Selecciona el módulo `app` en el selector de configuraciones superior.
   - Elige tu emulador o dispositivo físico conectado.
   - Haz clic en el botón **Run ▶** (o presiona `Shift + F10`).

---

## 📦 Tecnologías y Librerías

- **Lenguaje:** Kotlin 1.9.23
- **Arquitectura y UI:** ViewBinding, Activities, Material Components 1.12.0, RecyclerView.
- **Backend & Autenticación:** Firebase BoM 33.4.0 (Firebase Auth, Cloud Firestore, Firebase Analytics).
- **Consumo de APIs / Red:** Retrofit 2.11.0, Gson, OkHttp Logging Interceptor.
- **Concurrencia:** Kotlin Coroutines (`kotlinx-coroutines-android:1.8.1`), AndroidX Lifecycle Runtime.
- **Carga de Imágenes:** Glide 4.16.0.

