<p align="center">
  <img src="docs/brand/chrono_nav.svg" alt="CHRONO" width="320" />
</p>

<h3 align="center">CHRONO — 离线优先的周期事项提醒与硬件级加密卡包</h3>

<p align="center">
  <b>Native Android · Jetpack Compose · Material You (OKLab Monet) · StrongBox / TEE Hardware Keystore · Argon2id Encrypted Backup</b>
</p>

---

## 简介

**CHRONO** 是一款面向 Android 13+（API 33–37）的原生隐私工具应用，将**周期与到期事项追踪（`Expiry`）**与**硬件级生物识别加密卡包（`Wallet`）**融合在同一套极简、零云端依赖的本地架构中：

- **100% 本地离线存储**：无账号体系、无第三方统计 SDK、无云端同步后台。所有到期事项与加密卡片数据均严格保存在设备私有沙盒内。
- **硬件级安全边界**：卡包主密钥由 Android 硬件 Keystore（优先 **StrongBox** 安全芯片，回退 **TEE** 可信执行环境）生成与保护，解密必须通过系统级生物识别或锁屏凭据认证；剪贴板复制自动标记 `ClipDescription.EXTRA_IS_SENSITIVE` 并在超时后自动清理。
- **高刷物理交互与莫奈美学**：支持 120Hz 陀螺仪/重力视差光泽、360° 跟手阻尼翻面、10 层 3D 物理斜切卡身厚度、发卡行激光全息防伪标（银联天坛、万事达双地球、Visa 飞鸽），以及基于 OKLab/OKLCH 色彩空间的莫奈主题引擎。

---

## 核心功能

### 1. `Expiry` — 周期与到期事项管理
- **全生命周期进度条**：实时按分钟精度计算事项已过时长占比（`已过 XX%`），并按紧急程度动态切换语义色彩（进行中 / 即将到期 / 已逾期 / 已暂停）。
- **智能分区与分类筛选**：自动划分为「已到期」「即将到期」「进行中」「已暂停」四大分区，支持按分类胶囊一键过滤。
- **快捷内联操作**：支持按月/按年一键顺延续期（遵循真实日历月末规则）、暂停与恢复提醒，以及每日后台定时检查通知（支持锁屏隐藏敏感事项名称）。

### 2. `Wallet` — 硬件级加密卡包与 3D 拟真交互
- **双视图无缝切换**：
  - **叠放卡包（Wallet Stack）**：支持长按拖拽自由排序、顶卡陀螺仪动态反光；无需提前解锁即可浏览脱敏卡面封面（发卡行、卡名、尾号后四位）。
  - **轮播工作台（Carousel）**：点击任意卡片直达横向大卡轮播视图，就地查看或一键复制卡号、有效期、CVV（未解锁状态下点击眼睛或复制按钮即刻唤起生物识别并自动完成目标动作）。
- **拟真 3D 卡片物理引擎**：
  - **$\pm 25^\circ$ 手势意图分类器**：水平 $\pm 25^\circ$ 扇区内滑动顺滑切换卡片；大于 $25^\circ$ 斜向或纵向拖拽即可在卡面任意位置触发无界 360° 翻转。
  - **真实立体厚度与卡背生成**：`5%` 标准圆角配合 `3.0dp` 10 层 3D 投影后屏幕空间斜切厚度；翻至背面自动从正面卡面提取 OKLab 莫奈配色生成磁条、签名栏、CVV 区与拟真银白微虹彩激光全息防伪徽标。
- **离线 BIN 识别与卡面管理**：
  - 内置离线压缩 BIN 号段库（`assets/bins/`），输入卡号前 6 位即可离线识别发卡行、卡组织（UnionPay / Visa / Mastercard / AmEx / JCB）与卡种；
  - 支持从本地相册导入自定义或 AI 生成卡面，亦可按需配置自建的轻量化 CardFace 卡面聚合服务（仅按发卡行/卡组织标签查询公开卡面，绝不上传卡号）。

### 3. `Config` — 莫奈主题引擎、安全策略与加密备份
- **OKLab 莫奈主题面板**：
  - 支持 `跟随系统 / 浅色 / 深色` 三段切换及 **AMOLED 纯黑模式**；
  - 内置 11 款精致微缩主题预览卡（`跟随壁纸`、`暖纸极简`、`午夜暮蓝`、`青苹果`、`薰衣草`、`草莓奶昔`、`青绿松石`、`章鱼紫金`、`潮汐蓝`、`琥珀暖橘`、`自定义`）及色相/饱和度实时种子取色器。
- **防截屏与最近任务遮罩（`FLAG_SECURE`）**：默认全局开启防截屏及多任务切换模糊遮罩，可在 `Config` 中实时开关并立即生效。
- **Argon2id + AES-256-GCM 离线备份**：导出与恢复均通过系统 SAF 文件选择器完成，使用用户口令经 Argon2id 派生密钥加密，脱离原设备硬件 Keystore 亦可安全跨机迁移。

---

## 技术架构与安全设计

| 模块 | 技术选型与实现要点 |
| :--- | :--- |
| **UI 框架与动效** | Jetpack Compose + Material 3、自定义 3D `graphicsLayer` 透视旋转、预烘焙硬件平铺 `BitmapShader` 磨砂纹理、陀螺仪低通滤波与静止死区优化 |
| **本地持久化** | Room SQLite (`AppDatabase`)；到期事项表支持直接查询以保障后台提醒可靠性，卡片敏感载荷（卡号/有效期/CVV/备注）以 AES-GCM 密文块 + 独立 IV 落盘 |
| **硬件密钥层级** | Android Keystore `AES/GCM/NoPadding` 256-bit 密钥；自动探测 `StrongBox` 硬件安全模块，不支持时透明回退至 `TEE` |
| **跨机加密备份** | `argon2kt`（Argon2id 内存硬化密钥派生）+ `AES-256-GCM` 认证加密 + 事务级原子覆盖恢复 |
| **后台提醒调度** | AndroidX WorkManager 周期调度 + `NotificationCompat.VISIBILITY_PRIVATE` 锁屏脱敏公共视图 |

---

## 构建与运行

### 环境要求
- **JDK**：Java 17 或更高版本
- **Android SDK**：`compileSdk = 37`，`minSdk = 33`（Android 13+）

### 调试构建与单元测试
```bash
# 运行全部 JVM 单元测试
./gradlew testDebugUnitTest

# 构建 Debug 安装包（包名后缀 .proto，支持与正式版共存）
./gradlew assembleDebug
```
构建产物位于：`app/build/outputs/apk/debug/app-debug.apk`

### 正式版（Release）签名构建
正式版签名参数通过环境变量或外部 Gradle 属性传入，**切勿将密钥库或口令提交至代码仓库**：
```bash
export CARDVAULT_RELEASE_STORE_FILE="/path/to/release-keystore.jks"
export CARDVAULT_RELEASE_STORE_PASSWORD="your_store_password"
export CARDVAULT_RELEASE_KEY_ALIAS="your_key_alias"
export CARDVAULT_RELEASE_KEY_PASSWORD="your_key_password"

./gradlew assembleRelease
```
构建产物位于：`app/build/outputs/apk/release/app-release.apk`

---

## 仓库目录结构

```text
.
├── app/
│   ├── schemas/                        # Room 数据库 Schema 导出快照
│   └── src/
│       ├── main/
│       │   ├── assets/bins/            # 离线分片压缩 BIN 号段库 (00..99.tsv.gz)
│       │   ├── java/com/personal/cardvault/
│       │   │   ├── backup/             # Argon2id + AES-GCM 备份编解码器
│       │   │   ├── data/               # Room 数据库、DAO 与偏好设置
│       │   │   ├── expiry/             # 到期计算、续期逻辑与 WorkManager 提醒调度
│       │   │   ├── ui/
│       │   │   │   ├── components/     # 3D 卡面、全息防伪标、陀螺仪传感器、表单与滚轮组件
│       │   │   │   ├── screens/        # Expiry / Wallet / Config / Backup 四大页面模块
│       │   │   │   └── theme/          # OKLab 莫奈调色板生成器与主题定义
│       │   │   └── vault/              # Keystore 加解密、会话控制、剪贴板清理与卡面仓库
│       │   └── res/                    # CHRONO 矢量徽标、自适应桌面图标与银行/卡组织矢量资源
│       ├── test/                       # JVM 单元测试（加密、备份、手势分类、莫奈色板、续期规则等）
│       └── androidTest/                # 设备端集成测试（Room 事务恢复、SAF 备份编解码校验）
└── docs/
    └── brand/                          # CHRONO 品牌透明矢量 SVG 资源
```

---

## 隐私与安全声明

1. **零遥测承诺**：本应用不包含任何崩溃上报、行为分析或广告追踪代码。
2. **网络权限边界**：`android.permission.INTERNET` 仅在用户主动于卡片编辑页点击「匹配卡面」且配置了自定义卡面服务地址时按需发起 HTTP 请求；日常启动、事项提醒与卡包解锁完全不产生任何网络流量。
3. **备份口令提示**：导出的 `.cvbackup` 备份文件采用高强度 Argon2id + AES-256-GCM 加密，一旦遗忘备份口令将无法通过任何技术手段找回，请妥善保管。
