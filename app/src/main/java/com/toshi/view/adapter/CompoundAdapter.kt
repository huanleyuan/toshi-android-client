package com.toshi.view.adapter

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup

/*
An adapter which takes an array of adapters, so logic for things with different types can be split into
individual adapters.
*/
class CompoundAdapter<T: RecyclerView.ViewHolder, U: RecyclerView.Adapter<T>>(
        var adapters: List<U>
): RecyclerView.Adapter<T>() {

    private fun totalItemsBeforeSection(sectionIndex: Int): Int {
        when (sectionIndex) {
            in Int.MIN_VALUE..-1 -> throw AssertionError("No sections at negative indexs!")
            0 ->  /* There wouldn't be any items before section 0 */ return 0
            in 1..(sectionIndex - 1) -> {
                val previousAdapters = adapters.subList(0, sectionIndex)
                return previousAdapters.fold(0, { acc, adapter -> return acc + adapter.itemCount })
            }
            in sectionIndex..Int.MAX_VALUE -> throw AssertionError("Looking for section at $sectionIndex but there are only $itemCount sections")
        }

        // Even though the `when` should theoretically cover all possible values of Int, we apparently still need to do this:
        return -1
    }

    private fun compoundIndexOfItem(adapter: U, positionInAdapter: Int): Int {
        val sectionIndex = adapters.indexOf(adapter)
        val previousItems = totalItemsBeforeSection(sectionIndex)
        return previousItems + positionInAdapter
    }

    private fun sectionIndexOfItem(adapter: U, compoundIndex: Int): Int {
        val sectionIndex = adapters.indexOf(adapter)
        val previousItems = totalItemsBeforeSection(sectionIndex)
        return compoundIndex - previousItems
    }

    // Add observers when data set changes
    private fun currentSectionAdapter(position: Int): U {
        var previousCount = 0
        adapters.forEach { adapter ->
            if (position >= (previousCount + adapter.itemCount)) {
                previousCount += adapter.itemCount
            } else {
                return adapter
            }
        }

        // If we've gotten here, we've gone through all the adapters and haven't found anything
        throw AssertionError("No adapter for position $position")
    }

    override fun getItemCount(): Int {
        return adapters.fold(0, { acc, incoming -> acc + incoming.itemCount })
    }

    override fun getItemViewType(position: Int): Int {
        // Use the index of the section adapter to route the view type to the proper place
        return adapters.indexOf(currentSectionAdapter(position))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): T {
        val sectionAdapter = adapters[viewType]

        // NOTE: Each section should only handle one particular type of item, or this is gonna cause some screwiness.
        return sectionAdapter.onCreateViewHolder(parent, 0)
    }


    override fun onBindViewHolder(holder: T, position: Int) {
        val sectionAdapter = currentSectionAdapter(position)
        val sectionIndex = adapters.indexOf(sectionAdapter)

        when (sectionIndex) {
            0 -> sectionAdapter.onBindViewHolder(holder, position)
            in (1..(itemCount - 1)) -> sectionAdapter.onBindViewHolder(holder, sectionIndexOfItem(sectionAdapter, position))
            else -> throw AssertionError("Section index $sectionIndex out of bounds for adapter with $itemCount sections")
        }
    }


    fun notifyItemRemoved(adapter: U, removedIndex: Int) {
        notifyItemRemoved(compoundIndexOfItem(adapter, removedIndex))
    }

    fun notifyItemChanged(adapter: U, changeIndex: Int) {
        notifyItemChanged(compoundIndexOfItem(adapter, changeIndex))
    }

//    public fun notify
}