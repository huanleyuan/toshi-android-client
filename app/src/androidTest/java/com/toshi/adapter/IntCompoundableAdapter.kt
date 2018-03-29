package com.toshi.adapter

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import android.widget.TextView
import com.toshi.view.adapter.BaseCompoundableAdapter

class IntViewHolder(val view: ViewGroup): RecyclerView.ViewHolder(view) {

    val textView: TextView by lazy {
        val textView = TextView(view.context)
        view.addView(textView)
        textView
    }

    fun displayValue(value: Int) {
        textView.text = "$value"
    }
}

class IntCompoundableAdapter(ints: List<Int>): BaseCompoundableAdapter<IntViewHolder, Int>() {

    init {
        setItemList(ints)
    }

    override fun compoundableBindViewHolder(viewHolder: RecyclerView.ViewHolder, adapterIndex: Int) {
        val typedHolder = viewHolder as? IntViewHolder ?: return
        onBindViewHolder(typedHolder, adapterIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IntViewHolder {
        return IntViewHolder(parent)
    }

    override fun onBindViewHolder(holder: IntViewHolder, position: Int) {
        val int = itemAt(position)
        holder.displayValue(int)
    }
}