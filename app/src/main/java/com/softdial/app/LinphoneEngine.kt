package com.softdial.app

import android.content.Context
import org.linphone.core.Account
import org.linphone.core.AccountParams
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType
import java.util.Locale

/** Server 1 engine. */
class LinphoneEngine(
    private val context: Context,
    private val onStatus: (String) -> Unit
) : SipEngine {

    private var core: Core? = null

    private val listener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState,
            message: String
        ) {
            onStatus("Server 1 registration: $state")
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            onStatus("Server 1 call: $state")
        }
    }

    override fun start() {
        if (core != null) return
        val c = Factory.instance().createCore(null, null, context.applicationContext)
        c.addListener(listener)
        configureAudio(c)
        c.start()
        core = c
    }

    override fun register(username: String, password: String, domain: String) {
        start()
        val c = core ?: return
        val user = username.trim()
        val dom = domain.trim()
        if (user.isEmpty() || password.isEmpty() || dom.isEmpty()) {
            onStatus("Fill username, password and server")
            return
        }
        try {
            val identity = Factory.instance().createAddress("sip:$user@$dom")
            val server = Factory.instance().createAddress("sip:$dom")
            if (identity == null || server == null) {
                onStatus("Invalid SIP address")
                return
            }
            server.transport = TransportType.Udp
            c.addAuthInfo(Factory.instance().createAuthInfo(user, null, password, null, null, dom))
            val params: AccountParams = c.createAccountParams()
            params.identityAddress = identity
            params.serverAddress = server
            params.isRegisterEnabled = true
            val account = c.createAccount(params)
            c.addAccount(account)
            c.defaultAccount = account
        } catch (e: Exception) {
            onStatus("Server 1 error: ${e.message}")
        }
    }

    override fun call(number: String, domain: String): Boolean {
        val c = core ?: return false
        return try {
            val target = if (number.contains("@") || number.startsWith("sip:")) number else "sip:${number.trim()}@${domain.trim()}"
            val address = c.interpretUrl(target) ?: return false
            val params = c.createCallParams(null) ?: return false
            c.inviteAddressWithParams(address, params) != null
        } catch (e: Exception) {
            onStatus("Server 1 error: ${e.message}")
            false
        }
    }

    override fun hangUp() {
        val c = core ?: return
        try {
            c.currentCall?.terminate()
            c.calls.forEach { it.terminate() }
        } catch (_: Exception) {
        }
    }

    // ---- audio settings (reflection keeps this independent of exact SDK method names) ----

    private fun configureAudio(c: Core) {
        // Microphone input gain.
        invoke(c, "setMicGainDb", java.lang.Float.TYPE, -18f)
        // Echo cancellation off.
        invoke(c, "enableEchoCancellation", java.lang.Boolean.TYPE, false)
        // Only PCMU (8 kHz) is enabled.
        val types = try { c.javaClass.getMethod("getAudioPayloadTypes").invoke(c) } catch (_: Throwable) { null }
        val list: List<Any> = when (types) {
            is Array<*> -> types.filterNotNull()
            is Iterable<*> -> types.filterNotNull()
            else -> emptyList()
        }
        for (t in list) {
            val mime = try { (t.javaClass.getMethod("getMimeType").invoke(t) as? String).orEmpty().lowercase(Locale.ROOT) } catch (_: Throwable) { "" }
            val keep = mime == "pcmu" || mime == "telephone-event"
            invoke(t, "enable", java.lang.Boolean.TYPE, keep)
        }
    }

    private fun invoke(target: Any, name: String, type: Class<*>, value: Any) {
        try {
            target.javaClass.getMethod(name, type).invoke(target, value)
        } catch (_: Throwable) {
        }
    }
}
