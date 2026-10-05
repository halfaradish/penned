package dev.penned.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.penned.app.data.db.PennedDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(db: PennedDatabase) : ViewModel() {

    val rows: StateFlow<List<dev.penned.app.data.db.dao.TxWithCategory>> = db.transactionDao()
        .observeRecentWithCategory(limit = 500)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
