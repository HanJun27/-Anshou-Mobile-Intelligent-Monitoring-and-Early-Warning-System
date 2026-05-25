# 睡眠监测关键Bug修复总结

**修复日期**: 2026-04-15  
**修复者**: AI Assistant  
**诊断来源**: 深入代码分析（3个相互关联的关键Bug）

---

## 🎯 修复概览

本次修复解决了睡眠监测功能的核心问题，包括：
1. ✅ **入睡检测永远无法触发**（根本原因）
2. ✅ **睡眠快照日期归类错误**
3. ✅ **旧快照清理范围错误**
4. ✅ **起床异常闹钟未设置**

这些Bug导致了以下症状：
- ❌ 睡眠快照记录界面看不到任何记录
- ❌ 7:35 起床异常警报从未触发
- ❌ 第二天查询时使用时长显示错误（包含凌晨0点到入睡时刻的数据）

---

## 🐛 Bug #1: handleScreenOff 每次都重置屏幕关闭时间（最关键）

### 问题描述

**文件**: `SleepMonitorService.kt` 第 317-344 行

**之前的代码**:
```kotlin
private fun handleScreenOff() {
    val previousScreenOffTime = lastScreenOffTime
    lastScreenOffTime = System.currentTimeMillis()  // ❌ 每次都重置为 NOW
    prefsManager.saveLong("last_screen_off_time", lastScreenOffTime)
    ...
}
```

**问题分析**:
这段代码的注释自称是"修复"，但实际上引入了更严重的bug。配合 `handleScreenOn` 不重置 `lastScreenOffTime` 的策略，结果是：

```
23:00 你把手机放下 → lastScreenOffTime = 23:00 ✅
23:15 短暂瞥一眼屏幕（亮屏 5 秒）→ lastScreenOffTime 不变
23:15:05 再次黑屏 → lastScreenOffTime 被重置为 23:15:05 ❌
23:30 入睡检测：screenOffDuration = 14分55秒 < 15分钟 → 不通过
永远无法触发 confirmSleep()
```

**连锁反应**:
1. `confirmSleep()` 永远不被调用 → 睡眠快照永远不保存 ✅ 匹配症状
2. `confirmSleep()` 不调用 → `scheduleLatestWakeUpAlarm()` 不被设置 → 7:35 起床异常警报永远不触发 ✅ 匹配症状
3. 7:55 的 CheckinService 用的是独立的 AlarmManager 调度，所以这条路径不受影响 ✅ 匹配"7:56 能正常报警"

这就是为什么"加入睡眠快照功能后"问题出现的——把日常瞥手机重置变成了一个连锁故障。

---

### 修复方案

#### 步骤1: 新增字段

在类成员声明处（第 66 行附近）新增：
```kotlin
private var lastScreenOnTime: Long = 0  // ✅ Fix #1: 记录亮屏时间，用于判断是否需要重置关闭时间
```

#### 步骤2: 修改 handleScreenOff()

替换整个方法（第 323-360 行）：
```kotlin
private fun handleScreenOff() {
    val now = System.currentTimeMillis()
    val previousScreenOffTime = lastScreenOffTime

    // ✅ Fix #1: 关键修复：不再无条件重置 lastScreenOffTime
    // 仅当（a）首次记录，或（b）刚才用户真正在使用手机（亮屏≥5分钟）才重置
    // 否则视为“夜间短暂查看手机”，保留原始关闭时间以累积入睡判定窗口
    val SCREEN_ON_RESET_THRESHOLD = 5 * 60 * 1000L  // 5分钟
    val screenOnDuration = if (lastScreenOnTime > 0) now - lastScreenOnTime else 0L

    val shouldUpdate = (lastScreenOffTime == 0L) || (screenOnDuration >= SCREEN_ON_RESET_THRESHOLD)

    if (shouldUpdate) {
        lastScreenOffTime = now
        prefsManager.saveLong("last_screen_off_time", lastScreenOffTime)
        if (previousScreenOffTime == 0L) {
            Log.i(tag, "📱 屏幕关闭（首次记录）")
            com.livewell.untils.AppLogger.i(tag, "📱 屏幕关闭（首次记录）")
        } else {
            Log.i(tag, "📱 屏幕关闭（亮屏${screenOnDuration / 1000}秒后重置）")
            com.livewell.untils.AppLogger.i(tag, "📱 屏幕关闭（亮屏${screenOnDuration / 1000}秒后重置）")
        }
    } else {
        val keptOffDuration = now - lastScreenOffTime
        Log.d(tag, "📱 短暂亮屏${screenOnDuration / 1000}秒后再次关闭，保留原始关闭时间，已累积关闭${keptOffDuration / 1000}秒")
        com.livewell.untils.AppLogger.d(tag, "📱 短暂亮屏后再次关闭，保留原始关闭时间")
    }

    // ✅ 关键事件：立即保存缓存数据
    if (sleepDataCache.isNotEmpty()) {
        tryBatchSaveData()
    }

    // 检查是否满足入睡候选条件
    if (isWithinSleepTimeWindow()) {
        checkSleepCandidate()
    }
}
```

#### 步骤3: 修改 handleScreenOn()

在方法开头追加一行（第 371 行）：
```kotlin
private fun handleScreenOn() {
    // ✅ Fix #1: 记录亮屏时刻
    lastScreenOnTime = System.currentTimeMillis()
    
    // ... 后面保留原代码
}
```

#### 步骤4: 在重置状态时同时重置这两个变量

在以下位置添加重置代码：

**A. checkWakeUp() 中的醒来判定成功分支**（第 1121-1125 行）:
```kotlin
// ✅ Fix #1: 关键修复：重置屏幕关闭时间，让下一次睡眠周期能正确累积
lastScreenOffTime = 0
lastScreenOnTime = 0
prefsManager.saveLong("last_screen_off_time", 0)
```

**B. forceWakeUp()**（第 1201-1205 行）:
```kotlin
// ✅ Fix #1: 关键修复：重置屏幕关闭时间，让下一次睡眠周期能正确累积
lastScreenOffTime = 0
lastScreenOnTime = 0
prefsManager.saveLong("last_screen_off_time", 0)
```

**C. sendOverSleepAlert()**（第 1692-1696 行）:
```kotlin
// ✅ Fix #1: 关键修复：重置屏幕关闭时间，让下一次睡眠周期能正确累积
lastScreenOffTime = 0
lastScreenOnTime = 0
prefsManager.saveLong("last_screen_off_time", 0)
```

---

### 预期效果

应用 Fix #1 后，你应该立刻就能在"开发者设置 → 睡眠快照记录"里看到记录，并且 7:35 的起床异常报警也会恢复触发。

---

## 🐛 Bug #2: 睡眠快照日期未按"前一天"归类

### 问题描述

**文件**: `SleepMonitorService.kt` 第 2045-2056 行

**之前的代码**:
```kotlin
// ✅ 修复：使用入睡当天的日期，而不是前一天    ← 这个"修复"方向反了！
val sleepDate = Calendar.getInstance().apply {
    time = Date(sleepTime)
}
val sleepDateStr = dateFormat.format(sleepDate.time)
```

**问题分析**:
设计意图是："超过 0 点还没入睡时，把 0:00 到入睡时刻的数据归为前一天"。但代码注释里有人"修复"为使用当天日期。

**结果**: 
- 用户 01:00 入睡 → 快照存为周二
- 周二早上报警时，`getCompleteDayActivity()` 查找的是"昨天"（周一）的快照 → 找不到
- 降级为原始数据，包含了 0:00 至 01:00 的"昨日活动"

---

### 修复方案

替换 `recordPreSleepDataSnapshot()` 中的日期计算逻辑（第 2083-2100 行）：

```kotlin
// ✅ Fix #2: 修复：若用户在凌晨（早于预设起床时间）才入睡，把这次入睡归为“前一天”
//   例如预设起床 6:00，用户 1:30 才睡，则快照日期记为前一天
val sleepCal = Calendar.getInstance().apply {
    time = Date(sleepTime)
}
val sleepHour = sleepCal.get(Calendar.HOUR_OF_DAY)
val sleepMinute = sleepCal.get(Calendar.MINUTE)
val sleepMinutesOfDay = sleepHour * 60 + sleepMinute

val wakeHour = prefsManager.getWakeUpHour()
val wakeMinute = prefsManager.getWakeUpMinute()
val wakeMinutesOfDay = wakeHour * 60 + wakeMinute

if (sleepMinutesOfDay < wakeMinutesOfDay) {
    sleepCal.add(Calendar.DAY_OF_YEAR, -1)
    com.livewell.untils.AppLogger.i(tag, "🌙 凌晨入睡，快照日期回退到前一天")
}
val sleepDateStr = dateFormat.format(sleepCal.time)
```

---

## 🐛 Bug #3: cleanupOldSnapshots 的循环逻辑错误

### 问题描述

**文件**: `PrefsManager.kt` 第 1579-1606 行

**之前的代码**:
```kotlin
// 删除 7-30 天前的快照
for (i in 7..30) {                          // 循环 24 次
    calendar.add(Calendar.DAY_OF_YEAR, -1)  // 每次只减 1 天
    val oldDate = dateFormat.format(calendar.time)
    ...
}
```

**问题分析**:
实际删除的是 currentDate - 1 到 currentDate - 24 天的快照，而不是"7 天前到 30 天前"。这会过早删除最近 6 天内的快照（包括前几天的有效快照）。

---

### 修复方案

替换整个 `cleanupOldSnapshots()` 方法（第 1579-1604 行）：

```kotlin
fun cleanupOldSnapshots(currentDate: String) {
    try {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val baseCal = Calendar.getInstance()
        baseCal.time = dateFormat.parse(currentDate) ?: Date()

        var cleanedCount = 0
        // ✅ Fix #3: 修复：真正清理 7-30 天前的快照（之前误删 1-24 天）
        for (daysAgo in 7..30) {
            val cal = baseCal.clone() as Calendar
            cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            val oldDate = dateFormat.format(cal.time)

            val hadSnapshot = getLong("pre_sleep_time_$oldDate", 0) > 0
            if (hadSnapshot) {
                removePreSleepSnapshot(oldDate)
                cleanedCount++
            }
        }
        if (cleanedCount > 0) {
            android.util.Log.i("PrefsManager", "✅ 已清理 $cleanedCount 个 7 天前的旧快照")
        }
    } catch (e: Exception) {
        android.util.Log.e("PrefsManager", "❌ 清理旧快照失败：${e.message}", e)
    }
}
```

---

## 🐛 Bug #4: 起床异常闹钟仅在睡眠检测成功时才设置

### 问题描述

**文件**: `SleepMonitorService.kt` 第 161 和 897 行

**问题分析**:
`scheduleLatestWakeUpAlarm()` 仅在两处被调用：
1. `onCreate()` 中 `isWithinSleepTimeWindow()` 为真时（要求开 app 时刚好在睡眠时段）
2. `confirmSleep()` 成功后

如果用户白天打开 app（最常见情况），且因 Bug #1 入睡未检测到，则今天的 7:35 闹钟根本没设置。`startFullMonitoring()`（睡眠窗口启动闹钟触发）也没有调用它，是个遗漏。

对照可靠运行的 CheckinService：它在 `onCreate()` 中无条件 `scheduleAlarm()`，所以 7:55/7:56 一直能响。

---

### 修复方案

#### 步骤1: 在 onCreate() 末尾添加无条件调用

在 `onCreate()` 的 if/else 分支后（第 170-175 行）追加：

```kotlin
// ✅ Fix #4: 关键修复：无条件设置起床异常检查闹钟
// 之前仅在 confirmSleep() 或 onCreate 命中睡眠窗口时设置，
// 如果用户白天开 App 且当晚入睡检测失败，今天的起床警报闹钟就完全不会触发。
// 现在改为：服务每次启动都把闹钟设到下一次“预设起床 + 容差”时刻。
scheduleLatestWakeUpAlarm()
```

#### 步骤2: 在 startFullMonitoring() 末尾也添加调用

在 `startFullMonitoring()` 末尾（第 845-847 行）添加：

```kotlin
// ✅ Fix #4: 确保从低功耗唤醒后也重新设置起床异常闹钟
scheduleLatestWakeUpAlarm()
```

---

## 📊 修复验证

### 编译状态
✅ **BUILD SUCCESSFUL** - 所有修复已成功编译

### 预期效果

应用这4个修复后：

1. ✅ **入睡检测能正常触发**
   - 夜间短暂查看手机不会影响屏幕关闭时长累积
   - 屏幕关闭15分钟后能正确触发 `confirmSleep()`

2. ✅ **睡眠快照能正确保存**
   - `confirmSleep()` 被调用后会保存快照
   - 凌晨入睡的快照会正确归类到"前一天"

3. ✅ **7:35 起床异常闹钟会设置**
   - 服务每次启动都会无条件设置闹钟
   - 即使入睡检测失败，闹钟也会存在

4. ✅ **第二天查询时使用时长正确**
   - 能找到正确的快照
   - 减去睡前数据，只显示起床后的使用时长

5. ✅ **旧快照不会被误删**
   - 只清理7-30天前的快照
   - 最近6天内的快照保留

---

## 🔍 测试建议

### 测试场景1: 夜间短暂查看手机

1. 晚上23:00放下手机
2. 23:15短暂瞥一眼（亮屏5秒）
3. 继续睡觉
4. 23:30检查日志：应该看到"短暂亮屏5秒后再次关闭，保留原始关闭时间"
5. 23:45入睡检测应该触发（关闭时长45分钟 > 15分钟阈值）

### 测试场景2: 凌晨入睡

1. 凌晨01:30入睡
2. 检查日志：应该看到"🌙 凌晨入睡，快照日期回退到前一天"
3. 第二天早上查询使用时长，应该正确显示起床后的时长

### 测试场景3: 白天启动服务

1. 白天14:00打开App
2. 检查日志：应该看到"✅ 已设置最晚起床检查闹钟"
3. 第二天早上7:35应该能收到起床异常警报（如果未起床）

---

## 📝 其他发现的问题（不影响当前症状，建议关注）

1. **confirmedSleepStartTime 属性 getter 每次都读 SharedPreferences**（67-76 行）：在传感器回调里被频繁访问，会带来不必要的磁盘 I/O；建议改为内存字段，仅在 setter 和明确恢复点写盘。

2. **scheduleLatestWakeUpAlarm 在过了今天预设时刻时跳到明天**：例如 7:30 预设、容差 2 小时，6:30 才启动服务时会跳到明天 7:30+2h，今天的 9:30 警报错失。配合 Fix #4 后建议同时把"已过今天但未过 latestWakeTime"的边界条件改为今天。

3. **sendOverSleepAlert 报警后调用 recordSleepData(confirmedSleepStartTime, ...)**，但紧接着才把 confirmedSleepStartTime = 0：顺序正确，但日志里建议同时写一条快照清理记录，便于排查。

4. **saveAlertHistory 调用了 gson.toJson(history) 但拿到 json 后没用**（1431-1434 行）：是无害的死代码，可清理。

5. **isRetryableError 没有把"Read timed out"显式列进可重试**：163 邮箱在凌晨网络抖动时常见，建议增加 "timed out" / "Connection reset"。

6. **MailSender 没有为线程命名也没设 SocketTimeout**：默认无超时，极端情况下整个线程会被无限挂起。建议在 props 里加 "mail.smtp.connectiontimeout", "15000" 和 "mail.smtp.timeout", "20000"。

7. **BootReceiver 没有启动 SleepMonitorService**：仅启动 CheckinService 和 EmailReceiverService。这意味着设备重启后睡眠监测要等用户手动打开 App 才会运行——建议加上对应的启动逻辑。

---

## 🎯 总结

本次修复解决了睡眠监测功能的4个核心Bug，从根本上恢复了睡眠监测、快照保存、起床报警的完整功能链。

**关键修复点**:
1. ✅ 修复了 `handleScreenOff()` 无条件重置的问题（最根本）
2. ✅ 修正了凌晨入睡快照的日期归类
3. ✅ 修正了旧快照清理范围的循环逻辑
4. ✅ 确保了起床异常闹钟无条件设置

**下一步**:
- 重新安装应用并测试
- 观察今晚的入睡检测是否正常触发
- 明早检查睡眠快照记录和报警功能

---

**文档维护者**: AI Assistant  
**修复日期**: 2026-04-15
