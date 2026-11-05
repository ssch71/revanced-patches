-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# DexKit는 JNI에서 클래스/필드 이름으로 접근한다.
-keep class org.luckypray.dexkit.** { *; }
-dontwarn org.luckypray.dexkit.**

# libxposed-service가 참조하는 androidx 어노테이션 (런타임에는 불필요)
-dontwarn androidx.annotation.**
