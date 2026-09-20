# Sesame-M ProGuard Rules（修正版）

# ============================================================
# 1. Xposed / libxposed
# ============================================================
-keep class io.github.libxposed.** { *; }
-dontwarn io.github.libxposed.**
-keep class io.github.aw1y2z.sesame.hook.ApplicationHook { *; }
-keepclassmembers class io.github.aw1y2z.sesame.hook.ApplicationHook {
    public <init>(...);
}
-keep class * implements io.github.libxposed.api.XposedModule { *; }

# ============================================================
# 2. Model 系统
# ============================================================
-keep class io.github.aw1y2z.sesame.data.Model { *; }
-keep class io.github.aw1y2z.sesame.data.ModelType { *; }
-keep class io.github.aw1y2z.sesame.data.ModelGroup { *; }
-keep class io.github.aw1y2z.sesame.data.ModelFields { *; }
-keep class io.github.aw1y2z.sesame.data.ModelConfig { *; }
-keep class io.github.aw1y2z.sesame.data.ModelField { *; }
-keep class io.github.aw1y2z.sesame.data.modelFieldExt.** { *; }
-keepclassmembers class io.github.aw1y2z.sesame.data.Model {
    public <init>(...);
}
-keepclassmembers class io.github.aw1y2z.sesame.data.modelFieldExt.** {
    public <init>(...);
}
-keep class io.github.aw1y2z.sesame.data.**$* { *; }

# ============================================================
# 3. data.task 包（反射实例化）
# ============================================================
-keep class io.github.aw1y2z.sesame.data.task.** { *; }

# ============================================================
# 4. 配置类（Jackson 序列化）
# ============================================================
-keep class io.github.aw1y2z.sesame.data.ConfigV2 { *; }
-keep class io.github.aw1y2z.sesame.data.ConfigPreload { *; }
-keep class io.github.aw1y2z.sesame.data.AppConfig { *; }
-keep class io.github.aw1y2z.sesame.data.TokenConfig { *; }
-keepclassmembers class io.github.aw1y2z.sesame.data.ConfigV2 {
    public <init>(...);
}
-keepclassmembers class io.github.aw1y2z.sesame.data.AppConfig {
    public <init>(...);
}
-keepclassmembers class io.github.aw1y2z.sesame.data.TokenConfig {
    public <init>(...);
}

# ============================================================
# 5. 状态与统计
# ============================================================
-keep class io.github.aw1y2z.sesame.util.Status { *; }
-keep class io.github.aw1y2z.sesame.util.Statistics { *; }
-keepclassmembers class io.github.aw1y2z.sesame.util.Status {
    public static ** INSTANCE;
}
-keepclassmembers class io.github.aw1y2z.sesame.util.Statistics {
    public static ** INSTANCE;
}

# ============================================================
# 6. RPC
# ============================================================
-keep class io.github.aw1y2z.sesame.hook.RpcRequest { *; }
-keep class io.github.aw1y2z.sesame.hook.ServerCommon { *; }
-keep class io.github.aw1y2z.sesame.hook.BaseHandler { *; }
-keep class io.github.aw1y2z.sesame.rpc.bridge.* { *; }
-keepclassmembers class io.github.aw1y2z.sesame.hook.RpcRequest {
    public <init>(...);
}

# ============================================================
# 7. idMap
# ============================================================
-keep class io.github.aw1y2z.sesame.util.idMap.** { *; }

# ============================================================
# 8. Entity
# ============================================================
-keep class io.github.aw1y2z.sesame.entity.** { *; }

# ============================================================
# 9. 扩展模块
# ============================================================
-keep class io.github.aw1y2z.sesame.model.extensions.** { *; }
-keepclassmembers class io.github.aw1y2z.sesame.model.extensions.ExtensionsHandle {
    public static java.lang.Object handleAlphaRequest(java.lang.String, java.lang.String, java.lang.Object);
}

# ============================================================
# 10. Hook 包（保持原有整包保留，避免反射调用崩溃）
# ============================================================
-keep class io.github.aw1y2z.sesame.hook.** { *; }

# ============================================================
# 11. 工具类
# ============================================================
-keep class io.github.aw1y2z.sesame.util.XHelpers { *; }
-keep class io.github.aw1y2z.sesame.util.compat.** { *; }
-keep class io.github.aw1y2z.sesame.util.ClassUtil { *; }
-keep class io.github.aw1y2z.sesame.util.FileUtil { *; }
-keep class io.github.aw1y2z.sesame.util.Log { *; }
-keep class io.github.aw1y2z.sesame.util.JsonUtil { *; }
-keep class io.github.aw1y2z.sesame.util.TimeUtil { *; }
-keep class io.github.aw1y2z.sesame.util.NotificationUtil { *; }
-keep class io.github.aw1y2z.sesame.util.PermissionUtil { *; }
-keep class io.github.aw1y2z.sesame.util.StringUtil { *; }
-keep class io.github.aw1y2z.sesame.util.ThreadUtil { *; }
-keep class io.github.aw1y2z.sesame.util.ToastUtil { *; }
-keep class io.github.aw1y2z.sesame.util.TypeUtil { *; }

# ============================================================
# 12. Model 实现类 / UI
# ============================================================
-keep class io.github.aw1y2z.sesame.model.** { *; }
-keep class io.github.aw1y2z.sesame.ui.** { *; }
-keep class io.github.aw1y2z.sesame.SesameApplication { *; }

# ============================================================
# 13. Lombok 生成的 getter/setter 保留（R8 可能误删）
#     原规则写作 `class **`（全工程任何类），等于让 R8 无法删除/重命名任何类上的
#     全部 get*/set* 方法，明显削弱压缩效果。这里收窄到真正需要它的位置：
#     · data.**   —— Lombok + Jackson 序列化（配置模型、ModelField 体系）
#     · entity.** —— Lombok DTO，需 Jackson 反序列化
#     · util.Statistics / util.Status / util.NotificationUtil / util.idMap.UserIdMap
#     其它 Lombok 类所在包（hook.** / model.** / ui.** / data.task.**）本就被整包 keep；
#     rpc.intervallimit 由第 6 条、rpc.bridge 由第 6 条覆盖。
# ============================================================
-keepclassmembers class io.github.aw1y2z.sesame.data.** {
    public * get*();
    public void set*(...);
}
-keepclassmembers class io.github.aw1y2z.sesame.entity.** {
    public * get*();
    public void set*(...);
}
-keepclassmembers class io.github.aw1y2z.sesame.util.Statistics {
    public * get*();
    public void set*(...);
}
-keepclassmembers class io.github.aw1y2z.sesame.util.Status {
    public * get*();
    public void set*(...);
}
-keepclassmembers class io.github.aw1y2z.sesame.util.NotificationUtil {
    public * get*();
    public void set*(...);
}
-keepclassmembers class io.github.aw1y2z.sesame.util.idMap.UserIdMap {
    public * get*();
    public void set*(...);
}

# ============================================================
# 14. Jackson 注解字段/方法保留（防 R8 重命名 Jackson 注解字段）
# ============================================================
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <methods>;
}
-dontwarn java.beans.**

# ============================================================
# 15. 第三方库
# ============================================================
-keep class com.fasterxml.jackson.** { *; }
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**
-keep class org.nanohttpd.** { *; }
-dontwarn org.nanohttpd.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ============================================================
# 16. AppCompat Tab 组件（如遇 TabAdapter 崩溃再启用）
# ============================================================
# -keep class androidx.appcompat.widget.ScrollingTabContainerView { *; }
# -keep class androidx.appcompat.widget.ScrollingTabContainerView$* { *; }
# -keep class androidx.appcompat.widget.AbsActionBarView { *; }
# -keep class androidx.appcompat.widget.AbsActionBarView$* { *; }
