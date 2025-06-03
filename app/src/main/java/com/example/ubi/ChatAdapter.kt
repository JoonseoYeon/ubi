package com.example.ubi

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import android.widget.Button

class ChatAdapter(
    private val messages: List<CharSequence>,
    private val onItemClick: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    class MessageViewHolder(val button: Button) : RecyclerView.ViewHolder(button)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val button = Button(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return MessageViewHolder(button)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.button.text = messages[position]
        holder.button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 50f)
        holder.button.setOnClickListener {
            onItemClick?.invoke(position)
        }
    }

    override fun getItemCount(): Int = messages.size
}