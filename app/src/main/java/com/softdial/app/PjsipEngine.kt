package com.softdial.app

import android.content.Context
import org.pjsip.pjsua2.Account
import org.pjsip.pjsua2.AccountConfig
import org.pjsip.pjsua2.AudioMedia
import org.pjsip.pjsua2.AuthCredInfo
import org.pjsip.pjsua2.Call
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.Endpoint
import org.pjsip.pjsua2.EpConfig
import org.pjsip.pjsua2.OnCallMediaStateParam
import org.pjsip.pjsua2.OnCallStateParam
import org.pjsip.pjsua2.OnIncomingCallParam
import org.pjsip.pjsua2.OnRegStateParam
import org.pjsip.pjsua2.TransportConfig
import org.pjsip.pjsua2.pjmedia_type
import org.pjsip.pjsua2.pjsip_inv_state
import org.pjsip.pjsua2.pjsip_status_code
import org.pjsip.pjsua2.pjsip_transport_type_e
import org.pjsip.pjsua2.pjsua_call_media_status

/** Server 2 engine. */
class PjsipEngine(
    private val context: Context,
    private val onStatus: (String) -> Unit
) : SipEngine {

    private var ep: Endpoint? = null
    private var account: PjAccount? = null
    private var activeCall: PjCall? = null

    private fun reg() {
        val e = ep ?: return
        try {
            if (!e.libIsThreadRegistered()) e.libRegisterThread(Thread.currentThread().name)
        } catch (_: Throwable) {
        }
    }

    override fun start() {
        if (ep != null) return
        try {
            System.loadLibrary("pjsua2")
            val e = Endpoint()
            e.libCreate()
            val cfg = EpConfig()
            cfg.uaConfig.userAgent = "SoftDialSimple"
            cfg.uaConfig.maxCalls = 1
            cfg.medConfig.noVad = true
            cfg.medConfig.ecTailLen = 0        // echo cancellation off
            cfg.medConfig.clockRate = 8000     // 8 kHz audio
            cfg.medConfig.quality = 1          // basic codec quality
            cfg.medConfig.txDropPct = 8        // outgoing packet drop percentage
            cfg.medConfig.rxDropPct = 8        // incoming packet drop percentage
            cfg.medConfig.sndPlayLatency = 300 // audio latency (ms)
            cfg.medConfig.sndRecLatency = 300
            e.libInit(cfg)
            val t = TransportConfig()
            t.port = 0
            e.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP, t)
            e.libStart()
            ep = e
            reg()
            onlyG711(e)
        } catch (x: Throwable) {
            onStatus("Server 2 error: ${x.message}")
        }
    }

    private fun onlyG711(e: Endpoint) {
        try {
            val codecs = e.codecEnum2()
            for (i in 0 until codecs.size) {
                val id = codecs[i].codecId
                e.codecSetPriority(id, if (id.startsWith("PCMU/8000")) 255.toShort() else 0.toShort())
            }
        } catch (_: Throwable) {
        }
    }

    override fun register(username: String, password: String, domain: String) {
        start()
        val e = ep ?: return
        val user = username.trim()
        val dom = domain.trim()
        if (user.isEmpty() || password.isEmpty() || dom.isEmpty()) {
            onStatus("Fill username, password and server")
            return
        }
        try {
            reg()
            val ac = AccountConfig()
            ac.idUri = "sip:$user@$dom"
            ac.regConfig.registrarUri = "sip:$dom"
            ac.sipConfig.authCreds.add(AuthCredInfo("digest", "*", user, 0, password))
            val acc = PjAccount()
            acc.create(ac, true)
            account = acc
        } catch (x: Throwable) {
            onStatus("Server 2 error: ${x.message}")
        }
    }

    override fun call(number: String, domain: String): Boolean {
        val acc = account ?: return false
        return try {
            reg()
            val uri = if (number.contains("@") || number.startsWith("sip:")) number else "sip:${number.trim()}@${domain.trim()}"
            val c = PjCall(acc)
            activeCall = c
            c.makeCall(uri, CallOpParam(true))
            true
        } catch (x: Throwable) {
            onStatus("Server 2 error: ${x.message}")
            false
        }
    }

    override fun hangUp() {
        try {
            reg()
            activeCall?.hangup(CallOpParam(true))
        } catch (_: Throwable) {
        }
    }

    private inner class PjAccount : Account() {
        override fun onRegState(prm: OnRegStateParam) {
            val text = when {
                prm.code / 100 == 2 -> "Ok"
                prm.code == 0 -> "Progress"
                else -> "Failed: ${prm.reason}"
            }
            onStatus("Server 2 registration: $text")
        }

        override fun onIncomingCall(prm: OnIncomingCallParam) {
            // Incoming calls are not supported in this simple build: decline them.
            try {
                val c = PjCall(this, prm.callId)
                c.hangup(CallOpParam().also { it.statusCode = pjsip_status_code.PJSIP_SC_DECLINE })
            } catch (_: Throwable) {
            }
        }
    }

    private inner class PjCall : Call {
        constructor(acc: Account) : super(acc)
        constructor(acc: Account, id: Int) : super(acc, id)

        override fun onCallState(prm: OnCallStateParam) {
            val ci = try { info } catch (_: Throwable) { return }
            val text = when (ci.state) {
                pjsip_inv_state.PJSIP_INV_STATE_CALLING -> "Calling"
                pjsip_inv_state.PJSIP_INV_STATE_EARLY -> "Ringing"
                pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED -> "Connected"
                pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED -> "Ended"
                else -> return
            }
            onStatus("Server 2 call: $text")
        }

        override fun onCallMediaState(prm: OnCallMediaStateParam) {
            val e = ep ?: return
            val ci = try { info } catch (_: Throwable) { return }
            for (i in 0 until ci.media.size) {
                val mi = ci.media[i]
                if (mi.type == pjmedia_type.PJMEDIA_TYPE_AUDIO &&
                    mi.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE
                ) {
                    try {
                        val m = AudioMedia.typecastFromMedia(getMedia(i.toLong()))
                        val dm = e.audDevManager()
                        m.startTransmit(dm.playbackDevMedia)
                        dm.captureDevMedia.adjustTxLevel(0.15f)   // microphone level
                        dm.captureDevMedia.startTransmit(m)
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }
}
