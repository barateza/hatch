# JSch uses reflection and optional BouncyCastle providers; keep it intact.
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
-dontwarn org.conscrypt.**

# Keep enum names used in persisted settings and diagnostics.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep the public engine surface used by the app module.
-keep class pw.barateza.hatch.model.** { *; }
