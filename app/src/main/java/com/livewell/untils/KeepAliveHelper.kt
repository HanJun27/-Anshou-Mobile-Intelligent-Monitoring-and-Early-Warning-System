package com.livewell.untils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

class KeepAliveHelper(private val context: Context) {

    private val tag = "KeepAliveHelper"

    fun requestBatteryOptimizationIgnore() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.i(tag, "已请求电池优化忽略")
            } catch (e: Exception) {
                Log.e(tag, "请求电池优化失败：${e.message}")
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            }
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    fun getAutoStartIntent(): Intent? {
        return when {
            Build.MANUFACTURER.equals("Huawei", ignoreCase = true) ||
                    Build.BRAND.equals("Huawei", ignoreCase = true) ||
                    Build.BRAND.equals("Honor", ignoreCase = true) -> {
                try {
                    Intent().apply {
                        setClassName("com.huawei.systemmanager",
                            "com.huawei.systemmanager.optimize.process.ProtectActivity")
                    }
                } catch (e: Exception) {
                    null
                }
            }
            Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                    Build.BRAND.equals("Xiaomi", ignoreCase = true) -> {
                try {
                    Intent().apply {
                        action = "miui.intent.action.OP_AUTO_START"
                        addCategory(Intent.CATEGORY_DEFAULT)
                    }
                } catch (e: Exception) {
                    null
                }
            }
            Build.MANUFACTURER.equals("OPPO", ignoreCase = true) ||
                    Build.BRAND.equals("OPPO", ignoreCase = true) -> {
                try {
                    Intent().apply {
                        action = "com.oppo.externalsearchdialog.ACTION_OPEN_ROME_SETTINGS"
                        putExtra("from", "user")
                    }
                } catch (e: Exception) {
                    null
                }
            }
            Build.MANUFACTURER.equals("vivo", ignoreCase = true) ||
                    Build.BRAND.equals("vivo", ignoreCase = true) -> {
                try {
                    Intent().apply {
                        setClassName("com.iqoo.secure",
                            "com.iqoo.secure.safeguard.PurviewTabActivity")
                    }
                } catch (e: Exception) {
                    null
                }
            }
            Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
                    Build.BRAND.equals("samsung", ignoreCase = true) -> {
                try {
                    Intent().apply {
                        action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                        data = Uri.parse("package:${context.packageName}")
                    }
                } catch (e: Exception) {
                    null
                }
            }
            else -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
        }
    }

    fun getManufacturer(): String {
        return Build.MANUFACTURER
    }

    fun getKeepAliveAdvice(): String {
        val manufacturer = getManufacturer().lowercase()

        return when {
            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                "华为/荣耀手机设置指南：\n" +
                        "1. 打开「手机管家」APP\n" +
                        "2. 点击「应用启动管理」\n" +
                        "3. 找到「活着呢」，关闭「自动管理」\n" +
                        "4. 在弹出的窗口中，允许「自启动」、「关联启动」、「后台活动」\n" +
                        "5. 在「最近任务」界面，下拉应用卡片锁定"

            manufacturer.contains("xiaomi") ->
                "小米手机设置指南：\n" +
                        "1. 打开「安全中心」APP\n" +
                        "2. 点击「授权管理」→「自启动管理」\n" +
                        "3. 找到「活着呢」，开启自启动权限\n" +
                        "4. 在「最近任务」界面，下拉应用卡片锁定\n" +
                        "5. 进入「设置」→「电池与性能」→「应用省电」→ 选择「无限制」"

            manufacturer.contains("oppo") ->
                "OPPO手机设置指南：\n" +
                        "1. 打开「手机管家」APP\n" +
                        "2. 点击「权限隐私」→「自启动管理」\n" +
                        "3. 找到「活着呢」，开启自启动\n" +
                        "4. 在「最近任务」界面，下拉应用卡片锁定\n" +
                        "5. 进入「设置」→「电池」→「应用耗电管理」→ 关闭「自动优化」"

            manufacturer.contains("vivo") ->
                "vivo手机设置指南：\n" +
                        "1. 打开「i管家」APP\n" +
                        "2. 点击「应用管理」→「权限管理」→「自启动」\n" +
                        "3. 找到「活着呢」，开启自启动\n" +
                        "4. 在「最近任务」界面，下拉应用卡片锁定\n" +
                        "5. 进入「设置」→「电池」→「后台耗电管理」→ 选择「允许后台高耗电」"

            else ->
                "通用设置指南：\n" +
                        "1. 进入「设置」→「应用」→「活着呢」\n" +
                        "2. 开启「自启动」权限（如果有）\n" +
                        "3. 在「电池」或「省电」设置中，选择「无限制」\n" +
                        "4. 在「最近任务」界面，下拉应用卡片锁定"
        }
    }
}