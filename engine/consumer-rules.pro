# Prototype: keep the SSH/JSch surface reachable from reflection-light code paths.
-keep class com.jcraft.jsch.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
