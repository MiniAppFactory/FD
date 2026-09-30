package com.miniappfactory.frontlinedefender.game.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Compose cagri yerleri icin: Play Billing satin alma ekrani Activity ister.
 * `LocalContext.current.findActivity()` guvenli kullanim; null donerse cagiran taraf
 * islemi sessizce yapmaz (crash YOK).
 */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
