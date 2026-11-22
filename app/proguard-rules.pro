# Shizuku はシェルサービスをクラス名で別プロセスに起動する。
-keep class io.github.cyb3rl00m.prune.core.shell.ShellService { *; }
-keep class io.github.cyb3rl00m.prune.IShellService { *; }
-keep class io.github.cyb3rl00m.prune.IShellService$* { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class io.github.cyb3rl00m.prune.**$$serializer { *; }
-keepclassmembers class io.github.cyb3rl00m.prune.** { *** Companion; }
-keepclasseswithmembers class io.github.cyb3rl00m.prune.** { kotlinx.serialization.KSerializer serializer(...); }
