/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.metroboard.ime.keyboard.KeyAction
import com.metroboard.ime.keyboard.KeySpec
import com.metroboard.ime.util.KeyboardColors
import com.metroboard.ime.util.dp

class EmojiKeyboardView(
    context: Context,
    private val onEmojiSelected: (String) -> Unit,
    private val onCloseEmoji: () -> Unit,
    private val onBackspace: () -> Unit
) : LinearLayout(context) {

    private val prefs = context.getSharedPreferences("emoji_prefs", Context.MODE_PRIVATE)
    private val emojiGrid = GridLayout(context)
    private val scrollContainer = ScrollView(context)
    private val tabViews = mutableListOf<TextView>()
    private var currentCategoryIndex = 0

    private val CATEGORIES = listOf(
        "🕒" to "Recents",
        "😀" to "Smileys",
        "👋" to "People",
        "🐶" to "Animals",
        "🍕" to "Food",
        "⚽" to "Activity",
        "🚗" to "Travel",
        "💡" to "Objects",
        "❤️" to "Symbols",
        "🚩" to "Flags"
    )

    init {
        orientation = VERTICAL
        setBackgroundColor(KeyboardColors.background)
        setPadding(context.dp(4), context.dp(2), context.dp(4), context.dp(2))

        // 1. Category Bar
        val categoryBar = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val tabRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
        }

        CATEGORIES.forEachIndexed { index, (icon, _) ->
            val tab = TextView(context).apply {
                text = icon
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding(context.dp(10), context.dp(6), context.dp(10), context.dp(6))
                setOnClickListener {
                    selectCategory(index)
                }
            }
            tabViews.add(tab)
            tabRow.addView(tab)
        }
        categoryBar.addView(tabRow)
        addView(categoryBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // 2. Emoji Scroll Container with GridLayout
        emojiGrid.apply {
            columnCount = 7
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        scrollContainer.apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(emojiGrid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(scrollContainer, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // 3. Bottom Control Row (ABC, Space, Backspace)
        val bottomRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(context.dp(2), context.dp(4), context.dp(2), context.dp(4))
        }

        val abcBtn = KeyButton(context, KeyAction.LETTERS).apply {
            text = "&123"
            textSize = 16f
            setOnClickListener { onCloseEmoji() }
        }
        bottomRow.addView(abcBtn, LayoutParams(0, context.dp(44), 1.5f).apply {
            setMargins(context.dp(2), 0, context.dp(2), 0)
        })

        val spaceBtn = KeyButton(context, KeyAction.SPACE).apply {
            text = " "
            setOnClickListener { onEmojiSelected(" ") }
        }
        bottomRow.addView(spaceBtn, LayoutParams(0, context.dp(44), 4.5f).apply {
            setMargins(context.dp(2), 0, context.dp(2), 0)
        })

        val delBtn = KeyButton(context, KeyAction.DELETE).apply {
            setOnClickListener { onBackspace() }
        }
        bottomRow.addView(delBtn, LayoutParams(0, context.dp(44), 1.5f).apply {
            setMargins(context.dp(2), 0, context.dp(2), 0)
        })

        addView(bottomRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // Select initial category
        val initialCategory = if (getRecents().isNotEmpty()) 0 else 1
        selectCategory(initialCategory)
    }

    private fun selectCategory(index: Int) {
        currentCategoryIndex = index
        tabViews.forEachIndexed { i, view ->
            val isSelected = i == index
            view.setBackgroundColor(if (isSelected) Color.parseColor("#333333") else Color.TRANSPARENT)
            if (isSelected) {
                view.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#333333"))
                    setStroke(context.dp(2), KeyboardColors.accent)
                }
            } else {
                view.background = ColorDrawable(Color.TRANSPARENT)
            }
        }

        populateGrid(index)
        scrollContainer.scrollTo(0, 0)
    }

    private fun populateGrid(categoryIndex: Int) {
        emojiGrid.removeAllViews()
        val emojis = if (categoryIndex == 0) {
            getRecents().ifEmpty { EMOJI_SETS[1] }
        } else {
            EMOJI_SETS.getOrElse(categoryIndex) { emptyList() }
        }

        val cellWidth = context.resources.displayMetrics.widthPixels / 7
        val cellHeight = context.dp(46)

        emojis.forEach { emoji ->
            val tv = TextView(context).apply {
                text = emoji
                textSize = 24f
                gravity = Gravity.CENTER
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    recordRecent(emoji)
                    onEmojiSelected(emoji)
                }
                if (isSkinToneSupported(emoji)) {
                    setOnLongClickListener {
                        showSkinTonePopup(this, emoji)
                        true
                    }
                }
            }
            val params = GridLayout.LayoutParams().apply {
                width = cellWidth
                height = cellHeight
            }
            emojiGrid.addView(tv, params)
        }
    }

    private var skinTonePopup: PopupWindow? = null

    private fun showSkinTonePopup(anchor: View, baseEmoji: String) {
        skinTonePopup?.dismiss()
        val variants = getSkinToneVariants(baseEmoji)
        val popupView = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
            background = GradientDrawable().apply {
                setColor(if (KeyboardColors.isDarkTheme) Color.rgb(28, 28, 28) else Color.rgb(240, 240, 240))
                setStroke(context.dp(1), KeyboardColors.accent)
                cornerRadius = context.dp(4).toFloat()
            }
        }

        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = context.dp(8).toFloat()
        }
        skinTonePopup = popup

        variants.forEach { variant ->
            val tv = TextView(context).apply {
                text = variant
                textSize = 24f
                gravity = Gravity.CENTER
                setPadding(context.dp(6), context.dp(4), context.dp(6), context.dp(4))
                background = KeyboardColors.keyBackground()
                setOnClickListener {
                    recordRecent(variant)
                    onEmojiSelected(variant)
                    popup.dismiss()
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(context.dp(2), 0, context.dp(2), 0)
            }
            popupView.addView(tv, lp)
        }

        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popW = popupView.measuredWidth
        val popH = popupView.measuredHeight

        val xOff = (anchor.width - popW) / 2
        val yOff = -popH - context.dp(8)

        popup.showAsDropDown(anchor, xOff, yOff)
    }

    private fun getRecents(): List<String> {
        val raw = prefs.getString("recents", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(",").filter { it.isNotEmpty() }
    }

    private fun recordRecent(emoji: String) {
        val current = getRecents().toMutableList()
        current.remove(emoji)
        current.add(0, emoji)
        val trimmed = current.take(28)
        prefs.edit().putString("recents", trimmed.joinToString(",")).apply()
    }

    companion object {
        private val FITZPATRICK_MODIFIERS = listOf(
            "\uD83C\uDFFB", // Light
            "\uD83C\uDFFC", // Medium-Light
            "\uD83C\uDFFD", // Medium
            "\uD83C\uDFFE", // Medium-Dark
            "\uD83C\uDFFF"  // Dark
        )

        private val SKIN_TONE_BASES = setOf(
            "👋", "🤚", "🖐️", "✋", "🖖", "🫱", "🫲", "🫳", "🫴", "👌", "🤌", "🤏", "✌️", "🤞", "🫰", "🤟",
            "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "🫵", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏",
            "🙌", "🫶", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦵", "🦶", "👂", "🦻", "👃", "👶",
            "🧒", "👦", "👧", "🧑", "👱", "👨", "🧔", "👩", "🧓", "👴", "👵", "👮", "🕵️", "💂", "👷", "🤴",
            "👸", "🦸", "🦹", "🧙", "🧚", "🏋️", "🤸", "🤺", "⛹️", "🤾", "🧗", "🧘", "🏄", "🏊", "🤽", "🚣", "🏇", "🚴", "🚵"
        )

        fun isSkinToneSupported(emoji: String): Boolean {
            val clean = emoji.replace("\uFE0F", "")
            return SKIN_TONE_BASES.contains(emoji) || SKIN_TONE_BASES.any { it.replace("\uFE0F", "") == clean }
        }

        fun getSkinToneVariants(base: String): List<String> {
            val clean = base.replace("\uFE0F", "")
            return listOf(base) + FITZPATRICK_MODIFIERS.map { mod -> "$clean$mod" }
        }

        private val EMOJI_SETS: List<List<String>> = listOf(
            emptyList(), // 0: Recents dynamically populated
            // 1: Smileys & Emotion
            listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "☺️", "😊", "😇", "🙂", "🙃", "😉", "😌",
                "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥸",
                "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢",
                "😭", "😮‍💨", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗",
                "🤔", "🫣", "🤭", "🫢", "🫡", "🤫", "🫠", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧",
                "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕"
            ),
            // 2: People & Gestures
            listOf(
                "👋", "🤚", "🖐️", "✋", "🖖", "🫱", "🫲", "🫳", "🫴", "👌", "🤌", "🤏", "✌️", "🤞", "🫰", "🤟",
                "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "🫵", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏",
                "🙌", "🫶", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻",
                "👃", "🧠", "🫀", "🫁", "🦷", "🦴", "👀", "👁️", "👅", "👄", "👶", "🧒", "👦", "👧", "🧑", "👱",
                "👨", "🧔", "👩", "🧓", "👴", "👵", "👮", "🕵️", "💂", "👷", "🤴", "👸", "🦸", "🦹", "🧙", "🧚"
            ),
            // 3: Animals & Nature
            listOf(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️", "🐨", "🐯", "🦁", "🐮", "🐷", "🐽", "🐸",
                "🐵", "🐒", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄",
                "🐝", "🐛", "🦋", "🐌", "🐞", "🐜", "🦟", "🦗", "🕷️", "🦂", "🐢", "🐍", "🦎", "🦖", "🦕", "🐙",
                "🦑", "🦐", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🦭", "🐊", "🐅", "🐆", "🦓",
                "🦍", "🦧", "🐘", "🦛", "🦏", "🐪", "🐫", "🦒", "🦘", "🦬", "🐃", "🐂", "🐄", "🐎", "🐖", "🐏"
            ),
            // 4: Food & Drink
            listOf(
                "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥",
                "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶️", "🌽", "🥕", "🧄", "🧅", "🥔", "🍠", "🥐", "🥯",
                "🍞", "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥞", "🧇", "🥓", "🥩", "🍗", "🍖", "🌭", "🍔", "🍟",
                "🍕", "🥪", "🥙", "🧆", "🌮", "🌯", "🥗", "🥘", "🍝", "🍜", "🍲", "🍛", "🍣", "🍱", "🥟", "🍤",
                "🍙", "🍚", "🍘", "🍥", "🍧", "🍨", "🍦", "🥧", "🧁", "🍰", "🎂", "🍮", "🍭", "🍬", "🍫", "🍿",
                "🍩", "🍪", "☕", "🍵", "🧃", "🥤", "🧋", "🍺", "🍻", "🥂", "🍷", "🥃", "🍸", "🍹", "🍾", "🧊"
            ),
            // 5: Activity & Sports
            listOf(
                "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍",
                "🏏", "🥅", "⛳", "🏹", "🎣", "🤿", "🥊", "🥋", "🛹", "🛼", "🛷", "⛸️", "🎿", "🏂", "🏋️", "🤼",
                "🤸", "🤺", "⛹️", "🤾", "🧗", "🧘", "🏄", "🏊", "🤽", "🚣", "🏇", "🚴", "🚵", "🏆", "🥇", "🥈",
                "🥉", "🏅", "🎖️", "🎫", "🎟️", "🎪", "🎭", "🎨", "🎬", "🎤", "🎧", "🎼", "🎹", "🥁", "🎷", "🎺",
                "🎸", "🎻", "🎲", "♟️", "🎯", "🎳", "🎮", "🎰", "🧩"
            ),
            // 6: Travel & Places
            listOf(
                "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🛴", "🚲",
                "🛵", "🏍️", "🚨", "🚔", "🚍", "🚘", "🚖", "🚡", "🚠", "🚟", "🚃", "🚋", "🚞", "🚝", "🚄", "🚅",
                "🚈", "🚂", "🚆", "🚇", "🚊", "🚉", "🚁", "🛩️", "✈️", "🛫", "🛬", "🛰️", "🚀", "🛸", "🛶", "⛵",
                "🚤", "🛥️", "🛳️", "⛴️", "🚢", "⚓", "⛽", "🚧", "🚦", "🚥", "🗺️", "🗿", "🗽", "🗼", "🏰", "🏯",
                "🏟️", "🎡", "🎢", "🏖️", "🏝️", "🏜️", "🌋", "⛰️", "🏔️", "🗻", "🏕️", "⛺", "🏠", "🏡", "🏢", "🏬"
            ),
            // 7: Objects
            listOf(
                "💡", "🔦", "🕯️", "🧯", "💵", "💴", "💶", "💷", "🪙", "💰", "💳", "💎", "⚖️", "🪜", "🧰", "🔧",
                "🔨", "⚒️", "🛠️", "⛏️", "🪚", "🔩", "⚙️", "🧱", "⛓️", "🧲", "💣", "🧨", "🪓", "🔪", "🗡️", "⚔️",
                "🛡️", "⚰️", "🔮", "📿", "🧿", "💈", "🔭", "🔬", "🩹", "🩺", "💊", "💉", "🩸", "🧬", "🧪", "🌡️",
                "🧹", "🪠", "🧺", "🧻", "🚽", "🚿", "🛁", "🧼", "🪥", "🪒", "🧴", "🛎️", "🔑", "🗝️", "🚪", "🪑",
                "🛋️", "🛏️", "🖼️", "🛍️", "🛒", "🎁", "🎈", "🎉", "🎊", "✉️", "📩", "📦", "📫", "📮", "📜", "📄",
                "📅", "📆", "📁", "📂", "📰", "📖", "📚", "🔖", "📎", "📏", "📐", "📌", "✂️", "✏️", "✒️", "🔒"
            ),
            // 8: Symbols
            listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🤎", "🖤", "🤍", "💔", "❣️", "💕", "💞", "💓", "💗", "💖",
                "💘", "💝", "☮️", "✝️", "☪️", "🕉️", "☸️", "✡️", "☯️", "♈", "♉", "♊", "♋", "♌", "♍", "♎",
                "♏", "♐", "♑", "♒", "♓", "🆔", "⚛️", "☢️", "☣️", "📴", "📳", "🈶", "🈚", "✴️", "🆚", "💮", "🉐",
                "❌", "⭕", "🛑", "⛔", "🚫", "💯", "💢", "♨️", "❗", "❕", "❓", "❔", "‼️", "⁉️", "⚠️", "♻️",
                "✅", "❇️", "✳️", "❎", "🌐", "Ⓜ️", "🌀", "💤", "🏧", "♿", "🚹", "🚺", "🚻", "🎦", "📶", "🆗",
                "🆙", "🆒", "🆕", "🆓", "0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟",
                "▶️", "⏸️", "⏹️", "⏭️", "⏮️", "⏩", "⏪", "➡️", "⬅️", "⬆️", "⬇️", "🎵", "🎶", "➕", "➖", "➗", "✖️"
            ),
            // 9: Flags
            listOf(
                "🚩", "🏳️", "🏴", "🏁", "🏳️‍🌈", "🏳️‍⚧️", "🏴‍☠️", "🇪🇸", "🇺🇸", "🇲🇽", "🇪🇨", "🇨🇴", "🇦🇷", "🇨🇱", "🇵🇪", "🇬🇧",
                "🇫🇷", "🇩🇪", "🇮🇹", "🇧🇷", "🇨🇦", "🇯🇵", "🇰🇷", "🇨🇳", "🇮🇳", "🇷🇺", "🇦🇺", "🇳🇿", "🇿🇦", "🇺🇾", "🇻🇪", "🇵🇾",
                "🇧🇴", "🇵🇦", "🇨🇷", "🇳🇮", "🇭🇳", "🇸🇻", "🇬🇹", "🇨🇺", "🇩🇴", "🇵🇷", "🇨🇭", "🇦🇹", "🇳🇱", "🇧🇪", "🇵🇹", "🇬🇷"
            )
        )
    }
}
