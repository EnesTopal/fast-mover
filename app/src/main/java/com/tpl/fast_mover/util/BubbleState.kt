package com.tpl.fast_mover.util

import kotlinx.coroutines.flow.MutableStateFlow

object BubbleState {
    val lastMovedFileName = MutableStateFlow<String?>(null)
}