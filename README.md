<p align="center">
  <img src="docs/brand/chrono_nav.svg" alt="CHRONO" width="320" />
</p>

<h3 align="center">CHRONO — 把「到期提醒」和「加密卡包」安心留在本地的 Android 随身助手</h3>

<p align="center">
  <b>Android 13+ · Jetpack Compose · OKLab Material You · StrongBox / TEE 硬件加密 · Argon2id 离线备份</b>
</p>

---

## 为什么做 CHRONO？

证件快过期、会员自动续费、域名/证书到期总是后知后觉；银行卡号、有效期、CVV 不敢随便记在云端备忘录，又经常在结账、填表时临时找不到。

**CHRONO** 想做的是一件很纯粹的事：**给你一个好看、顺手、完全不依赖云端的本地空间**——

- **不注册、不联网、不打扰**：没有账号体系，没有第三方统计 SDK，也没有后台上传。你的到期事项和卡片信息只存在你自己的手机沙盒里。
- **硬件级护城河**：卡包主密钥由 Android 硬件 Keystore（优先 **StrongBox** 安全芯片，自动回退 **TEE**）生成与保护，每次查看敏感信息都需要系统生物识别或锁屏凭据；复制卡号或 CVV 时会自动标记敏感内容并在超时后自动清理剪贴板。
- **把卡包做出实体手感**：120Hz 陀螺仪视差反光、360° 跟手阻尼翻面、10 层 3D 斜切卡身厚度、银联/万事达/Visa 激光全息防伪标，再配合 OKLab 莫奈配色引擎，让每次打开卡包都像拿起一张真实的实体卡。

---

## 快速上手 & 在线卡面服务

### 1. 安装或构建
- 前往 Releases 下载最新安装包，或参考下方「构建与运行」自行编译安装。
- 最低支持 **Android 13（API 33）**。

### 2. 配置公共在线卡面服务（可选，推荐）
不想一张张手动找卡面图片？我们提供了一个**开箱即用的公共 CardFace 卡面聚合服务**，所有用户都可以直接免费使用：

```text
https://cardface.toadtools.online
```

**配置方法：**
1. 打开 CHRONO，进入底部的 **`Config（设置）`** 页面；
2. 找到 **「CardFace 在线卡面服务」**，填入上面的地址 `https://cardface.toadtools.online` 并保存；
3. 在新增或编辑卡片时点击 **「匹配卡面」**，即可直接搜索并套用对应银行的真实卡面。

> **隐私说明：** 在线卡面服务**仅接收发卡行、卡组织、卡种等公开标签**用于检索卡面图片，**绝不会上传卡号、尾号、有效期、CVV 或任何个人备注**；即使不配置该地址，App 内置的离线 BIN 号段识别与本地相册选图依然可以完整使用。如果你有自己的服务器，也可以单独部署轻量化后端 `cardface-server`。

---

## 核心功能

### 1. `Expiry` — 不再错过任何重要到期日
- **一眼看清时间进度**：按分钟精度实时计算已过时长占比（`已过 XX%`），并根据紧急程度自动切换语义色彩（进行中 / 即将到期 / 已逾期 / 已暂停）。
- **清晰分区与筛选**：自动归类为「已到期」「即将到期」「进行中」「已暂停」四大分区，支持按分类胶囊一键过滤。
- **顺手的小操作**：支持按月/按年一键顺延续期（遵循真实日历月末规则）、随时暂停或恢复提醒，并支持在系统通知与锁屏界面自动隐藏敏感事项名称。

### 2. `Wallet` — 硬件加密卡包与 3D 拟真交互
- **两种视图随心切换**：
  - **叠放卡包（Wallet Stack）**：像真实卡包一样层层叠放，支持长按拖拽排序与顶卡陀螺仪反光；未解锁时仅展示脱敏封面（发卡行、卡名、尾号后四位），拿出来就能认出哪张是哪张。
  - **轮播工作台（Carousel）**：点击任意卡片直达横向大卡视图，就地查看或一键复制卡号、有效期、CVV；未解锁时点击眼睛或复制按钮会直接唤起生物识别，通过后自动完成查看或复制。
- **3D 拟真卡片物理引擎**：
  - **$\pm 25^\circ$ 手势意图分类**：水平 $\pm 25^\circ$ 内滑动顺滑切换卡片；大于 $25^\circ$ 斜向或纵向拖拽即可在卡面任意位置触发 360° 无界翻面。
  - **真实厚度与自动卡背**：`5%` 标准圆角配合 `3.0dp` 10 层 3D 斜切厚度；翻到背面时会自动从正面卡面提取 OKLab 莫奈配色，生成磁条、签名栏、CVV 区以及银白微虹彩激光全息防伪徽标。
- **离线 BIN 识别**：
  - 内置离线压缩 BIN 号段库（`assets/bins/`），输入卡号前 6 位即可离线识别发卡行、卡组织（UnionPay / Visa / Mastercard / AmEx / JCB）与卡种；
  - 支持从本地相册导入自定义卡面或 AI 生成卡面，也支持配合 `cardface-server` 在线匹配。

### 3. `Config` — 莫奈主题、隐私防护与跨机备份
- **OKLab 莫奈主题引擎**：支持 `跟随系统 / 浅色 / 深色` 与 **AMOLED 纯黑模式**，内置 11 款微缩主题预览卡（`跟随壁纸`、`暖纸极简`、`午夜暮蓝`、`青苹果`、`薰衣草`、`草莓奶昔`、`青绿松石`、`章鱼紫金`、`潮汐蓝`、`琥珀暖橘`、`自定义`）及色相/饱和度实时取色器。
- **防截屏与最近任务遮罩（`FLAG_SECURE`）**：默认全局开启防截屏及多任务切换模糊遮罩，可在 `Config` 中随时开关。
- **Argon2id + AES-256-GCM 跨机加密备份**：通过系统 SAF 文件选择器导出/恢复 `.cvbackup` 备份文件，使用你的自定义口令经 Argon2id 派生密钥加密，换新手机时脱离原硬件 Keystore 也能安全迁移。

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
2. **网络权限边界**：`android.permission.INTERNET` 仅在用户主动配置了 CardFace 服务地址并在卡片编辑页点击「匹配卡面」时按需发起请求；日常启动、事项提醒与卡包解锁完全不产生任何网络流量。
3. **备份口令提示**：导出的 `.cvbackup` 备份文件采用高强度 Argon2id + AES-256-GCM 加密，**一旦遗忘备份口令将无法通过任何技术手段找回**，请务必妥善保管。
