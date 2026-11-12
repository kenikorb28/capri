package com.capri.assistant.system

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import com.capri.core.Lang
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// PROTECTION: Android's own fingerprint (or face, or the phone's code) question, asked before a sensitive action when
// they turned the switch on. Capri never sees the fingerprint: Android only answers yes or no. Anything but a clear
// yes (cancelled, no lock set up, timed out) is a no.
object Gate {
    val Kinds: Int = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    private val waiting = ConcurrentHashMap<Int, (Boolean) -> Unit>()
    private val next = AtomicInteger(1)

    // Whether this phone has a fingerprint, a face or a screen lock Android can ask for.
    fun ready(context: Context): Boolean = try { context.getSystemService(BiometricManager::class.java)?.canAuthenticate(Kinds) == BiometricManager.BIOMETRIC_SUCCESS } catch (e: Exception) { false }

    // Android's question needs a screen of hers in front, so a see-through one carries it. The answer comes on the main thread.
    fun ask(context: Context, title: String, then: (Boolean) -> Unit) {
        val id = next.getAndIncrement()
        waiting[id] = then
        try {
            context.startActivity(Intent(context, GateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION).putExtra("id", id).putExtra("title", title))
        } catch (e: Exception) { answer(id, false) }
    }

    fun answer(id: Int, ok: Boolean) { waiting.remove(id)?.invoke(ok) }

    // For her worker threads: waits for the answer, at most a minute. Never call it on the main thread.
    fun blocking(context: Context, title: String, seconds: Long = 60): Boolean {
        val done = CountDownLatch(1)
        var ok = false
        ask(context, title) { ok = it; done.countDown() }
        return done.await(seconds, TimeUnit.SECONDS) && ok
    }
}

class GateActivity : Activity() {
    private var id = 0
    private var answered = false
    private val cancel = CancellationSignal()

    private fun done(ok: Boolean) {
        if (answered) return
        answered = true
        Gate.answer(id, ok)
        finish()
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        id = intent.getIntExtra("id", 0)
        try {
            val prompt = BiometricPrompt.Builder(this)
                .setTitle(intent.getStringExtra("title") ?: "Capri")
                .setSubtitle(Lang.pick("Capri asks that it's really you", "Capri vérifie que c'est bien toi"))
                .setAllowedAuthenticators(Gate.Kinds)
                .build()
            prompt.authenticate(cancel, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { done(true) }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { done(false) }
            })
        } catch (e: Exception) { done(false) }
    }

    override fun onDestroy() {
        if (!answered) { answered = true; try { cancel.cancel() } catch (e: Exception) { }; Gate.answer(id, false) }
        super.onDestroy()
    }
}
