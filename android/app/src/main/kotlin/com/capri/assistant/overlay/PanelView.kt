package com.capri.assistant.overlay

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.capri.assistant.engine.Capri
import com.capri.assistant.engine.Line
import com.capri.assistant.engine.Mood
import com.capri.assistant.ui.Neon
import com.capri.assistant.ui.Plate
import com.capri.core.Lang
import com.capri.core.Security
import com.capri.core.cut

// Her panel over any app: the chat, a status line that says what she's doing, Oui/Non when she asked before acting, and
// the 2.0 menu (Parler, Analyser, Presse-papiers, Rechercher, Étudier, Actions). Compact by default, full on demand.
@SuppressLint("ViewConstructor")
class PanelView(context: Context, private val capri: Capri, private val host: Host, private val screen: Boolean = false) : LinearLayout(context) {
    interface Host { fun close(); fun full(on: Boolean); fun typing(on: Boolean) }

    private val p = Neon.palette(context)
    private val font = Neon.font(context)
    private fun dp(v: Int) = Neon.dp(context, v.toFloat())
    private val log = LinearLayout(context).apply { orientation = VERTICAL }
    private val scroll = ScrollView(context).apply { addView(log); isVerticalScrollBarEnabled = false }
    private val status = text("", 12f, p.faint)
    private val modeChip = text("", 11f, p.onAccent)
    private val partial = text("", 14f, p.cyan).apply { visibility = GONE }
    private val confirm = row()
    private val clipRow = row()
    private val more = row()
    private val levels = row()
    private val input = EditText(context)
    private var full = screen || capri.prefs.panelFull

    init {
        orientation = VERTICAL
        background = if (screen) null else Plate(p.ground, p.pink, dp(14).toFloat(), dp(1).toFloat() + 0.5f, p.cyan)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        isFocusableInTouchMode = true

        val head = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text("CAPRI", 17f, p.pink, true).apply { letterSpacing = 0.18f })
        head.addView(modeChip.apply { setPadding(dp(8), dp(2), dp(8), dp(2)); visibility = GONE }, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        head.addView(View(context), LayoutParams(0, 1, 1f))
        if (!screen) head.addView(icon(if (full) "⤡" else "⤢") { full = !full; capri.prefs.panelFull = full; host.full(full); refill() })
        head.addView(icon("■") { capri.interrupt() })
        head.addView(icon("✕") { host.close() })
        addView(head)
        addView(status.apply { setPadding(0, dp(2), 0, dp(6)) })
        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(partial)
        addView(scrolled(confirm).apply { visibility = GONE })
        addView(scrolled(clipRow).apply { visibility = GONE })
        addView(scrolled(levels).apply { visibility = GONE })

        val menu = row()
        menu.addView(chip(Lang.pick("🎙 Talk", "🎙 Parler"), p.cyan, filled = true) { capri.listen(conversation = capri.prefs.keepListening) })
        menu.addView(chip(Lang.pick("Analyze", "Analyser"), p.pink) { capri.say(Lang.pick("Look at my screen and explain what you see.", "Regarde mon écran et explique-moi ce que tu vois.")) })
        menu.addView(chip(Lang.pick("Clipboard", "Presse-papiers"), p.cyan) { clipboard() })
        menu.addView(chip(Lang.pick("Search", "Rechercher"), p.cyan) { input.setText(Lang.pick("Search the web: ", "Cherche sur le web : ")); input.setSelection(input.text.length); focusInput() })
        menu.addView(chip(Lang.pick("Study", "Étudier"), p.cyan) { toggle(levels) })
        menu.addView(chip("Actions", p.pink) { toggle(more) })
        addView(scrolled(menu))

        levels.addView(chip(Lang.pick("Beginner", "Débutant"), p.cyan) { capri.say("mode étude débutant"); levels.parentView().visibility = GONE })
        levels.addView(chip(Lang.pick("Normal", "Normal"), p.cyan) { capri.say("mode étude normal"); levels.parentView().visibility = GONE })
        levels.addView(chip(Lang.pick("Advanced", "Avancé"), p.cyan) { capri.say("mode étude avancé"); levels.parentView().visibility = GONE })
        levels.addView(chip(Lang.pick("Quiz me", "Quiz"), p.pink) { capri.say(Lang.pick("Quiz me on what we're studying, one question at a time.", "Fais-moi un quiz sur ce qu'on étudie, une question à la fois.")) })
        levels.addView(chip(Lang.pick("Normal mode", "Mode normal"), p.faint) { capri.say("mode normal") })

        more.addView(chip(Lang.pick("Do it for me", "Fais-le pour moi"), p.pink, filled = true) { capri.say(Lang.pick("Do it for me.", "Fais-le pour moi.")) })
        more.addView(chip(Lang.pick("Where were we?", "On en était où ?"), p.cyan) { capri.say(Lang.pick("Where were we?", "On en était où ?")) })
        more.addView(chip(Lang.pick("Notifications", "Notifications"), p.cyan) { capri.say(Lang.pick("Summarize my notifications.", "Résume mes notifications.")) })
        more.addView(chip(Lang.pick("Photo", "Photo"), p.cyan) { capri.say(Lang.pick("Take a photo and tell me what it is.", "Prends une photo et dis-moi ce que c'est.")) })
        more.addView(chip(Lang.pick("Gaming", "Mode jeu"), p.cyan) { capri.say("mode jeu") })
        more.addView(chip(Lang.pick("My phone", "Mon téléphone"), p.cyan) { capri.say(Lang.pick("How's my phone doing?", "Comment va mon téléphone ?")) })
        more.addView(chip(Lang.pick("To my PC", "Vers mon PC"), p.cyan) { capri.say(Lang.pick("Send this conversation to my PC.", "Envoie cette conversation sur mon PC.")) })

        val bottom = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0) }
        input.apply {
            hint = Lang.pick("Write to Capri…", "Écris à Capri…")
            setHintTextColor(p.faint); setTextColor(p.text); typeface = font; textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4
            imeOptions = EditorInfo.IME_ACTION_SEND
            background = Plate(p.field, p.edge, dp(8).toFloat(), dp(1).toFloat())
            setPadding(dp(10), dp(9), dp(10), dp(9))
            setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { send(); true } else false }
            setOnFocusChangeListener { _, has -> host.typing(has) }
            setOnClickListener { focusInput() }
        }
        bottom.addView(input, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(chip("➤", p.pink, filled = true) { send() }, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(6) })
        addView(bottom)

        confirm.addView(chip(Lang.pick("YES, DO IT", "OUI, FAIS-LE"), p.cyan, filled = true) { capri.say(Lang.pick("yes", "oui")) })
        confirm.addView(chip(Lang.pick("NO", "NON"), p.hot) { capri.say(Lang.pick("no", "non")) })

        refill()
        mood(capri.mood, capri.moodDetail)
        mode(capri.prefs.mode)
        showConfirm(Security.pending())
    }

    private fun View.parentView(): View = (parent as? View) ?: this

    // Back closes the panel: Android 13+ sends it through the window's back dispatcher (predictive back), older ones as a key.
    private val back = android.window.OnBackInvokedCallback { host.close() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (android.os.Build.VERSION.SDK_INT >= 33 && !screen) findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, back)
    }

    override fun onDetachedFromWindow() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !screen) findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(back)
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!screen && event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) { host.close(); return true }
        return super.dispatchKeyEvent(event)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); typeface = if (bold) Typeface.create(font, Typeface.BOLD) else font
    }

    private fun row() = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    private fun scrolled(v: View) = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(v); setPadding(0, dp(5), 0, 0) }

    private fun chip(label: String, accent: Int, filled: Boolean = false, go: () -> Unit) = text(label, 13f, if (filled) p.onAccent else accent).apply {
        gravity = Gravity.CENTER
        setPadding(dp(11), dp(7), dp(11), dp(7))
        background = Plate(if (filled) accent else p.field, accent, dp(6).toFloat(), dp(1).toFloat())
        setOnClickListener { go() }
        layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(6) }
    }

    private fun icon(label: String, go: () -> Unit) = text(label, 17f, p.cyan).apply { setPadding(dp(10), dp(4), dp(10), dp(4)); setOnClickListener { go() } }

    private fun toggle(r: LinearLayout) { val box = r.parentView(); box.visibility = if (box.visibility == VISIBLE) GONE else VISIBLE }

    private fun focusInput() {
        host.typing(true)
        input.requestFocus()
        post { context.getSystemService(InputMethodManager::class.java).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun send() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        capri.say(t)
    }

    private fun bubbleFor(l: Line): View {
        val you = l.who == Line.YOU
        val sys = l.who == Line.SYS
        val t = text(l.text, if (sys) 12f else 15f, if (sys) p.faint else p.text).apply {
            setLineSpacing(0f, 1.1f)
            setTextIsSelectable(true)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            if (!sys) background = Plate(p.field, if (you) p.cyan else p.pink, dp(8).toFloat(), dp(1).toFloat())
        }
        return LinearLayout(context).apply {
            gravity = if (you) Gravity.END else Gravity.START
            setPadding(if (you) dp(36) else 0, dp(3), if (you) 0 else dp(36), dp(3))
            addView(t)
        }
    }

    fun refill() {
        log.removeAllViews()
        val shown = capri.lines.filter { it.text.isNotBlank() }.takeLast(if (full) 60 else 6)
        if (shown.isEmpty()) log.addView(bubbleFor(Line(Line.SYS, Lang.pick("Hold my bubble to talk, or write below. I only look at your screen when you ask.", "Garde le doigt sur ma bulle pour parler, ou écris en bas. Je ne regarde ton écran que si tu me le demandes."))))
        for (l in shown) log.addView(bubbleFor(l))
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    fun add(l: Line) {
        if (l.text.isBlank()) { refill(); return }
        partial.visibility = GONE
        val fresh = bubbleFor(l)
        log.addView(fresh)
        // Néon 2.0: a new line slides up into the conversation.
        if (com.capri.assistant.ui.Neon.soft) { fresh.alpha = 0f; fresh.translationY = dp(10).toFloat(); fresh.animate().alpha(1f).translationY(0f).setDuration(200L).start() }
        while (log.childCount > (if (full) 60 else 6)) log.removeViewAt(0)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        if (l.who == Line.CAPRI) showConfirm(Security.pending())
    }

    fun partial(text: String?) {
        if (text.isNullOrBlank()) { partial.visibility = GONE; return }
        partial.text = "« " + text.cut(140) + " »"
        partial.visibility = VISIBLE
    }

    fun mood(m: Mood, detail: String) {
        status.text = when (m) {
            Mood.LISTENING -> Lang.pick("Listening…", "J'écoute…")
            Mood.THINKING -> Lang.pick("Thinking…", "Je réfléchis…")
            Mood.SPEAKING -> Lang.pick("Talking — say \"wait\" or tap ■ to stop me", "Je parle — dis « attends » ou touche ■ pour m'arrêter")
            Mood.WORKING -> Lang.pick("Working on your screen — ■ stops me", "J'agis sur ton écran — ■ m'arrête")
            Mood.ERROR -> detail.cut(120)
            Mood.ASLEEP -> Lang.pick("Napping. Tap to wake me.", "Je fais la sieste. Touche-moi pour me réveiller.")
            else -> capri.brainLabel()
        }
        status.setTextColor(if (m == Mood.ERROR) p.hot else p.faint)
        if (m != Mood.LISTENING) partial.visibility = GONE
    }

    fun mode(m: String) {
        val label = when (m) { "study" -> "CAPRI STUDY"; "dev" -> "CAPRI DEV"; "gaming" -> "CAPRI GAMING"; else -> null }
        modeChip.visibility = if (label == null) GONE else VISIBLE
        modeChip.text = label ?: ""
        modeChip.background = Plate(p.cyan, p.cyan, dp(4).toFloat(), 0f)
    }

    fun showConfirm(on: Boolean) { confirm.parentView().visibility = if (on) VISIBLE else GONE }

    // The smart clipboard: while the panel has focus Android lets her read what they copied, then she offers what to do.
    fun clipboard() {
        val box = clipRow.parentView()
        if (box.visibility == VISIBLE) { box.visibility = GONE; return }
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString()?.trim() else null
        clipRow.removeAllViews()
        if (text.isNullOrEmpty()) {
            clipRow.addView(chip(Lang.pick("Read my clipboard", "Lire mon presse-papiers"), p.cyan) { capri.say(Lang.pick("read what I copied", "lis ce que j'ai copié")) })
        } else {
            val q = "« " + text.cut(4000) + " »"
            clipRow.addView(text(text.cut(40), 12f, p.faint).apply { setPadding(0, 0, dp(8), 0) })
            clipRow.addView(chip(Lang.pick("Translate", "Traduire"), p.cyan) { capri.say(Lang.pick("Translate this into English: ", "Traduis ça en français : ") + q, echo = Lang.pick("Translate what I copied", "Traduis ce que j'ai copié")) })
            clipRow.addView(chip(Lang.pick("Summarize", "Résumer"), p.cyan) { capri.say(Lang.pick("Summarize this: ", "Résume ça : ") + q, echo = Lang.pick("Summarize what I copied", "Résume ce que j'ai copié")) })
            clipRow.addView(chip(Lang.pick("Fix", "Corriger"), p.cyan) { capri.say(Lang.pick("Fix the spelling and grammar, keep my style: ", "Corrige l'orthographe et la grammaire, garde mon style : ") + q, echo = Lang.pick("Fix what I copied", "Corrige ce que j'ai copié")) })
            clipRow.addView(chip(Lang.pick("Explain", "Expliquer"), p.cyan) { capri.say(Lang.pick("Explain this simply: ", "Explique-moi ça simplement : ") + q, echo = Lang.pick("Explain what I copied", "Explique ce que j'ai copié")) })
            clipRow.addView(chip(Lang.pick("Reply", "Répondre"), p.pink) { capri.say(Lang.pick("Write a reply to this message for me: ", "Écris-moi une réponse à ce message : ") + q, echo = Lang.pick("Help me reply to what I copied", "Aide-moi à répondre à ce que j'ai copié")) })
        }
        box.visibility = VISIBLE
    }
}
