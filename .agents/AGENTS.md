# Workspace Rules & Architecture Guide

## Rules
- **Branch Management**: Setiap commit harus selalu dilakukan di branch `main` (kecuali diperintahkan lain).
- **Formatting**: Sebelum commit atau saat mengedit file `.kt` atau `.kts`, pastikan kode mematuhi aturan standar ktlint/spotless secara manual (4 spasi indentasi, tanpa trailing whitespace, akhiri dengan single empty newline, import alfabetis & tanpa wildcard, penempatan spasi & kurung kurawal yang benar). Jika dibutuhkan, pemformatan otomatis dapat dijalankan dengan `./gradlew spotlessApply -PspotlessFiles="..." --no-daemon`.
- **Gradle Task Execution**: Jangan jalankan gradlew (never run `./gradlew` or `./gradlew.bat` commands) kecuali untuk memformat kode menggunakan `./gradlew spotlessApply`.

---

## Codebase Architecture

### 1. Project Structure
- **`src/<lang>/<extension_name>`**: Contains individual Android modules for extensions. Each directory contains:
  - `build.gradle`: Declares extension configuration (`extName`, `extClass`, `extVersionCode`, `isNsfw`) and applies `kei.plugins.extension.legacy`.
  - `src/`: Kotlin source code files.
  - `res/`: Res files, including launcher icons (`ic_launcher.png`).
- **`lib/`**: Contains library modules that extensions can depend on (e.g., `:lib:i18n`, `:lib:cryptoaes`, `:lib:lzstring`).
- **`lib-multisrc/`**: Contains multisrc templates used as a base for multiple extensions.
- **`core/`**: Core utilities module compiled into every extension.
- **`gradle/build-logic/`**: Kotlin convention plugins that manage base settings (`kei.plugins.*`).

### 2. Version Catalog & Target SDKs
- **Kotlin**: JetBrains Kotlin plugin version `2.3.21` (configured in `gradle/libs.versions.toml`).
- **Android Gradle Plugin (AGP)**: Version `9.2.1`.
- **Target & Compile SDK**: Android API Level `34` (configured in `gradle/kei.versions.toml`).
- **Java Compatibility**: Java `11`.
- **Serialization**: Kotlinx Serialization `1.7.3` (`1.8.x` is avoided due to compiler compatibility issues).

---

## Coding Conventions

### 1. JSON Parsing
Use the extension functions from package `keiyoushi.utils` (defined in `core/src/main/kotlin/keiyoushi/utils/Json.kt`) for JSON decoding/encoding:
- Decode from String: `jsonStr.parseAs<Type>()`
- Decode from okhttp Response: `response.parseAs<Type>()`
- Encode to String: `obj.toJsonString()`
- Encode to OkHttp RequestBody: `obj.toJsonRequestBody()`

### 2. Network Client & Rate Limiting
Always set up rate limiting using the `.rateLimit(...)` extension on `OkHttpClient.Builder` (defined in `core/src/main/kotlin/keiyoushi/network/RateLimit.kt`):
```kotlin
override val client = super.client.newBuilder()
    .rateLimit(permits = 4, period = 1.seconds)
    .build()
```

### 3. Extension Class Conventions
- The `extClass` inside `build.gradle` must start with a dot (e.g., `extClass = '.BacaKomik'`).
- The extension class resides under `eu.kanade.tachiyomi.extension.<lang>.<extension_name>` and inherits from `HttpSource()` or another source class.
