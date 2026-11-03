// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject
import java.util.Locale

internal fun localizedActivityContext(activity: android.app.Activity, locale: Locale): Context =
    android.view.ContextThemeWrapper(activity, 0).apply {
        applyOverrideConfiguration(android.content.res.Configuration(activity.resources.configuration).apply { setLocale(locale) })
    }

internal enum class AppLanguage(val tag:String,val displayName:String,val locale:Locale) {
    SIMPLIFIED("zh-Hans","简体中文",Locale.SIMPLIFIED_CHINESE),
    TRADITIONAL("zh-Hant","繁體中文",Locale.TRADITIONAL_CHINESE),
    ENGLISH("en","English",Locale.ENGLISH);
    companion object {
        fun fromTag(value:String?)=entries.firstOrNull{it.tag==value}?:SIMPLIFIED
        fun saved(context:Context)=fromTag(context.getSharedPreferences("settings",Context.MODE_PRIVATE).getString("appLanguage","zh-Hans"))
        fun save(context:Context,language:AppLanguage){context.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putString("appLanguage",language.tag).apply()}
    }
}
internal val LocalAppLanguage=staticCompositionLocalOf{AppLanguage.SIMPLIFIED}
internal val LocalSetAppLanguage=staticCompositionLocalOf<(AppLanguage)->Unit>{{}}
private object TranslationCatalog {
    @Volatile private var cached:JSONObject?=null
    fun load(context:Context):JSONObject=cached?:synchronized(this){cached?:JSONObject(context.assets.open("localization.json").bufferedReader().use{it.readText()}).also{cached=it}}
}
internal class AppStrings(context:Context,val language:AppLanguage) {
    private val catalog=TranslationCatalog.load(context)
    fun text(key:String,vararg arguments:Any?):String {
        var value=if(language==AppLanguage.SIMPLIFIED)key else catalog.optJSONObject(key)?.optString(language.tag,key)?:key
        return Regex("\\{(\\d+)\\}").replace(value) { match -> arguments.getOrNull(match.groupValues[1].toInt())?.toString() ?: match.value }
    }
    private val patterns by lazy {
        catalog.keys().asSequence().filter{it.contains("{0}") && it.replace(Regex("\\{\\d+\\}"), "").any(Char::isLetter)}
            .sortedByDescending { it.replace(Regex("\\{\\d+\\}"), "").length }.map { key ->
            val matches=Regex("\\{(\\d+)\\}").findAll(key).toList()
            val pattern=StringBuilder("^");var position=0
            matches.forEach{m->pattern.append(Regex.escape(key.substring(position,m.range.first))).append("(.*?)");position=m.range.last+1}
            pattern.append(Regex.escape(key.substring(position))).append('$')
            key to Regex(pattern.toString())
        }.toList()
    }
    fun message(value:String):String=message(value,0)
    private fun message(value:String,depth:Int):String {
        if(language==AppLanguage.SIMPLIFIED||depth>3)return value
        if(catalog.has(value))return text(value)
        if(value.contains('\n'))return value.lines().joinToString("\n"){message(it,depth+1)}
        for((key,pattern) in patterns) {
            val match=pattern.matchEntire(value)?:continue
            return text(key,*match.groupValues.drop(1).map{if(it==value)it else message(it,depth+1)}.toTypedArray())
        }
        return value
    }
}
@Composable internal fun appStrings():AppStrings {
    val context=LocalContext.current;val language=LocalAppLanguage.current
    return remember(context,language){AppStrings(context,language)}
}
@Composable internal fun tr(key:String,vararg arguments:Any?):String=appStrings().text(key,*arguments)
@Composable internal fun trMessage(value:String):String=appStrings().message(value)
internal fun Context.localized(key:String,vararg arguments:Any?)=AppStrings(this,AppLanguage.saved(this)).text(key,*arguments)
