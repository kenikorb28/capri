package com.capri.assistant.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import com.capri.assistant.engine.Capri
import com.capri.assistant.screen.CapriAccessibility
import com.capri.core.Lang

// How their phone is doing, read from what Android lets any app see. CPU load and game FPS aren't readable by apps on
// Android, so she never makes them up.
object DeviceInfo {
    class Battery(val percent: Int, val charging: Boolean, val plug: String?, val celsius: Double?, val saver: Boolean)

    fun battery(context: Context): Battery {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = context.getSystemService(BatteryManager::class.java)
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
            ?: sticky?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / maxOf(1, it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)) } ?: -1
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val plug = when (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) { BatteryManager.BATTERY_PLUGGED_AC -> "AC"; BatteryManager.BATTERY_PLUGGED_USB -> "USB"; BatteryManager.BATTERY_PLUGGED_WIRELESS -> Lang.pick("wireless", "sans fil"); 0 -> null; else -> "?" }
        val t = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10.0 }
        return Battery(pct, charging, plug, t, context.getSystemService(PowerManager::class.java).isPowerSaveMode)
    }

    fun heat(context: Context): String = when (context.getSystemService(PowerManager::class.java).currentThermalStatus) {
        PowerManager.THERMAL_STATUS_NONE -> Lang.pick("cool", "normale")
        PowerManager.THERMAL_STATUS_LIGHT -> Lang.pick("a little warm", "un peu chaud")
        PowerManager.THERMAL_STATUS_MODERATE -> Lang.pick("warm, Android may slow it a bit", "chaud, Android peut le ralentir un peu")
        PowerManager.THERMAL_STATUS_SEVERE -> Lang.pick("hot, Android is slowing it down", "très chaud, Android le ralentit")
        else -> Lang.pick("overheating, Android may close apps", "en surchauffe, Android peut fermer des apps")
    }

    fun memory(context: Context): Triple<Long, Long, Boolean> {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return Triple(info.availMem, info.totalMem, info.lowMemory)
    }

    fun storage(): Pair<Long, Long> { val s = StatFs(Environment.getDataDirectory().path); return s.availableBytes to s.totalBytes }

    fun network(context: Context): String {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return Lang.pick("no connection", "pas de connexion")
        val kind = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Lang.pick("mobile data", "données mobiles")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> Lang.pick("a network", "un réseau")
        }
        val works = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return kind + (if (works) "" else Lang.pick(" (no internet through it)", " (sans accès à internet)"))
    }

    fun online(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun onWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    fun headphones(context: Context): String? {
        val outs = context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val d = outs.firstOrNull { it.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLE_HEADSET) } ?: return null
        return d.productName?.toString()?.takeIf { it.isNotBlank() } ?: Lang.pick("headphones", "un casque")
    }

    private fun gb(b: Long) = String.format("%.1f", b / 1_073_741_824.0)

    fun status(context: Context, focus: String?): String {
        val parts = ArrayList<String>()
        val all = focus == null || focus == "all"
        if (all || focus == "battery" || focus == "heat") {
            val b = battery(context)
            parts.add(Lang.pick("Battery ${b.percent}%", "Batterie ${b.percent} %") + (if (b.charging) Lang.pick(", charging" + (b.plug?.let { " ($it)" } ?: ""), ", en charge" + (b.plug?.let { " ($it)" } ?: "")) else "") +
                (if (b.saver) Lang.pick(", battery saver on", ", économiseur activé") else "") + (b.celsius?.let { Lang.pick(", battery at %.0f °C".format(it), ", batterie à %.0f °C".format(it)) } ?: ""))
        }
        if (all || focus == "heat") parts.add(Lang.pick("Phone temperature: ", "Température du téléphone : ") + heat(context))
        if (all || focus == "memory") {
            val (free, total, low) = memory(context)
            parts.add(Lang.pick("Memory: ${gb(free)} GB free of ${gb(total)} GB", "Mémoire : ${gb(free)} Go libres sur ${gb(total)} Go") + (if (low) Lang.pick(", running low", ", presque pleine") else ""))
        }
        if (all || focus == "storage") { val (free, total) = storage(); parts.add(Lang.pick("Storage: ${gb(free)} GB free of ${gb(total)} GB", "Stockage : ${gb(free)} Go libres sur ${gb(total)} Go")) }
        if (all || focus == "network") {
            parts.add(Lang.pick("Network: ", "Réseau : ") + network(context))
            parts.add(headphones(context)?.let { Lang.pick("Audio out: $it", "Son vers : $it") } ?: Lang.pick("No headphones connected", "Pas de casque branché"))
        }
        if (all) parts.add(Lang.pick("(Apps can't read CPU load or game FPS on Android.)", "(Android ne laisse pas les apps lire la charge du processeur ni les FPS des jeux.)"))
        return parts.joinToString(". ") + "."
    }

    // One short line about the phone for her brain: battery, network, the app in front, headphones, her mode.
    fun situation(context: Context, capri: Capri): String = try {
        val b = battery(context)
        val bits = ArrayList<String>()
        bits.add(Lang.pick("battery ${b.percent}%", "batterie ${b.percent} %") + if (b.charging) Lang.pick(" charging", " en charge") else "")
        bits.add(network(context))
        CapriAccessibility.front?.let { pkg -> Apps.label(context, pkg)?.let { bits.add(Lang.pick("app in front: $it", "app au premier plan : $it")) } }
        headphones(context)?.let { bits.add(Lang.pick("headphones on", "casque branché")) }
        if (capri.prefs.mode != "normal") bits.add(Lang.pick("mode ", "mode ") + capri.prefs.mode)
        bits.joinToString(", ")
    } catch (e: Exception) { "" }
}
