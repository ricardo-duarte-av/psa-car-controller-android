# ---- kotlinx.serialization (all PSACC DTOs and navigation routes are @Serializable) ----
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.**

-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class **$$serializer { *; }

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class pt.aguiarvieira.psacc.data.network.dto.** { *; }

# ---- Strip debug/verbose logging from release ----
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# ---- Tink (via androidx.security-crypto) references compile-only Error Prone annotations ----
-dontwarn com.google.errorprone.annotations.**
