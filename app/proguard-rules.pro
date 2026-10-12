# Room / Kotlin 序列化在 release 混淆下的必要保留规则
-keep class dev.relayhub.data.** { *; }
-keepclassmembers class dev.relayhub.data.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }

# Room 生成的实现
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Compose
-dontwarn androidx.compose.**
