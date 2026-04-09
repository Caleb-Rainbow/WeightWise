package com.example.weight.ui.theme

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.weight.ui.theme.myiconpack.CalendarCheck
import kotlin.collections.List as ____KtList

public object MyIconPack

private var __AllIcons: ____KtList<ImageVector>? = null

public val MyIconPack.AllIcons: ____KtList<ImageVector>
  get() {
    if (__AllIcons != null) {
      return __AllIcons!!
    }
    __AllIcons= listOf(CalendarCheck)
    return __AllIcons!!
  }
