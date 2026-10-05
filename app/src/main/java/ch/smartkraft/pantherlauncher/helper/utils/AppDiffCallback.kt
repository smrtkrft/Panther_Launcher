package ch.smartkraft.pantherlauncher.helper.utils

import androidx.recyclerview.widget.DiffUtil
import ch.smartkraft.pantherlauncher.data.AppListItem

// DiffUtil for efficiently handling list updates
class AppDiffCallback(
    private val oldList: List<AppListItem>,
    private val newList: List<AppListItem>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].settingsKey == newList[newItemPosition].settingsKey
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}