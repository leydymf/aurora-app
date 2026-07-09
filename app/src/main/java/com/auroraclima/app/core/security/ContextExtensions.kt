package com.auroraclima.app.core.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Desenvuelve la cadena de ContextWrapper hasta la Activity dueña de la ventana. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
