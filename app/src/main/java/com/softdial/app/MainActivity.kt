package com.softdial.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Soft Dial: a simple, modern SIP caller with two servers. */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var user: EditText
    private lateinit var pass: EditText
    private lateinit var server: EditText
    private lateinit var number: EditText
    private lateinit var server1Btn: Button
    private lateinit var server2Btn: Button

    private var serverIndex = 1
    private var linphone: SipEngine? = null
    private var pjsip: SipEngine? = null

    // Short delay before each action.
    private fun delayed(ms: Long, action: () -> Unit) { handler.postDelayed(action, ms) }

    private fun show(text: String) {
        handler.post {
            status.text = text
            val t = text.lowercase()
            status.setTextColor(Color.parseColor(
                if (t.contains("error") || t.contains("fail") || t.contains("could not")) "#FCA5A5"
                else if (t.contains("connected") || t.contains("registered") && !t.contains("not")) "#86EFAC"
                else "#C7D2FE"))
            if (text.contains("Ended") || text.contains("Released") || text.contains("Error")) resetAudio()
        }
    }

    private fun engine(): SipEngine =
        if (serverIndex == 1) linphone ?: LinphoneEngine(this, ::show).also { linphone = it }
        else pjsip ?: PjsipEngine(this, ::show).also { pjsip = it }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    private fun gradient(c1: Int, c2: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(c1, c2)).apply {
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun styledField(editText: EditText): EditText = editText.apply {
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#6B7494"))
        textSize = 16f
        setSingleLine(true)
        background = rounded(Color.parseColor("#0F1530"), 14, Color.parseColor("#2A3358"))
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

    private fun styledButton(label: String, c1: Int, c2: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 16f
            background = gradient(c1, c2, 16)
            stateListAnimator = null
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { onClick() }
        }

    private fun card(vararg views: android.view.View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.parseColor("#151B36"), 22, Color.parseColor("#252E55"))
            setPadding(dp(18), dp(18), dp(18), dp(18))
            for (v in views) addView(v, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }

    private fun openTelegram() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/soft_dial")))
        } catch (_: Exception) {
            status.text = "Telegram or a browser is not available"
        }
    }

    private fun updateServerButtons() {
        val on = gradient(Color.parseColor("#8B5CF6"), Color.parseColor("#22D3EE"), 14)
        val off = rounded(Color.parseColor("#0F1530"), 14, Color.parseColor("#2A3358"))
        server1Btn.background = if (serverIndex == 1) on else off
        server2Btn.background = if (serverIndex == 2) on else off
        server1Btn.setTextColor(if (serverIndex == 1) Color.WHITE else Color.parseColor("#8E98C0"))
        server2Btn.setTextColor(if (serverIndex == 2) Color.WHITE else Color.parseColor("#8E98C0"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        window.statusBarColor = Color.parseColor("#0B1020")
        window.navigationBarColor = Color.parseColor("#0B1020")

        // ---- Header ----
        val title = TextView(this).apply {
            text = "Soft Dial"
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 30f
        }
        val badge = TextView(this).apply {
            text = "PREMIUM SOON"
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 10f
            background = gradient(Color.parseColor("#F59E0B"), Color.parseColor("#F97316"), 10)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title)
            addView(badge, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(10) })
        }
        val tagline = TextView(this).apply {
            text = "Crystal-clear internet calling, made simple."
            setTextColor(Color.parseColor("#A5B4FC"))
            textSize = 14f
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#2A1F6B"), Color.parseColor("#0A3A5C"))
            ).apply { cornerRadius = dp(26).toFloat() }
            setPadding(dp(22), dp(24), dp(22), dp(24))
            addView(titleRow)
            addView(tagline, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }

        // ---- Status pill ----
        status = TextView(this).apply {
            text = "Not registered"
            setTextColor(Color.parseColor("#C7D2FE"))
            setTypeface(typeface, Typeface.BOLD)
            textSize = 14f
            gravity = Gravity.CENTER
            background = rounded(Color.parseColor("#1B2347"), 14)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        // ---- Server picker ----
        server1Btn = Button(this).apply {
            text = "Server 1"; isAllCaps = false; textSize = 15f; stateListAnimator = null
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { delayed(300) { serverIndex = 1; updateServerButtons() } }
        }
        server2Btn = Button(this).apply {
            text = "Server 2"; isAllCaps = false; textSize = 15f; stateListAnimator = null
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { delayed(300) { serverIndex = 2; updateServerButtons() } }
        }
        updateServerButtons()
        val serverRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(server1Btn, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
            addView(server2Btn, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        }

        fun label(t: String) = TextView(this).apply {
            text = t
            setTextColor(Color.parseColor("#8E98C0"))
            setTypeface(typeface, Typeface.BOLD)
            textSize = 12f
        }

        // ---- Fields ----
        user = styledField(EditText(this).apply { hint = "SIP username" })
        pass = styledField(EditText(this).apply {
            hint = "SIP password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        })
        server = styledField(EditText(this).apply { hint = "SIP server" })
        number = styledField(EditText(this).apply {
            hint = "Number to call"
            inputType = InputType.TYPE_CLASS_PHONE
            textSize = 20f
        })

        val register = styledButton("Register", Color.parseColor("#6366F1"), Color.parseColor("#8B5CF6")) {
            delayed(1000) { engine().register(user.text.toString(), pass.text.toString(), server.text.toString()) }
        }
        val call = styledButton("📞  Call", Color.parseColor("#16A34A"), Color.parseColor("#22C55E")) {
            delayed(1500) {
                val am = getSystemService(AUDIO_SERVICE) as AudioManager
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                if (!engine().call(number.text.toString(), server.text.toString())) {
                    status.text = "Could not start the call"
                    resetAudio()
                }
            }
        }
        val hang = styledButton("Hang up", Color.parseColor("#DC2626"), Color.parseColor("#F43F5E")) {
            delayed(700) { engine().hangUp(); resetAudio() }
        }
        val callRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(call, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
            addView(hang, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        }

        val accountCard = card(label("SERVER"), serverRow, label("ACCOUNT"), user, pass, server, register)
        val callCard = card(label("CALL"), number, callRow)

        // ---- Coming soon ----
        val soon = TextView(this).apply {
            text = "💎  Premium features are coming soon — more themes, a smoother experience " +
                "and a lot more. Stay tuned!"
            setTextColor(Color.parseColor("#FDE68A"))
            textSize = 13f
            background = rounded(Color.parseColor("#2A2210"), 18, Color.parseColor("#5B4A16"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        // ---- Telegram button ----
        val telegram = TextView(this).apply {
            text = "✈️  আমাদের গ্রুপে যুক্ত হন  •  Join us on Telegram"
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 15f
            gravity = Gravity.CENTER
            background = gradient(Color.parseColor("#229ED9"), Color.parseColor("#1D7FD1"), 18)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            startAnimation(AlphaAnimation(1f, 0.7f).apply {
                duration = 1000
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            })
            setOnClickListener { openTelegram() }
        }
        val telegramNote = TextView(this).apply {
            text = "নতুন আপডেট ও ফিচারের খবর সবার আগে পেতে"
            setTextColor(Color.parseColor("#8E98C0"))
            textSize = 12f
            gravity = Gravity.CENTER
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        fun add(v: android.view.View, bottom: Int = 14) =
            content.addView(v, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(bottom) })
        add(header)
        add(status)
        add(accountCard)
        add(callCard)
        add(soon)
        add(telegram, 6)
        add(telegramNote, 0)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1020"))
            isFillViewport = true
            addView(content)
        }
        setContentView(scroll)
    }

    private fun resetAudio() {
        try {
            (getSystemService(AUDIO_SERVICE) as AudioManager).mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        try { linphone?.hangUp(); pjsip?.hangUp() } catch (_: Exception) { }
        resetAudio()
        super.onDestroy()
    }
}
