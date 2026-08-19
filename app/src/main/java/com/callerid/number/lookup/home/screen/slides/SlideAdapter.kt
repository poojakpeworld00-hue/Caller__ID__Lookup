package com.callerid.number.lookup.home.screen.slides

import android.animation.Animator
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.RecyclerView
import com.callerid.number.lookup.home.databinding.CellOnboardingBinding

class SlideAdapter(
    private val pages: List<SlidePage>
) : RecyclerView.Adapter<SlideAdapter.VH>() {

    private companion object {

        const val MIN_ART_SCALE = 0.45f
    }

    inner class VH(val binding: CellOnboardingBinding) : RecyclerView.ViewHolder(binding.root) {

        private val anims = mutableListOf<Animator>()

        fun bind(page: SlidePage) {
            cancelAnims()
            val container = binding.artContainerVw
            container.removeAllViews()

            if (page.customArtRes != 0) {
                LayoutInflater.from(container.context).inflate(page.customArtRes, container, true)

                anims += SlideAnimations.attach(container)
            } else {
                val image = ImageView(container.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageResource(page.artRes)
                }
                container.addView(image)
            }

            binding.lblTitle.setText(page.titleRes)
            binding.lblDesc.setText(page.descRes)
            fitArt(container)
        }

        private fun fitArt(container: FrameLayout) {
            val art = container.getChildAt(0) ?: return
            container.doOnLayout {
                val designHeight = it.resources.getDimension(com.intuit.sdp.R.dimen._210sdp)
                if (designHeight <= 0f) return@doOnLayout
                val scale = (it.height / designHeight).coerceIn(MIN_ART_SCALE, 1f)
                art.scaleX = scale
                art.scaleY = scale
            }
        }

        private fun cancelAnims() {
            anims.forEach { it.cancel() }
            anims.clear()
        }

        fun recycle() = cancelAnims()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = CellOnboardingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(pages[position])

    override fun onViewRecycled(holder: VH) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = pages.size
}
