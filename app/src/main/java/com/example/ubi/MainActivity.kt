package com.example.ubi;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.res.ResourcesCompat
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.Editable
import android.text.style.StrikethroughSpan
import android.util.Log
import android.view.KeyEvent

import android.view.View

class MainActivity : AppCompatActivity() {
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<CharSequence>()
    fun computeFontProbabilities(delay: Long): List<Float> {
        val clampedDelay = delay.coerceIn(100L, 500L)

        // 빠를수록 weight = 1, 느릴수록 weight = 0
        val fastness = (500f - clampedDelay) / 400f  // 100ms → 1.0, 500ms → 0.0

        val prob12 = 0.05f + (0.90f * (1 - fastness))  // 100ms → 5%, 500ms → 95%
        val prob34 = 1.0f - prob12                    // 나머지

        val prob1 = prob12 * 0.5f
        val prob2 = prob12 * 0.5f
        val prob3 = prob34 * 0.5f
        val prob4 = prob34 * 0.5f

        return listOf(prob1, prob2, prob3, prob4)
    }

    fun pickFontIndex(probabilities: List<Float>): Int {
        val rand = Math.random().toFloat()
        var cumulative = 0f
        for ((i, p) in probabilities.withIndex()) {
            cumulative += p
            if (rand < cumulative) return i
        }
        return probabilities.lastIndex
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val chatRecyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
        val editText = findViewById<EditText>(R.id.messageInput)
        val font = ResourcesCompat.getFont(this, R.font.myfont1regular)
        editText.typeface = font
        val sendButton = findViewById<Button>(R.id.sendButton)

        val fontIds = listOf(
            R.font.myfont1regular, // 정성 4점
            R.font.myfont2regular, // 정성 5점
            R.font.myfont3regular, // 정성 1점
            R.font.myfont4regular  // 정성 2점
        )
        val sincerity = listOf(4, 5, 1, 2)
        var lastInputTime = System.currentTimeMillis()
        val spannableBuilder = SpannableStringBuilder()

        editText.addTextChangedListener(object : TextWatcher {
            private var previousTextLength = 0

            override fun afterTextChanged(s: Editable?) {
                if (s == null || s.isEmpty()) return

                val currentLength = s.length

                // 👇 글자 추가일 때만 처리
                if (currentLength > previousTextLength) {
                    val now = System.currentTimeMillis()
                    val delay = now - lastInputTime
                    lastInputTime = now

                    val probabilities = computeFontProbabilities(delay)
                    Log.d("FontProb", "delay=$delay ms → probs=$probabilities")
                    val fontIndex = pickFontIndex(probabilities)
                    val typeface = ResourcesCompat.getFont(this@MainActivity, fontIds[fontIndex])!!

                    val lastChar = s.last()
                    spannableBuilder.append(
                        lastChar.toString(),
                        CustomTypefaceSpan(typeface),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    editText.removeTextChangedListener(this)
                    editText.setText(spannableBuilder)
                    editText.setSelection(spannableBuilder.length)
                    editText.addTextChangedListener(this)
                }

                previousTextLength = currentLength
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        editText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                if (spannableBuilder.isNotEmpty()) {
                    val fullText = spannableBuilder.toString()
                    val end = spannableBuilder.length

                    // 2. 마지막 공백 또는 줄바꿈 기준으로 단어 시작점 찾기
                    val start = fullText.lastIndexOfAny(charArrayOf(' ', '\n'), end - 1).let {
                        if (it == -1) 0 else it + 1
                    }

                    // 3. 해당 범위에 CrossoutSpan 적용 (X 긋기)
                    spannableBuilder.setSpan(
                        CrossoutSpan(),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    // 4. UI 반영
                    editText.setText(spannableBuilder)
                    editText.setSelection(spannableBuilder.length)
                }
                true // ← 백스페이스 이벤트 소비했음
            } else {
                false
            }
        }
        chatAdapter = ChatAdapter(messages)
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = chatAdapter

        sendButton.setOnClickListener {
            if (spannableBuilder.isNotEmpty()) {
                messages.add(SpannableString(spannableBuilder))
                chatAdapter.notifyItemInserted(messages.size - 1)
                chatRecyclerView.scrollToPosition(messages.size - 1)
                spannableBuilder.clear()
                editText.setText("")
            }
        }
    }

}