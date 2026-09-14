package dev.herdr.handheld.launcher

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

data class InstalledApp(val label: String,val packageName: String,val activityName: String)

object SystemAccess {
    fun apps(context: Context): List<InstalledApp> = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),PackageManager.MATCH_ALL)
        .filter { it.activityInfo.packageName!=context.packageName }
        .map { InstalledApp(it.loadLabel(context.packageManager).toString(),it.activityInfo.packageName,it.activityInfo.name) }
        .distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    fun open(context: Context,app: InstalledApp): Boolean = runCatching {
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName,app.activityName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));true
    }.getOrDefault(false)
    fun settings(context: Context,home: Boolean=false): Boolean {
        val actions=if(home) listOf(Settings.ACTION_HOME_SETTINGS,Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,Settings.ACTION_SETTINGS) else listOf(Settings.ACTION_SETTINGS)
        return actions.any { action -> runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));true }.getOrDefault(false) }
    }
    fun deviceSettings(context: Context,wifi: Boolean): Boolean = runCatching {
        context.startActivity(Intent(if(wifi)Settings.ACTION_WIFI_SETTINGS else Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));true
    }.getOrElse { settings(context) }
    fun requestHome(activity: Activity): Boolean {
        val roles=activity.getSystemService(RoleManager::class.java)
        if(roles?.isRoleAvailable(RoleManager.ROLE_HOME)==true && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            return runCatching { activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME),120);true }.getOrElse { settings(activity,true) }
        }
        return settings(activity,true)
    }
}
