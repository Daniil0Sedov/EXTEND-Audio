package com.extend.audio.ui.presets

/** Адаптер списка сохранённых пресетов с кнопками применить, изменить и удалить. */

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.extend.audio.R
import com.extend.audio.databinding.ItemPresetBinding
import com.extend.audio.domain.model.Preset
import com.extend.audio.ui.common.formatBandsSummary

/** Адаптер списка пресетов с обработчиками действий над каждым элементом. */
class PresetListAdapter(
    private val onApplyClicked: (Preset) -> Unit,
    private val onEditClicked: (Preset) -> Unit,
    private val onDeleteClicked: (Preset) -> Unit,
) : RecyclerView.Adapter<PresetListAdapter.PresetViewHolder>() {

    private var items: List<Preset> = emptyList()
    private var activePresetId: String? = null

    /** Обновляет набор пресетов и id активного элемента. */
    fun submitData(presets: List<Preset>, activePresetId: String?) {
        items = presets
        this.activePresetId = activePresetId
        notifyDataSetChanged()
    }

    /** Создаёт view holder карточки пресета. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return PresetViewHolder(ItemPresetBinding.inflate(inflater, parent, false))
    }

    /** Привязывает пресет и его активное состояние к карточке списка. */
    override fun onBindViewHolder(holder: PresetViewHolder, position: Int) {
        holder.bind(items[position], items[position].id == activePresetId)
    }

    /** Возвращает количество элементов в списке пресетов. */
    override fun getItemCount(): Int = items.size

    /** Holder карточки одного пресета в списке. */
    inner class PresetViewHolder(
        private val binding: ItemPresetBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        /** Заполняет карточку пресета данными и навешивает действия на кнопки. */
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
