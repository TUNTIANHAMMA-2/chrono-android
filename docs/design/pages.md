# 「有期」(CardVault) 页面清单与跳转说明

本文档对应《开发文档、清单与任务计划》（`docs/dev-plan.md`）任务 T0.10，列出界面层实现的全部页面、对应函数、参数设计、假数据预览及页面跳转流。

---

## 一、页面清单

所有界面函数均位于 `com.personal.cardvault.ui.screens` 及其子包下，严格遵循纯声明式 UI 规范：自身不持有任何私有数据或数据库状态，所有呈现内容和交互动作均通过参数传入，每个界面均附带假数据预览（`@Preview`）并集成至调试源码的预览中心。

| 序号 | 页面名称 | 界面函数 | 所在源码路径 | 对应文档章节与任务 | 预览覆盖状态 |
|---|---|---|---|---|---|
| 1 | **事项首页** | `ExpiryHomeScreen` | `ui/screens/expiry/ExpiryHomeScreen.kt` | 6.2、T2.5 | 正常四分区列表、空数据引导、通知权限缺失提示 |
| 2 | **事项新建/编辑页** | `ExpiryEditScreen` | `ui/screens/expiry/ExpiryEditScreen.kt` | 6.3、T2.4 | 新建事项、修改事项、开始晚于到期日轻提示 |
| 3 | **事项删除确认页** | `ExpiryDeleteConfirmScreen` | `ui/screens/expiry/ExpiryDeleteConfirmScreen.kt` | 7.4、6.5 | 条目正常存在确认、条目已被删除/不存在 |
| 4 | **保险箱主页** | `VaultScreen` | `ui/screens/vault/VaultScreen.kt` | 8.4、8.6、8.8、T4.5、T4.9 | 还没建库、未解锁、已解锁有卡、已解锁无卡、保险箱打不开 |
| 5 | **卡片详情页** | `CardDetailScreen` | `ui/screens/vault/CardDetailScreen.kt` | 8.6、T4.5、T4.7 | 敏感信息默认遮挡、明文展开、有效期格式化与独立复制 |
| 6 | **卡片录入/编辑页** | `CardEditScreen` | `ui/screens/vault/CardEditScreen.kt` | 8.7、5.3、T4.6 | 新建卡片、修改现有卡片、数字密码键盘无纠错 |
| 7 | **备份导出页** | `BackupExportScreen` | `ui/screens/backup/BackupExportScreen.kt` | 9.3、9.6、T5.3 | 正常导出流程、保险箱打不开分支提示、网盘与口令风险提示 |
| 8 | **从备份恢复页** | `BackupRestoreScreen` | `ui/screens/backup/BackupRestoreScreen.kt` | 9.4、9.6、T5.4 | 选择文件与口令输入、数据覆盖二次确认、统一解密失败报错 |
| 9 | **设置页** | `SettingsScreen` | `ui/screens/settings/SettingsScreen.kt` | 10.3、T3.7、T4.10、T6.1、T6.3 | 完整 9 项设置（开关、权限、休眠说明、备份入口、保护级别、版本号） |
| 10 | **调试预览中心** | `DebugPreviewHubScreen` | `app/src/debug/.../ui/preview/PreviewScreen.kt` | T0.10、阶段 1 | 全页面假数据手机交互预览、动态取色/黑白灰实时切换 |

---

## 二、页面跳转与交互说明

### 2.1 主导航结构（底部导航栏）
应用通过 `CardVaultBottomBar` 提供三大一级入口（文档 10.3 实现建议）：
1. **事项 (`NavTab.EXPIRY`)**：默认首页，展示到期事项的四级分区（已到期、即将到期、进行中、已暂停）。
2. **保险箱 (`NavTab.VAULT`)**：进入银行卡凭据管理。未解锁时仅呈现解锁入口，验证通过后展示卡片列表。
3. **设置 (`NavTab.SETTINGS`)**：应用参数配置、权限状态、休眠引导、备份导出与恢复、系统密钥级别查看。

### 2.2 事项管理流程
```
[事项首页 (ExpiryHomeScreen)]
   │
   ├── 点击 FAB (+) ──────────> [新建事项 (ExpiryEditScreen)] ─── 保存 ──> 返回首页
   │
   ├── 点击条目 ──────────────> [编辑事项 (ExpiryEditScreen)] ─── 保存 ──> 返回首页
   │                                     │
   │                                     └── 点击删除 ──> [确认对话框 (ConfirmDialog)]
   │
   ├── 条目一键续期 (有周期) ──> 后台推进到期日，刷新列表
   │
   ├── 条目续期 (无周期) ─────> 打开编辑事项页面，用户自行选定日期
   │
   ├── 条目暂停 / 恢复 ───────> 直接更新 isPaused 状态并刷新
   │
   └── 条目删除 ──────────────> 弹出 ConfirmDialog 二次确认后删除
```
**通知点击流程（文档 7.4）：**
- 点通知本体 / 点无周期事项“续期”：直接启动 Activity 打开 `ExpiryEditScreen`。
- 点通知“删除”：直接启动 Activity 打开 `ExpiryDeleteConfirmScreen`。若条目已不存在，呈现“这个事项已经不在了”并关闭页面；若存在，展示当前名称与到期日，确认后删除。
- 点通知“续期”(有周期) / 点通知“暂停”：走后台广播接收器条件更新，不启动任何 Activity。

### 2.3 卡片保险箱流程
```
[保险箱主页 (VaultScreen)]
   │
   ├── [状态: 还没建库] ──── 用户点击“创建保险箱” ──> 系统生物识别/锁屏密码认证 ──> 进入已解锁状态
   │
   ├── [状态: 未解锁] ────── 用户点击“解锁保险箱” ──> 系统生物识别/锁屏密码认证 ──> 进入已解锁状态
   │
   ├── [状态: 已解锁]
   │      │
   │      ├── 点击卡片 ──────> [卡片详情页 (CardDetailScreen)]
   │      │                          │
   │      │                          ├── 点击“编辑” ──> [卡片修改页 (CardEditScreen)]
   │      │                          └── 点击“删除” ──> ConfirmDialog 二次确认
   │      │
   │      ├── 点击添加 (+) ───> [卡片录入页 (CardEditScreen)] ─── 保存 ──> 返回保险箱
   │      │
   │      └── 点击“锁定” ─────> 内存清零，回到未解锁状态
   │
   └── [状态: 保险箱打不开] (文档 8.8)
          ├── 出口 A: 点击“从备份恢复” ──> 进入 BackupRestoreScreen 恢复流程
          └── 出口 B: 点击“重置保险箱” ──> 弹出危险二次确认框，清空保险箱元数据并删除 Keystore 别名
```
**安全交互规范（文档 8.6、8.7）：**
- `CardDetailScreen` 中的卡号和 CVV 默认遮挡显示，点击眼睛图标切换显示明文。
- 卡号、有效期、CVV 分别设有复制按钮；有效期格式为 `MM/YY`，月或年未填时复制按钮禁用。
- 详情页下方明确标示：“复制出去之后，数据就已经离开保险箱了；系统大约一小时后会自动清空剪贴板。”
- 卡片录入页与详情页进入时设置系统 `FLAG_SECURE`，离开时清除。

### 2.4 备份与恢复流程
```
[设置页 / 保险箱打不开状态]
   │
   ├── 点击“导出备份” ──> [备份导出页 (BackupExportScreen)]
   │                          │
   │                          ├── 1. SAF 选择文件保存位置 (CreateDocument)
   │                          ├── 2. 输入两次备份口令并校验一致
   │                          ├── (分支: 若保险箱失效，提示仅备份事项并确认)
   │                          └── 3. 设备认证 (已建库时) ──> 导出完成
   │
   └── 点击“从备份恢复” ──> [从备份恢复页 (BackupRestoreScreen)]
                              │
                              ├── 1. SAF 选择备份文件 (.cvb)
                              ├── 2. 输入口令并尝试解密
                              │        └── 失败: 统一报错“口令不对或文件已损坏”
                              │
                              └── 3. 成功: 展示“X 个事项、Y 张卡”，用户确认整体覆盖 ──> 事务写入完成
```

---

## 三、外观与主题设计准则

1. **Material 3 规范与系统跟随**：
   - 应用主题完全基于 Material 3 标准，浅色与深色模式自适应跟随系统。
2. **莫奈动态取色开关**：
   - 当设置中“动态取色”开启时，主题配色取自系统壁纸（`dynamicLightColorScheme` / `dynamicDarkColorScheme`）。
   - 当“动态取色”关闭时，全应用仅使用黑、白及中性灰（`MonochromeDarkColorScheme` / `MonochromeLightColorScheme`），不含任何彩色元素。深色模式下背景为纯黑（`0xFF000000`）。
3. **中性灰错误色**：
   - 动态取色关闭时，错误色与警示色一律降级为中性灰，危险操作（删除、重置、覆盖）完全通过醒目的文本提示和二次确认对话框实现防御，避免出现 AI 生成式的杂色或突兀的饱和度。
