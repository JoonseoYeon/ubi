package com.example.ubi

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.*
import android.text.style.StrikethroughSpan
import android.util.Log
import android.view.KeyEvent
import android.view.View
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<CharSequence>()
    private val lockedMessages = mutableListOf<Triple<String, String, Boolean>>()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val POSTBOX_LATITUDE = 36.0126913
    private val POSTBOX_LONGITUDE = 129.3253946
    private val POSTBOX_RADIUS_METERS = 100.0

    private var pendingUnlockIndex: Int? = null
    private val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val chatRecyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
        val editText = findViewById<EditText>(R.id.messageInput)
        val sendButton = findViewById<Button>(R.id.sendButton)
        val seeButton = findViewById<Button>(R.id.button)

        val font = ResourcesCompat.getFont(this, R.font.myfont1regular)
        editText.typeface = font

        seeButton.setOnClickListener {
            messages.clear()
            lockedMessages.forEachIndexed { index, _ ->
                messages.add(SpannableString("${index + 1}. message"))
            }
            chatAdapter.notifyDataSetChanged()
        }

        sendButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
                return@setOnClickListener
            }

            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null && isWithinPostbox(location)) {
                    if (spannableBuilder.isNotEmpty()) {
                        val builder = AlertDialog.Builder(this)
                        builder.setTitle("Enter unlock condition")

                        val conditionInput = EditText(this)
                        conditionInput.hint = "ex> flower"
                        val imageCheckBox = CheckBox(this).apply { text = "Picture condition?" }

                        val layout = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(conditionInput)
                            addView(imageCheckBox)
                        }

                        builder.setView(layout)
                        builder.setPositiveButton("ok") { dialog, _ ->
                            val condition = conditionInput.text.toString()
                            val encoded = Base64.encodeToString(spannableBuilder.toString().toByteArray(), Base64.DEFAULT)
                            lockedMessages.add(Triple(encoded, condition, imageCheckBox.isChecked))
                            Toast.makeText(this, "Message sent with condition: $condition", Toast.LENGTH_SHORT).show()
                            resetInput(editText)
                            messages.clear()
                            lockedMessages.forEachIndexed { index, _ ->
                                messages.add(SpannableString("${index + 1}. message (Locked)"))
                            }
                            chatAdapter.notifyDataSetChanged()
                            dialog.dismiss()
                        }
                        builder.setNegativeButton("cancel") { dialog, _ -> dialog.cancel() }
                        builder.show()
                    }
                } else {
                    Toast.makeText(this, "You should go to the postbox", Toast.LENGTH_SHORT).show()
                }
            }
        }

        chatAdapter = ChatAdapter(messages) { position ->
            val (encoded, condition, isImage) = lockedMessages[position]
            if (!isImage) {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Enter unlock condition")
                val input = EditText(this)
                builder.setView(input)
                builder.setPositiveButton("ok") { dialog, _ ->
                    val userInput = input.text.toString()
                    if (userInput == condition) {
                        val decoded = String(Base64.decode(encoded, Base64.DEFAULT))
                        messages[position] = SpannableString(decoded)
                        chatAdapter.notifyItemChanged(position)
                    } else {
                        Toast.makeText(this, "No match", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }
                builder.setNegativeButton("cancel") { dialog, _ -> dialog.cancel() }
                builder.show()
            } else {
                pendingUnlockIndex = position
                val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(takePictureIntent, 100)
            }
        }

        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = chatAdapter

        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s == null || s.length <= lastInputLength || suppressTextChange) return

                suppressTextChange = true

                val now = System.currentTimeMillis()
                val delay = now - lastInputTime
                lastInputTime = now

                val newChar = s[lastInputLength]
                val probabilities = computeFontProbabilities(delay)
                val fontIndex = pickFontIndex(probabilities)
                val typeface = ResourcesCompat.getFont(this@MainActivity, fontIds[fontIndex])!!

                spannableBuilder.append(
                    newChar.toString(),
                    CustomTypefaceSpan(typeface),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                lastInputLength = spannableBuilder.length
                editText.setText(spannableBuilder)
                editText.setSelection(spannableBuilder.length)

                suppressTextChange = false
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        editText.setOnKeyListener(View.OnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                if (spannableBuilder.isNotEmpty()) {
                    lastCrossedOutRange?.let { range ->
                        crossoutPressCount++
                        val length = range.last - range.first
                        if (crossoutPressCount >= length) {
                            spannableBuilder.delete(range.first, range.last + 1)
                            lastCrossedOutRange = null
                            crossoutPressCount = 0
                        }
                        editText.setText(spannableBuilder)
                        editText.setSelection(spannableBuilder.length)
                        lastInputLength = spannableBuilder.length
                        return@OnKeyListener true
                    }
                    val fullText = spannableBuilder.toString()
                    val end = spannableBuilder.length
                    val start = fullText.lastIndexOfAny(charArrayOf(' ', '\n'), end - 1).let {
                        if (it == -1) 0 else it + 1
                    }
                    if (start == end) {
                        spannableBuilder.delete(end - 1, end)
                        editText.setText(spannableBuilder)
                        editText.setSelection(spannableBuilder.length)
                        lastInputLength = spannableBuilder.length
                        return@OnKeyListener true
                    }
                    spannableBuilder.getSpans(start, end, CrossoutSpan::class.java).forEach {
                        spannableBuilder.removeSpan(it)
                    }
                    spannableBuilder.setSpan(CrossoutSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    lastCrossedOutRange = start until end
                    crossoutPressCount = 1
                    editText.setText(spannableBuilder)
                    editText.setSelection(spannableBuilder.length)
                    return@OnKeyListener true
                }
            }
            false
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            val position = pendingUnlockIndex ?: return
            val (encoded, condition, _) = lockedMessages[position]
            val photo = data?.extras?.get("data") as? Bitmap ?: return

            val stream = ByteArrayOutputStream()
            photo.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            val byteArray = stream.toByteArray()

            val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("condition", condition)
                .addFormDataPart(
                    "image", "photo.jpg",
                    byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("https://0716-34-124-233-178.ngrok-free.app/analyze")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Server error", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string()?.trim()
                    Log.d("SERVER_RESPONSE", "응답: $body")
                    val matched = body == "true"

                    runOnUiThread {
                        if (matched) {
                            val decoded = String(Base64.decode(encoded, Base64.DEFAULT))
                            messages[position] = SpannableString(decoded)
                            chatAdapter.notifyItemChanged(position)
                        } else {
                            Toast.makeText(this@MainActivity, "Wrong condition", Toast.LENGTH_SHORT).show()
                        }
                        pendingUnlockIndex = null
                    }
                }
            })
        }
    }

    private fun isWithinPostbox(location: Location): Boolean {
        val result = FloatArray(1)
        Location.distanceBetween(
            location.latitude, location.longitude,
            POSTBOX_LATITUDE, POSTBOX_LONGITUDE,
            result
        )
        return result[0] <= POSTBOX_RADIUS_METERS
    }

    private fun resetInput(editText: EditText) {
        spannableBuilder.clear()
        editText.setText("")
        editText.setSelection(0)
        lastInputLength = 0
        lastCrossedOutRange = null
        crossoutPressCount = 0
    }

    private val spannableBuilder = SpannableStringBuilder()
    private var lastInputLength = 0
    private var suppressTextChange = false
    private var lastCrossedOutRange: IntRange? = null
    private var crossoutPressCount = 0
    private var lastInputTime = System.currentTimeMillis()

    private val fontIds = listOf(
        R.font.myfont1regular,
        R.font.myfont2regular,
        R.font.myfont3regular,
        R.font.myfont4regular
    )

    private fun computeFontProbabilities(delay: Long): List<Float> {
        val clampedDelay = delay.coerceIn(100L, 500L)
        val fastness = (500f - clampedDelay) / 400f
        val prob12 = 0.05f + (0.90f * (1 - fastness))
        val prob34 = 1.0f - prob12
        val prob1 = prob12 * 0.5f
        val prob2 = prob12 * 0.5f
        val prob3 = prob34 * 0.5f
        val prob4 = prob34 * 0.5f
        return listOf(prob1, prob2, prob3, prob4)
    }

    private fun pickFontIndex(probabilities: List<Float>): Int {
        val rand = Math.random().toFloat()
        var cumulative = 0f
        for ((i, p) in probabilities.withIndex()) {
            cumulative += p
            if (rand < cumulative) return i
        }
        return probabilities.lastIndex
    }
}
