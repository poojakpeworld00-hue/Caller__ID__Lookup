package com.callerid.number.lookup.home.screen.locale

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.callerid.number.lookup.home.frame.ShellViewModel

class LanguageViewModel : ShellViewModel() {

    val languages: List<LanguageItem> = LanguageCatalog.all

    private val _suggested = MutableLiveData<List<LanguageItem>>()
    val suggested: LiveData<List<LanguageItem>> = _suggested

    private val _others = MutableLiveData<List<LanguageItem>>()
    val others: LiveData<List<LanguageItem>> = _others

    private val _selectedTag = MutableLiveData(LanguageCatalog.all.first().tag)
    val selectedTag: LiveData<String> = _selectedTag

    private var appliedCountry: String? = "__none__"

    fun init(currentTag: String) {
        if (languages.any { it.tag == currentTag }) {
            _selectedTag.value = currentTag
        }
    }

    fun useCountry(iso2: String?) {
        val normalized = iso2?.uppercase()
        if (normalized == appliedCountry) return
        appliedCountry = normalized

        val suggested = LanguageCatalog.suggestedFor(normalized)
        val suggestedTags = suggested.map { it.tag }.toSet()
        _suggested.value = suggested
        _others.value = languages.filterNot { it.tag in suggestedTags }
    }

    fun select(tag: String) {
        if (_selectedTag.value != tag) _selectedTag.value = tag
    }
}
