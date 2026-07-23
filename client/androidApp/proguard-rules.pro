-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.flibusta.reader.**$$serializer { *; }
-keepclassmembers class com.flibusta.reader.** {
    *** Companion;
}
-keepclasseswithmembers class com.flibusta.reader.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Voyager
-keep class cafe.adriel.voyager.** { *; }

# Compose
-dontwarn androidx.compose.**

# SLF4J
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }
