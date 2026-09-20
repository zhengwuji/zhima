# Sesame-M（芝麻粒-M）

[![License](https://img.shields.io/github/license/zhengwuji/zhima.svg)](LICENSE)

> 芝麻粒系列的又一个分支版本。本仓库是 [aw1y2z/Sesame-M](https://github.com/aw1y2z/Sesame-M) 的个人镜像，
> 保留完整上游历史（含更早的芝麻粒生态提交），并叠加本仓库自己的安全加固、性能优化与自动化发布流水线。

本项目可与其它同源的芝麻粒模块共存安装。

## 为了大家的资金安全与个人信息安全，强烈建议
1. 不要使用任何未开放源代码的修改版！
2. 不要使用任何未开放源代码的修改版！！
3. 不要使用任何未开放源代码的修改版！！！

## 自动构建与自动发布

每次向 `main` 推送代码都会自动触发 [Build APK](.github/workflows/build.yml)：

1. 跑单元测试（`app/src/test`，纯 JVM，不需要设备）与 lint 报告；
2. 构建 release APK（R8 混淆 + 资源压缩 + ABI 拆分）：`arm64-v8a` / `armeabi-v7a` / `universal`；
3. 用仓库 Secrets 中的密钥库签名；
4. **自动发布到 [Releases](../../releases)**，附件含 `SHA256SUMS.txt`（校验和）与 `mapping-*.txt`
   （R8 混淆映射，用于还原崩溃栈），更新内容自动摘取自上一次发布之后的提交记录。

版本号由 CI 自动推导：取最近一个 `v*` tag 并把 patch 段 +1（首个版本为 `v1.0.0`）；
也可以手动触发 workflow 并指定版本号。推送到 `MIUIX*` 等非主分支只产出 Artifact，不发版。

### 首次使用需在仓库 Secrets 配置签名（只需一次）

```bash
python tools/gen_keystore.py --print-secrets
```

把输出的 4 个值填进 **Settings → Secrets and variables → Actions**：

| Secret | 说明 |
| --- | --- |
| `SESAME_KEYSTORE_BASE64` | 密钥库文件的 base64 |
| `SESAME_STORE_PASSWORD` | 密钥库口令 |
| `SESAME_KEY_ALIAS` | 密钥别名 |
| `SESAME_KEY_PASSWORD` | 密钥口令 |

> 未配置时 CI 仍会构建，但产出未签名 APK 且不会发版（避免发出装不上的包）。
> 密钥库与 `signing.properties` 都不入库（见 `.gitignore`），请自行备份 ——
> 换了密钥库就无法覆盖安装旧版本，只能卸载重装。

## 本仓库相对上游的主要改动

### 安全
- 调试 HTTP 服务**默认关闭**：新增 `debugHttpServer` / `debugRpcEnabled` / `debugExtraRoutes` 开关；
  开启后令牌随机生成（落盘 `sesame-M/debug_server.txt`）、端口默认随机、且只监听 `127.0.0.1`；
- 移除源码里硬编码的调试令牌；`/debugHandler`（可执行任意宿主 RPC）与授权码/标记两条路由默认不注册，
  且都必须携带令牌（原先这两条路由完全不鉴权）；
- 令牌比较改为常量时间（`MessageDigest.isEqual`）；
- 移除 `MANAGE_EXTERNAL_STORAGE`（"所有文件访问"）与 `requestLegacyExternalStorage`，
  统计/日志导出改到应用专属目录 `sesame-M/export`；
- `allowBackup=false`；设置页/扩展页/日志页三个 Activity 不再导出（日志页会露出账号标识）；
- 新增 `network_security_config.xml`，对内置第三方接口强制 TLS；
- 移除第三方游戏服上报里预埋的授权码与小程序标记字段。

### 性能
- 配置/统计/黑名单落盘**去写放大**（内容未变不写盘）并改为原子写（临时文件 + rename），
  保存不再无条件触发滚动备份；
- WakeLock 加超时，避免异常路径永久持锁耗电；
- 后台任务统一走有界共享线程池（命名线程 + 异常留痕 + 背压），替换散落的裸 `new Thread`；
- 跨模块复用的等待间隔收敛为 `util/Intervals` 命名常量。

### 工程
- 单测门禁（`app/src/test`）+ lint 报告 + R8 mapping 随包发布；
- Gradle wrapper 固定 `distributionSha256Sum`；
- 版本信息改用 `providers.exec`（Gradle 9 / configuration cache 兼容）；
- 删除未被引用的旧版 `app/libs/api-82*.jar`。

## 本地构建

```bash
# 需要 JDK 17 与 Android SDK（compileSdk 37）
./gradlew assembleNormalRelease -Pversion=1.1.6
```

本地签名：把密钥库路径与口令填进 `signing.properties`（该文件不入库）后直接构建即可；
不填则产出未签名 APK，仅用于编译校验。

## 主要功能
