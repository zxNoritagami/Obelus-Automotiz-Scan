# Add project specific proguard rules here.

# Hilt
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }
-keep class dagger.hilt.** { *; }
-keepclassmembers @dagger.hilt.android.AndroidEntryPoint class * {
    @javax.inject.Inject <fields>;
}

# DataStore
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { <fields>; }

# Domain Models (Keep names for debugging, but allow shrinking unused)
-keepnames class com.obelus.obelusscan.domain.model.** { *; }
-keep class com.obelus.obelusscan.domain.model.ObdPid
-keep class com.obelus.obelusscan.ui.dashboard.DashboardViewModel

# Compose
-keep class androidx.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler {
    <init>();
}

# Bluetooth / Serial
-keep class android.bluetooth.** { *; }

# General
-printmapping mapping.txt

# Eclipse Paho MQTT - Evitar que R8 elimine clases usadas por reflexión
-keep class org.eclipse.paho.** { *; }
-keep interface org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**

# iText7 PDF - keep all classes used via reflection/font loading
-keep class com.itextpdf.** { *; }
-keep interface com.itextpdf.** { *; }
-dontwarn com.itextpdf.**
-dontwarn org.bouncycastle.**

# Apache POI Excel
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn schemasMicrosoftComOfficeOffice.**
