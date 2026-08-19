package com.callerid.number.lookup.home.screen.identify

import android.app.Activity
import android.content.Intent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.frame.FrameActivity
import com.callerid.number.lookup.home.databinding.ScreenCountryPickerBinding

class CountryPickActivity : FrameActivity<ScreenCountryPickerBinding>() {

    override val layoutId: Int = R.layout.screen_country_picker

    private lateinit var adapter: DialCountryAdapter

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.countryRootVw) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        adapter = DialCountryAdapter { country ->
            setResult(
                Activity.RESULT_OK,
                Intent()
                    .putExtra(EXTRA_ISO, country.iso2)
                    .putExtra(EXTRA_DIAL, country.dial)
                    .putExtra(EXTRA_NAME, country.name)
            )
            finish()
        }
        binding.rollCountries.layoutManager = LinearLayoutManager(this)
        binding.rollCountries.adapter = adapter
        adapter.submit(DialCountries.all)

        binding.padBack.setOnClickListener { goBack() }
        binding.inpSearch.addTextChangedListener { text -> filter(text?.toString().orEmpty()) }
    }

    private fun filter(query: String) {
        val q = query.trim()
        val list = if (q.isEmpty()) {
            DialCountries.all
        } else {
            DialCountries.all.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.dial.contains(q) ||
                    it.iso2.contains(q, ignoreCase = true)
            }
        }
        adapter.submit(list)
    }

    companion object {
        const val EXTRA_ISO = "extra_iso"
        const val EXTRA_DIAL = "extra_dial"
        const val EXTRA_NAME = "extra_name"
    }
}
