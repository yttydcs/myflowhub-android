# GitHub Actions：自动构建 / 发布 APK（CI + Release）

本文档说明如何在 GitHub 上全自动产出 APK（包含 Hub 能力），并在打 tag 后发布到 GitHub Releases。

## 1) CI（push/PR）

- 触发：任意 push / PR（已忽略 `v*.*.*` 的 tag push）
- 产物：`app-debug.apk` + `myflowhub.aar`（作为 Actions Artifacts）

## 2) Release（tag）

### 2.1 Tag 规范

- 格式：`vMAJOR.MINOR.PATCH`（例如 `v0.1.0`）
- 版本映射：
  - `versionName = MAJOR.MINOR.PATCH`（去掉 `v` 前缀）
  - `versionCode = MAJOR*1_000_000 + MINOR*1_000 + PATCH`

### 2.2 一次性配置：GitHub Secrets（Release 必需）

在仓库 GitHub Settings → Secrets and variables → Actions → Secrets 新增：

- `ANDROID_KEYSTORE_BASE64`：keystore 文件的 base64（建议单行，无换行）
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

### 2.3 生成 keystore（示例）

以下命令仅为示例；请妥善保存 keystore 与密码（遗失将无法签名同一包名的后续版本）。

> 注意：
> - `keytool` 来自 **JDK**（部分 JRE 可能不包含）。若提示找不到 `keytool`，请安装 JDK 17+ 或使用 Android Studio 自带的 `...\\Android Studio\\jbr\\bin\\keytool.exe`。
> - 下方示例的 `\\` 是 bash 的换行续写；在 **PowerShell** 中请使用一行命令，或改用反引号 `` ` `` 续行。

```bash
keytool -genkeypair \
  -keystore myflowhub-release.jks \
  -storetype JKS \
  -storepass "<STORE_PASSWORD>" \
  -alias "<KEY_ALIAS>" \
  -keypass "<KEY_PASSWORD>" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 36500 \
  -dname "CN=MyFlowHub, OU=Self, O=Self, L=Self, S=Self, C=US"
```

PowerShell（Windows，一行命令示例）：

```powershell
keytool -genkeypair -v -keystore myflowhub-release.jks -storetype JKS -storepass "<STORE_PASSWORD>" -alias "<KEY_ALIAS>" -keypass "<KEY_PASSWORD>" -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=MyFlowHub, OU=Self, O=Self, L=Self, S=Self, C=US"
```

### 2.4 keystore 转 base64

macOS/Linux（生成单行 base64）：

```bash
base64 -w 0 myflowhub-release.jks > myflowhub-release.jks.b64
```

PowerShell（Windows）：

```powershell
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("myflowhub-release.jks"))
$b64 | Set-Content -NoNewline myflowhub-release.jks.b64
```

将 `myflowhub-release.jks.b64` 的内容作为 `ANDROID_KEYSTORE_BASE64` 保存到 GitHub Secrets。

### 2.5 发版

```bash
git tag v0.1.0
git push origin v0.1.0
```

推送完成后：
- GitHub Actions 会自动构建并创建/更新 GitHub Release。
- Release Assets 将包含：
  - `app-release.apk`（已签名，可覆盖安装升级）
  - `myflowhub.aar`（用于复用/审计）
  - `build-info.txt`（记录 Android/Server commit 等信息）

## 3) 说明：Server 依赖（对齐现状）

当前 `hubmobile/go.mod` 使用 `replace ../../MyFlowHub-Server`。

GitHub Actions 会额外 checkout `yttydcs/myflowhub-server` 到同一 workspace 的 `repo/MyFlowHub-Server`，以保证：
- CI/Release 构建时 APK 始终内置 Hub（通过 `gomobile bind` 生成 AAR）。
- Release 采用 `myflowhub-server` 的 `main` 最新提交，并写入 `build-info.txt` 以便审计与回放。

