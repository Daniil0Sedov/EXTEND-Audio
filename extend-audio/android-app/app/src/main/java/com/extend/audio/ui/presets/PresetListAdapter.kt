package com.extend.audio.ui.presets

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.extend.audio.R
import com.extend.audio.databinding.ItemPresetBinding
import com.extend.audio.domain.model.Preset
import com.extend.audio.ui.common.formatBandsSummary

class PresetListAdapter(
    private val onApplyClicked: (Preset) -> Unit,
    private val onEditClicked: (Preset) -> Unit,
    private val onDeleteClicked: (Preset) -> Unit,
) : RecyclerView.Adapter<PresetListAdapter.PresetViewHolder>() {

    private var items: List<Preset> = emptyList()
    private var activePresetId: String? = null

    fun submitData(presets: List<Preset>, activePresetId: String?) {
        items = presets
        this.activePresetId = activePresetId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return PresetViewHolder(ItemPresetBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: PresetViewHolder, position: Int) {
        holder.bind(items[position], items[position].id == activePresetId)
    }

    override fun getItemCount(): Int = items.size

    inner class PresetViewHolder(
        private val binding: ItemPresetBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(preset: Preset, isActive: Boolean) {
            binding.textPresetName.text = preset.name
            binding.textPresetModel.text =
                binding.root.context.getString(R.string.headphone_model, preset.headphoneModel)
            binding.textPresetBands.text = formatBandsSummary(preset.bands)
            binding.textActiveBadge.isVisible = isActive
            binding.buttonApply.setOnClickListener { onApplyClicked(preset) }
            binding.buttonEdit.setOnClickListener { onEditClicked(preset) }
            binding.buttonDelete.setOnClickListener { onDeleteClicked(preset) }
        }
    }
}
