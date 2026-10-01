# ===== 应用自身代码：全保留（体积很小，零行为风险）=====
# 真正的体积大头（material-icons-extended 上万个未使用图标类、未使用的库代码）
# 位于第三方包中，仍会被 R8 正常剔除。
-keep class com.bluearchive.toolbox.** { *; }

# ===== Shizuku：Binder/AIDL 接口，禁止混淆 =====
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# ===== OkHttp / Okio =====
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# ===== kotlinx.serialization =====
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.bluearchive.toolbox.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.bluearchive.toolbox.**$$serializer { *; }
