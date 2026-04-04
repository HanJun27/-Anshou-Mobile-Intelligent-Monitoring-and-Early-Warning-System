package com.livewell.untils

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import java.util.Calendar
import java.util.concurrent.TimeUnit

class UsageStatsHelper(private val context: Context) {

    

    fun getTodayAppUsageMinutes(): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return 0
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            calendar.timeInMillis,
            System.currentTimeMillis()
        )

        var totalUsage = 0L
        stats?.forEach { usageStats ->
            val packageName = usageStats.packageName
            // ✅ 扩大关键应用范围，更准确反映用户使用情况
            if (isCriticalApp(packageName)) {
                totalUsage += usageStats.totalTimeInForeground
            }
        }

        return TimeUnit.MILLISECONDS.toMinutes(totalUsage)
    }

    // app/src/main/java/com/livewell/untils/UsageStatsHelper.kt

private fun isCriticalApp(packageName: String): Boolean {
    // ========== 精确匹配列表 ==========
    val exactMatches = listOf(
        // 本应用
        context.packageName,
        
        // 系统核心应用
        "com.android.phone",           // 电话
        "com.android.mms",             // 短信
        "com.android.systemui",        // 系统 UI
        "android",                     // 系统服务
        "com.google.android.apps.maps", // 谷歌地图
        
        // 社交通讯
        "com.tencent.mm",              // 微信
        "com.tencent.mobileqq",        // QQ
        "com.tencent.qqlite",          // QQ 轻量版
        "com.eg.android.AlipayGphone", // 支付宝
        "com.sina.weibo",              // 微博
        "com.xingin.xhs",              // 小红书
        "com.zhihu.android",           // 知乎
        "com.douban.frodo",            // 豆瓣
        "com.coolapk.market",          // 酷安
        "com.immomo.momo",             // 陌陌
        
        // 短视频
        "com.ss.android.ugc.aweme",    // 抖音
        "com.ss.android.ugc.aweme.lite", // 抖音极速版
        "com.kuaishou.nebula",         // 快手
        "com.kuaishou.nebula.lite",    // 快手极速版
        "com.bilibili.app.in",         // 哔哩哔哩
        "tv.danmaku.bili",             // 哔哩哔哩 HD
        
        // 长视频
        "com.qiyi.video",              // 爱奇艺
        "com.tencent.qqlive",          // 腾讯视频
        "com.youku.phone",             // 优酷
        "com.hunantv.imgo.activity",   // 芒果 TV
        "com.pptv.pptv",               // PPTV
        "com.sohu.sohuvideo",          // 搜狐视频
        
        // 购物电商
        "com.taobao.taobao",           // 淘宝
        "com.jingdong.app.mall",       // 京东
        "com.xunmeng.pinduoduo",       // 拼多多
        "com.suning.easyug",           // 苏宁易购
        "com.vipshop.vip",             // 唯品会
        "com.taobao.idlefish",         // 闲鱼
        "com.tmall.wireless",          // 天猫
        
        // 生活服务
        "com.sankuai.meituan",         // 美团
        "com.sankuai.meituan.takeoutnew", // 美团外卖
        "me.ele",                      // 饿了么
        "com.dianping.v1",             // 大众点评
        "com.autonavi.minimap",        // 高德地图
        "com.baidu.BaiduMap",          // 百度地图
        "com.UCMobile",                // UC 浏览器
        "com.android.browser",         // 系统浏览器
        "com.tencent.mtt",             // QQ 浏览器
        "com.quark.browser",           // 夸克浏览器
        "com.ss.android.article.news", // 今日头条
        "com.ss.android.article.lite", // 今日头条极速版
        "com.netease.newsreader.activity", // 网易新闻
        "com.ifeng.newspaper",         // 凤凰新闻
        "com.ximalaya.ting.android",   // 喜马拉雅
        "com.qzone",                   // QQ 空间
        "com.tencent.karaoke",         // 全民 K 歌
        "com.netease.cloudmusic",      // 网易云音乐
        "com.tencent.qqmusic",         // QQ 音乐
        "cn.kuwo.player",              // 酷我音乐
        
        // 办公学习
        "com.tencent.wps",             // WPS Office
        "com.tencent.docs",            // 腾讯文档
        "com.youdao.dict",             // 有道词典
        "com.baidu.tieba",             // 百度贴吧
        "com.hujiang.activity",        // 沪江网校
        "com.fenbi.android.mobile",    // 粉笔
        "com.yuanfudao.android.common", // 猿辅导
        
        // 游戏
        "com.tencent.tmgp.sgame",      // 王者荣耀
        "com.tencent.tmgp.pubgmhd",    // 和平精英
        "com.netease.hyxd",            // 阴阳师
        "com.miHoYo.GenshinImpact",    // 原神
        "com.tencent.tmgp.cf",         // 穿越火线
        "com.netease.dwrg",            // 第五人格
    )
    
    // ========== 包名前缀匹配（覆盖整个应用家族）==========
    val prefixMatches = listOf(
        "com.tencent.",           // 腾讯系应用（微信、QQ、腾讯游戏等）
        "com.alibaba.",           // 阿里系应用（淘宝、天猫、支付宝等）
        "com.baidu.",             // 百度系应用（百度地图、贴吧等）
        "com.ss.android.",        // 字节系应用（抖音、今日头条等）
        "com.netease.",           // 网易系应用（网易云音乐、网易游戏等）
        "com.xiaomi.",            // 小米系应用
        "com.huawei.",            // 华为系应用
        "com.oplus.",             // OPPO 系应用
        "com.vivo.",              // vivo 系应用
        "com.samsung.",           // 三星系应用
        "com.google.android.",    // 谷歌系应用
        "com.meituan.",           // 美团系应用
        "com.jd.",                // 京东系应用
        "com.sina.",              // 新浪系应用（微博等）
        "com.sohu.",              // 搜狐系应用
        "com.ifeng.",             // 凤凰系应用
        "com.taobao.",            // 淘宝系应用
        "com.tmall.",             // 天猫系应用
        "com.kuaishou.",          // 快手系应用
        "com.bilibili.",          // B 站系应用
        "com.qiyi.",              // 爱奇艺系应用
        "com.youku.",             // 优酷系应用
        "com.xunmeng.",           // 拼多多系应用
        "com.dianping.",          // 大众点评系应用
        "com.autonavi.",          // 高德系应用
        "com.amap.",              // 高德地图系应用
        "com.eg.",                // 支付宝系应用
        "com.alipay.",            // 支付宝系应用
        "com.ximalaya.",          // 喜马拉雅系应用
        "com.moji.",              // 墨迹天气系应用
        "com.qihoo.",             // 360 系应用
        "com.lbe.",               // LBE 系应用
        "com.duokan.",            // 多看系应用
        "com.wps.",               // WPS 系应用
        "com.kingsoft.",          // 金山系应用
        "com.miui.",              // MIUI 系应用
        "com.hihonor.",           // 荣耀系应用
        "com.heytap.",            // 欢太系应用（OPPO/Realme）
        "com.realme.",            // Realme 系应用
        "com.iqoo.",              // iQOO 系应用
        "com.oneplus.",           // 一加系应用
        "com.meizu.",             // 魅族系应用
        "com.zte.",               // 中兴系应用
        "com.lenovo.",            // 联想系应用
        "com.asus.",              // 华硕系应用
        "com.htc.",               // HTC 系应用
        "com.lg.",                // LG 系应用
        "com.motorola.",          // 摩托罗拉系应用
        "com.nokia.",             // 诺基亚系应用
        "com.sony.",              // 索尼系应用
        "com.oppo.",              // OPPO 系应用
        "com.coloros.",           // ColorOS 系应用
    )
    
    // 精确匹配
    if (exactMatches.contains(packageName)) {
        return true
    }
    
    // 前缀匹配
    for (prefix in prefixMatches) {
        if (packageName.startsWith(prefix)) {
            return true
        }
    }
    
    return false
}

    fun hasUsageStatsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return true
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - TimeUnit.DAYS.toMillis(1),
            now
        )
        return stats != null && stats.isNotEmpty()
    }
}