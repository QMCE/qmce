# QMCE Lite X - Agent 交接文档

## 项目概览

- 项目目录：`/home/rj/qmce-lite-x`
- 主模块：`app-new`
- 包名：`rj.qmce.litex`
- 原包名：`com.tencent.qqlite`
- 目标：独立轻量 QQ 客户端，通过打包 QQ SDK + 原生 so 跑通二维码登录和通信

## 当前进度：Wear Compose UI 迁移（进行中）

### 已完成

1. **从 `~/KiliKili` 复制 Wear Compose vendor 源码到 `app-new`**
   - `app-new/src/main/java/androidx/wear/compose/foundation/` — Wear Compose Foundation
   - `app-new/src/main/java/androidx/wear/compose/material3/` — Wear Compose Material3
   - `app-new/src/main/java/androidx/wear/compose/materialcore/` — Material Core
   - 每个目录的嵌套问题（`cp -r` 导致的双重嵌套）已修复

2. **复制配套资源**
   - `app-new/src/main/res/drawable/wear_m3c_*.xml` — 动画资源
   - `app-new/src/main/res/drawable/circular_vignette_*.png`, `rectangular_vignette_*.png`
   - `app-new/src/main/res/values/wear_m3c_strings.xml` — 字符串/复数资源

3. **更新依赖**
   - `gradle/libs.versions.toml`：Compose BOM 升级到 `2025.10.00`（对齐 KiliKili）
   - 新增：`emoji2-bundled`, `compose-ui-util`, `graphics-shapes`, `graphics-path`, `compose-foundation`, `compose-animation`, `compose-animation-graphics`, `compose-material-ripple`
   - `app-new/build.gradle.kts`：`compileSdk` 升到 35，`freeCompilerArgs` 加了 experimental opt-in

4. **重写 MainActivity.kt**
   - 用 Wear Compose Material3 组件（`ScreenScaffold`, `TimeText`, `TransformingLazyColumn`, `Card`, `Button`, `Text`）
   - 保留完整登录流程：`fetchCodeSigVerifyLogin` → `queryCodeResult` 轮询 → `getStWithQrSig` 换票 → `bindLoggedInAccount`
   - 登录成功后跳转 `ChatListScreen`（带占位聊天列表）

5. **修复了部分 R 引用**
   - `Strings.kt` 里的 `import rj.kilikili.R` 已改为 `import rj.qmce.lite.R`

### 未解决的编译错误

构建失败，剩余错误：

1. **`AlertDialog.kt`** — 引用 `rj.kilikili.ui.dialog.BasicFullScreenDialog` 和 `FullScreenDialogProperties`
   - 这是 KiliKili 自己写的全屏对话框实现，不是 Wear Compose 标准组件
   - 已复制 `FullscreenDialog.kt` 到 `app-new/src/main/java/rj/kilikili/ui/dialog/`，但该文件自身还有更多依赖

2. **`ScreenScaffold.kt`** — 引用 `rj.kilikili.ui.components.wear.TopBarScrollBehavior` 和 `rememberEnterAlwaysScrollBehavior`
   - 已复制 `WearTopBar.kt`，但它依赖 `rj.kilikili.data.setting.LocalData`（未复制）

3. **`ConfirmationDialog.kt` / `OpenOnPhoneDialog.kt`** — 部分 R 引用可能还有残留

### 下一步修复方向

修复编译错误的策略（按优先级）：

1. **把缺失的 KiliKili 辅助类全部补过来**：
   - `rj/kilikili/data/setting/LocalData` — WearTopBar 依赖
   - `FullscreenDialog.kt` 的完整依赖链
   - 或者：**直接删除/注释掉**有依赖问题的文件（`AlertDialog.kt`, `ConfirmationDialog.kt`, `OpenOnPhoneDialog.kt`, `ScreenScaffold.kt` 中有问题的方法），因为这些 Dialog 不是登录主链路必需的

2. **重新构建验证**

3. **验证功能**：安装到真机测试登录流程

## 关键文件

| 路径 | 说明 |
|------|------|
| `app-new/src/main/java/rj/qmce/lite/QmceApplication.kt` | Application，继承 WatchApplicationDelegate |
| `app-new/src/main/java/rj/qmce/lite/ui/MainActivity.kt` | Wear Compose UI + 登录流程 |
| `app-new/src/main/java/moye/signature/KillerApplication.java` | 签名与包信息伪装 |
| `app-new/src/main/java/rj/qmce/lite/fix/SignatureProbe.kt` | 签名探针日志 |
| `app-new/src/main/java/rj/qmce/lite/fix/KtFix.kt` | Kotlin stdlib 访问冲突修复 |
| `app-new/src/main/AndroidManifest.xml` | MSF 组件和权限 |
| `app-new/build.gradle.kts` | 构建配置（compileSdk=35, targetSdk=26） |
| `gradle/libs.versions.toml` | 依赖版本（Compose BOM 2025.10.00） |
| `app-new/libs/qq-sdk.jar` | QQ SDK 类（25M，多轮补类+ASM patch） |
| `app-new/src/main/java/androidx/wear/compose/` | Wear Compose vendor 源码 |
| `app-new/src/main/java/rj/kilikili/` | KiliKili 辅助类（FullscreenDialog, WearTopBar） |
| `app/decompiled/apktool/` | 主要 smali 参考 |
| `~/KiliKili/` | Wear Compose vendor 参考来源 |

## ASM Patch 状态（qq-sdk.jar）

- `com.tencent.mobileqq.msf.core.auth.c` — 签名数组硬返回 `KillerApplication.getOriginalSignatures()`
- `com.tencent.mobileqq.msf.service.MsfService.isSamePackage()` — 硬返回 `true`
- 验证命令：
  ```bash
  javap -classpath app-new/libs/qq-sdk.jar -c -p com.tencent.mobileqq.msf.core.auth.c
  javap -classpath app-new/libs/qq-sdk.jar -c -p com.tencent.mobileqq.msf.service.MsfService
  ```

## 签名关键常量

- 原签名 bytes MD5: `a6b745bf24a2c277527716f6f36eb68d`
- 原签名 chars MD5: `be910af39a26a4a992c6fd01a143ed19`
- appId: `537243416`
- QUA: `V1_WAT_SQ_9.0.3_0_IDC_B`

## 构建与调试

```bash
./gradlew :app-new:assembleDebug
adb install -r app-new/build/outputs/apk/debug/app-new-debug.apk
adb shell am force-stop rj.qmce.litex
adb logcat -c
adb shell am start -n rj.qmce.litex/rj.qmce.lite.ui.MainActivity
adb shell input tap 460 1430  # 获取二维码按钮
timeout 45s adb logcat --uid=10122 -v time '*:V' | grep -E 'InvalidSign|failCode|AndroidRuntime|FATAL|NoClass|QMCE|fetchCode|queryCode|getSt'
```

## 重要约束

- 主要参考 `app/decompiled/apktool/` 的 smali
- 不准用 `app/libs/source.jar`
- 修改 `qq-sdk.jar` 前必须备份
- 全局包名伪装会破坏 AndroidX Provider 和 MSF binding，只能用栈判断
- 用户允许 ASM 硬改，该硬编码就硬编码
