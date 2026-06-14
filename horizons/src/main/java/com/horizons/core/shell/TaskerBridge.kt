package com.horizons.core.shell

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

// Requires user setting: Tasker → Settings → Misc → Allow External Access
class TaskerBridge(private val context: Context) {

    fun runTask(taskName: String, vararg params: Pair<String, String>): Result<Unit> {
        if (!isTaskerInstalled()) {
            return Result.failure(IllegalStateException("Tasker not installed"))
        }
        return try {
            val intent = Intent(ACTION_TASK).apply {
                setPackage(TASKER_PACKAGE)
                // version_number=1.1 is required — modern Tasker silently ignores
                // ACTION_TASK broadcasts that omit it.
                putExtra("version_number", "1.1")
                putExtra("task_name", taskName)
                putExtra("task_priority", 5)
                params.forEachIndexed { i, p -> putExtra("par${i + 1}", p.second) }
            }
            context.sendBroadcast(intent)
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun runScene(sceneName: String): Result<Unit> {
        if (!isTaskerInstalled()) {
            return Result.failure(IllegalStateException("Tasker not installed"))
        }
        return try {
            val intent = Intent(ACTION_TASK).apply {
                setPackage(TASKER_PACKAGE)
                putExtra("version_number", "1.1")
                putExtra("scene_name", sceneName)
            }
            context.sendBroadcast(intent)
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun isTaskerInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(TASKER_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        const val TASKER_PACKAGE = "net.dinglisch.android.taskerm"
        const val ACTION_TASK = "net.dinglisch.android.tasker.ACTION_TASK"
    }
}
