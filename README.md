# 安守 AnShou — 移动端智能监护与预警系统

> 一部手机，就是一道守护线。
>
> 面向独居老人与独居人群的**被动式安全守护系统**：不需要穿戴设备、不需要老人学习任何新操作，手机自动监测作息异常，一旦发生"该睡没睡、该醒没醒、长时间无活动"等危险信号，立即通过 **邮件 + 短信** 双通道向守护人发出预警。

---

## 目录

- [项目背景](#项目背景)
- [核心功能](#核心功能)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [目录结构](#目录结构)
- [快速开始](#快速开始)
- [邮件/短信通道配置](#邮件短信通道配置)
- [关键机制设计](#关键机制设计)
- [已修复的关键问题](#已修复的关键问题)
- [调试与排查](#调试与排查)
- [相关文档](#相关文档)
- [License](#license)

---

## 项目背景

随着人口老龄化加剧与独居人群规模持续扩大，"独居者在家中发生意外却无人知晓"已成为亟待解决的社会问题。市面上的解决方案普遍存在门槛：

| 现有方案 | 痛点 |
| --- | --- |
| 智能手环/手表类 | 需要老人配合充电、佩戴，续航焦虑，忘记佩戴即失效 |
| 紧急呼叫器硬件 | 额外购置成本，且**必须由当事人主动按键**——失去意识时无法触发 |
| 社区养老平台 | 依赖上门服务，覆盖密度低，响应滞后 |

**安守的思路完全相反**：不要求被守护者做任何事。它利用每个人都不离身的手机，通过传感器 + 屏幕状态 + 应用使用时长做**被动式多源融合判定**——"手机长时间没动静"本身就是最强的危险信号。

**设计原则**：

- 🧓 **零学习成本** —— 被守护者无需注册、无需操作，装好即生效
- 💰 **零额外硬件** —— 一部 Android 手机即可，无任何购置成本
- 🔒 **隐私本地化** —— 判定逻辑全部在端侧完成，敏感数据加密存储
- 🛡️ **保底可达** —— 邮件 + 短信双通道报警，不依赖第三方推送服务

---

## 核心功能

### 1. 睡眠监测（SleepMonitorService）
- **多条件融合入睡判定**：屏幕关闭时长 ≥ 阈值（默认 15 分钟）+ 步数增长 < 10 步 + 加速度计静止，连续确认 3 次才判定入睡，有效防误判
- **醒来检测**：步数 ≥ 50 步 / 亮屏 ≥ 30 秒 / 明显体动，任一触发
- **起床异常报警**：预设起床时间 + 容差（如 7:30 + 30min）后仍未醒来 → 自动发送超时睡眠警报
- **睡眠快照机制**：入睡时保存步数/使用时长快照，支持跨天增量计算，保证"今日活动量"统计准确

### 2. 签到警报（CheckinService）
- 每日定时（默认 7:55）检查当日活动状态：使用时长 < 60 分钟 **或** 步数 < 1000 步即视为异常
- **两级确认机制**：先推送带"我已安全 / 稍后提醒"按钮的确认通知，超时（默认 1 分钟）未确认才升级为真实警报，最大限度抑制误报
- 支持自动报警模式（跳过确认直接报警）
- 同日去重：当日已报警则不再重复

### 3. 守护模式（Guardian）
- 维护多对多守护关系（父母/子女/朋友等）
- IMAP 定期拉取（最近 30 天）警报邮件并同步至本地
- 守护者视角的警报历史列表

### 4. 时光胶囊（Time Capsule）
- 个人记忆加密存储（文字 + 图片 + 附件）
- 支持设置未来解锁时间

### 5. SOS 紧急求助
- 紧急情况一键发送求助邮件至预设联系人

### 6. 后台管理系统（Web）
- 登录鉴权（JWT）、仪表盘、实时警报、警报历史
- 用户管理、统计报表（ECharts 可视化）

---

## 系统架构

```
┌─────────────────────────┐        ┌──────────────────────────┐        ┌───────────────────────────┐
│      Android 客户端       │        │       Spring Boot 后端     │        │    Vue3 后台管理系统 (Web)   │
│      (Kotlin 原生)        │        │                          │        │                           │
│  ┌───────────────────┐  │  HTTP  │  ┌────────────────────┐  │  HTTP  │  ┌─────────────────────┐  │
│  │  UI 层             │  │ ─────▶ │  │ AuthController     │  │ ◀───── │  │ 登录 / 鉴权          │  │
│  │  MainActivity      │  │  JWT   │  │ AlertController    │  │  JWT   │  │ 仪表盘 / 实时警报     │  │
│  │  Home/Guardian/... │  │        │  │ UserController     │  │        │  │ 警报历史 / 用户管理   │  │
│  ├───────────────────┤  │        │  │ DashboardController│  │        │  │ 统计分析 (ECharts)   │  │
│  │  服务层（前台服务）    │  │        │  └────────────────────┘  │        │  └─────────────────────┘  │
│  │  SleepMonitorService│ │        │  Spring Security + JWT   │        │                           │
│  │  CheckinService     │ │        │  Spring Data JPA         │        └───────────────────────────┘
│  │  EmailReceiver      │ │        └───────────┬──────────────┘
│  ├───────────────────┤  │                     │ JDBC
│  │  传感器层           │  │                     ▼
│  │  步数/加速度/屏幕状态 │  │              ┌─────────┐
│  ├───────────────────┤  │              │  MySQL   │
│  │  报警通道           │  │              └─────────┘
│  │  SMTP 邮件 / 短信    │  │
│  ├───────────────────┤  │      ┌────────────────────────────┐
│  │  存储层             │  │      │   报警下行通道（独立于后端）    │
│  │  PrefsManager       │  │      │   SMTP: 警报邮件直发守护人    │
│  │  SecurePrefsManager │  │      │   SMS : 短信直发守护人       │
│  │  (AES 加密)          │  │      └────────────────────────────┘
│  └───────────────────┘  │
└─────────────────────────┘
```

**要点**：客户端的**警报通道（邮件/短信）独立于后端服务器直发守护人**——即使后端宕机，保底报警能力依然存在；后端与管理端承担的是集中监控、数据分析与运营管理能力。

---

## 技术栈

| 端 | 技术 | 版本 |
| --- | --- | --- |
| Android 客户端 | Kotlin + Android SDK | compileSdk 34 / minSdk 26 (Android 8.0+) / targetSdk 34 |
| Android 关键机制 | 前台服务、AlarmManager 精确闹钟、EncryptedSharedPreferences、传感器（计步/加速度）、UsageStatsManager | — |
| 后端 | Kotlin + Spring Boot | Kotlin 1.9.24 / Spring Boot 3.2.4 |
| 后端安全 | Spring Security + JJWT | jjwt 0.11.5 |
| 后端数据 | Spring Data JPA + MySQL | mysql-connector-j |
| 管理端 | Vue 3 + TypeScript + Vite | vue 3.4 / ts 5.4 / vite 5.2 |
| 管理端 UI/状态/图表 | Ant Design Vue + Pinia + ECharts + Vue Router | antd / pinia 2.1 / echarts 5.5 |
| 报警通道 | JavaMail (SMTP) / SMS | 465 SSL / 587 TLS |

---

## 目录结构

```
HuoZheNe/
├── app/                                  # Android 客户端（Kotlin）
│   └── src/main/java/com/livewell/
│       ├── MainActivity.kt               # 主活动（承载主要 UI）
│       ├── HomeFragment.kt / GuardianFragment.kt / SettingsFragment.kt
│       ├── service/
│       │   ├── SleepMonitorService.kt    # 睡眠监测服务（核心）
│       │   ├── CheckinService.kt         # 签到警报服务
│       │   ├── EmailReceiverService.kt   # IMAP 邮件接收/同步
│       │   └── UnifiedNotificationService.kt
│       ├── receiver/
│       │   ├── AlertReceiver.kt          # 警报触发广播
│       │   └── AlertConfirmReceiver.kt   # 警报确认（我已安全/稍后提醒）
│       ├── untils/
│       │   ├── PrefsManager.kt           # 偏好存储（含跨天快照计算）
│       │   ├── SecurePrefsManager.kt     # 加密存储（邮箱凭据）
│       │   ├── MailSender.kt             # SMTP 发送
│       │   ├── SmsSender.kt              # 短信发送
│       │   ├── UsageStatsHelper.kt       # 应用使用时长统计
│       │   ├── SystemStepManager.kt      # 计步传感器
│       │   └── AppLogger.kt / LogWriter.kt
│       ├── model/                        # AlertHistoryRecord / GuardianTarget
│       └── adapter/                      # RecyclerView 适配器
├── backend/                              # 后端（Kotlin + Spring Boot 3.2.4）
│   └── src/main/kotlin/com/
│       ├── controller/                   # Auth / Alert / User / Dashboard / ...
│       └── ...
├── Background Management System/         # 管理端（Vue3 + Vite + TS）
│   └── src/views/                        # auth / dashboard / alerts / users / statistics / system
├── md/                                   # 项目文档与素材
├── PROJECT_ARCHITECTURE.md               # 详细架构文档（模块级代码解析）
└── SLEEP_MONITOR_BUG_FIXES.md            # 睡眠监测 4 个关键 Bug 修复记录
```

---

## 快速开始

### 环境要求

- Android Studio（AGP 支持 compileSdk 34）+ JDK 17
- MySQL 8.x
- Node.js 18+（管理端构建）

### 1. Android 客户端

```bash
# 用 Android Studio 打开项目根目录，或命令行构建：
./gradlew :app:assembleDebug
```

构建产物：`app/build/outputs/apk/debug/app-debug.apk`，安装后授予计步、短信、使用情况访问等权限即可。

### 2. 后端

```bash
# 1) 创建数据库
mysql -u root -p -e "CREATE DATABASE anshou DEFAULT CHARACTER SET utf8mb4;"

# 2) 配置数据源（backend/src/main/resources/application.properties）
#    spring.datasource.url / username / password

# 3) 启动
cd backend
./gradlew bootRun
```

### 3. 后台管理系统

```bash
cd "Background Management System"
npm install
npm run dev        # 开发模式（Vite）
npm run build      # 生产构建
```

---

## 邮件/短信通道配置

应用内「功能设置」中配置：

| 配置项 | 说明 |
| --- | --- |
| 发件邮箱 | 建议 163 等支持 SMTP 的邮箱 |
| SMTP 授权码 | 在邮箱设置中生成的**授权码**（不是登录密码） |
| SMTP 主机/端口 | smtp.163.com，465（SSL）或 587（TLS） |
| 收件邮箱 | 守护人邮箱，支持多个 |
| 通知方式 | 邮件 / 短信 / 双通道 |

> 凭据通过 `EncryptedSharedPreferences` 加密存储；初始化失败时自动降级到私有 SharedPreferences，保证可用性。

---

## 关键机制设计

### 多重保活
- 睡眠监测与签到检查均以**前台服务**常驻（低优先级常驻通知）
- `AlarmManager.setExactAndAllowWhileIdle` + `RTC_WAKEUP`：设备深度睡眠时也能准时唤醒执行检查
- 服务 `onDestroy` 时将运行状态（入睡时间、屏幕关闭时间等）持久化，重启后恢复，状态不丢失

### 入睡判定（防误判核心）
```
屏幕关闭 ≥ 15min  ∧  步数增长 < 10  ∧  加速度静止  →  连续 3 次确认  →  判定入睡
```
关键细节：**夜间短暂亮屏（< 5 分钟）不重置屏幕关闭计时**，避免"瞥一眼手机"打断入睡判定窗口。

### 睡眠快照跨天计算
入睡时保存当日步数/使用时长快照（凌晨入睡的快照自动归入前一天），次日查询时用当前值减快照值得到真实增量，避免跨 0 点统计污染。

### 警报状态机
```
异常检出 → 确认通知（1 分钟超时）
            ├─ 用户点「我已安全」→ 取消警报
            ├─ 用户点「稍后提醒」→ 15 分钟后重检
            └─ 超时未确认       → 真实警报（邮件/短信）→ 记录历史 → 当日去重
```

### 邮件发送可靠性
- 网络不可用时快速重试（2 秒），避免长时间阻塞线程导致服务被杀
- 发送失败自动进入重试闹钟；用户确认醒来后主动取消重试，防止"人醒了还在报警"

---

## 已修复的关键问题

完整记录见 [SLEEP_MONITOR_BUG_FIXES.md](./SLEEP_MONITOR_BUG_FIXES.md)，代表性修复：

| # | 问题 | 根因 | 修复 |
| --- | --- | --- | --- |
| 1 | 入睡检测永不触发 | 夜间短暂亮屏无条件重置屏幕关闭计时，时长永远累积不到阈值 | 亮屏 < 5 分钟不重置计时 |
| 2 | 凌晨入睡快照归类错误 | 快照按入睡当日日期存储，跨天查询找不到 | 入睡时间早于预设起床时间时，快照回退一天 |
| 3 | 有效快照被误删 | 清理循环中 Calendar 复用导致删除范围错误 | 独立 Calendar 副本，精确清理 7~30 天前快照 |
| 4 | 起床闹钟未设置 | 闹钟仅在睡眠检测成功后设置 | 服务每次启动无条件设置下一次起床检查闹钟 |
| 5 | 深度睡眠唤醒后邮件失败 | 刚唤醒时网络模块未就绪 | 网络等待 + 快速重试 + 可重试错误关键字 |
| 6 | 报警时间延迟累积 | `scheduleAtFixedRate` 延迟累积 | 改用 `scheduleWithFixedDelay` |
| 7 | 同日重复报警 | 醒来后邮件重试闹钟未取消 | 醒来时取消重试闹钟 + 当日去重 |

---

## 调试与排查

```bash
# 查看运行日志
adb logcat | grep -E "SleepMonitor|CheckinService|MailSender"

# 查看文件日志（同时输出到 Logcat 与文件）
adb shell cat /sdcard/Android/data/com.livewell/files/app_logs.txt

# 清除数据 / 强制停止（测试用）
adb shell pm clear com.livewell
adb shell am force-stop com.livewell
```

| 现象 | 排查方向 |
| --- | --- |
| 睡眠监测不工作 | 日志搜 "SleepMonitorService 已启动" |
| 入睡未检测到 | 检查屏幕关闭阈值配置，日志搜 "确认入睡" |
| 邮件发不出 | 日志搜 "邮件配置不完整" / 检查授权码 |
| 报警延迟 | 日志搜 "检查触发时间" |

---

## 相关文档

- [PROJECT_ARCHITECTURE.md](./PROJECT_ARCHITECTURE.md) — 模块级架构文档（含核心代码解析、完整业务流程图、开发规范）
- [SLEEP_MONITOR_BUG_FIXES.md](./SLEEP_MONITOR_BUG_FIXES.md) — 睡眠监测关键 Bug 修复实录

---

## License

本项目仅用于学习与课程设计交流。
