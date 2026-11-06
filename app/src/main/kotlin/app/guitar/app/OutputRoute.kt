package app.guitar.app

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Watches where sound goes: the phone's own speaker, or something plugged in / paired
 * (wired or USB headphones, a USB DAC, Bluetooth headphones or speaker, hearing aids).
 * [onChange] fires once at [start] and again on every plug/unplug or connect/disconnect,
 * with `true` while the phone speaker is the output. Android-only by nature (Lockstep:
 * a browser cannot see the output route).
 */
class OutputRoute(context: Context, private val onChange: (onPhoneSpeaker: Boolean) -> Unit) {
    private val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var last: Boolean? = null

    private val external: Set<Int> = buildSet {
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET); add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_LINE_ANALOG); add(AudioDeviceInfo.TYPE_LINE_DIGITAL)
        add(AudioDeviceInfo.TYPE_AUX_LINE)
        add(AudioDeviceInfo.TYPE_USB_DEVICE); add(AudioDeviceInfo.TYPE_USB_ACCESSORY)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP); add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        add(AudioDeviceInfo.TYPE_HDMI)
        if (Build.VERSION.SDK_INT >= 26) add(AudioDeviceInfo.TYPE_USB_HEADSET)
        if (Build.VERSION.SDK_INT >= 28) add(AudioDeviceInfo.TYPE_HEARING_AID)
        if (Build.VERSION.SDK_INT >= 31) { add(AudioDeviceInfo.TYPE_BLE_HEADSET); add(AudioDeviceInfo.TYPE_BLE_SPEAKER) }
    }

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = check()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = check()
    }

    /** True when no external output is connected, i.e. the phone speaker plays. */
    fun onPhoneSpeaker(): Boolean =
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).none { it.type in external }

    private fun check() {
        val now = onPhoneSpeaker()
        if (now != last) { last = now; onChange(now) }
    }

    fun start() { am.registerAudioDeviceCallback(callback, handler); check() }
    fun stop() = am.unregisterAudioDeviceCallback(callback)
}
