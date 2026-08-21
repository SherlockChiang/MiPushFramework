package com.nihility.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import com.elvishew.xlog.XLog

object NotificationManagerEx {
    private const val TAG = "NotificationManagerEx"

    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationContext: Context
    private var notificationService: Any? = null
    private var packageAttributionSupported: Boolean? = null

    @JvmField
    var isHooked: Boolean = false

    @JvmStatic
    fun init(context: Context) {
        notificationContext = context.applicationContext
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationService = invokeHidden(notificationManager, "getService", emptyArray()).value
        packageAttributionSupported = null
    }

    fun notify(
        packageName: String,
        tag: String?, id: Int, notification: Notification
    ) {
        XLog.d(TAG, "notify() called with: packageName = $packageName, tag = $tag, id = $id, notification = $notification")
        if (::notificationContext.isInitialized && packageName != notificationContext.packageName) {
            // HyperOS 2 reads this official XMSF bridge extra on Android 10+
            // even when the public NotificationManager call is used.
            notification.extras.putString("xmsf_target_package", packageName)
        }
        // Official XMSF switches to the package-attributed hidden API on
        // Android 10/HyperOS.  This is what lets SystemUI resolve the real
        // client's channel, click target and focus renderer; the old bridge
        // accidentally used the reverse SDK condition and therefore posted
        // third-party notifications as XMSF on modern devices.
        val postedAsPackage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            supportsPackageAttribution() &&
            notifyAsPackage(packageName, tag, id, notification)
        if (!postedAsPackage) {
            notificationManager.notify(tag, id, notification)
        }
    }

    /**
     * Xiaomi's XMSF enables package attribution only when the private fake
     * condition-provider bridge is installed. Probe the same capability before
     * calling the hidden API; unsupported/AOSP builds retain the public fallback.
     */
    private fun supportsPackageAttribution(): Boolean {
        packageAttributionSupported?.let { return it }
        if (!::notificationManager.isInitialized) return false
        val supported = (invokeHidden(
            "isSystemConditionProviderEnabled",
            arrayOf(String::class.java),
            arrayOf("xmsf_fake_condition_provider_path")
        ).value as? Boolean) == true
        packageAttributionSupported = supported
        return supported
    }

    private fun notifyAsPackage(
        packageName: String,
        tag: String?,
        id: Int,
        notification: Notification,
    ): Boolean {
        if (!::notificationContext.isInitialized || packageName == notificationContext.packageName) {
            return false
        }
        return try {
            val method = notificationManager.javaClass.getDeclaredMethod(
                "notifyAsPackage",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Notification::class.java,
            )
            method.isAccessible = true
            method.invoke(notificationManager, packageName, tag, id, notification)
            true
        } catch (ignored: Throwable) {
            false
        }
    }

    fun cancel(
        packageName: String,
        tag: String?, id: Int
    ) {
        XLog.d(TAG, "cancel() called with: packageName = $packageName, tag = $tag, id = $id")
        if (!cancelAsPackage(packageName, tag, id)) {
            notificationManager.cancel(tag, id)
        }
    }

    fun createNotificationChannels(
        packageName: String,
        channels: List<NotificationChannel?>
    ) {
        XLog.d(TAG, "createNotificationChannels() called with: packageName = $packageName, channels = $channels")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!createNotificationChannelsAsPackage(packageName, channels)) {
                notificationManager.createNotificationChannels(channels)
            }
        }
    }

    fun getNotificationChannel(
        packageName: String,
        channelId: String?
    ): NotificationChannel? {
        XLog.d(TAG, "createNotificationChannels() called with: packageName = $packageName, channelId = $channelId")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getNotificationChannelAsPackage(packageName, channelId)
                ?: notificationManager.getNotificationChannel(channelId)
        } else {
            null
        }
    }

    fun getNotificationChannels(
        packageName: String
    ): List<NotificationChannel?>? {
        XLog.d(TAG, "getNotificationChannels() called with: packageName = $packageName")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getNotificationChannelsAsPackage(packageName)
                ?: notificationManager.getNotificationChannels()
        } else {
            emptyList()
        }
    }

    fun deleteNotificationChannel(
        packageName: String,
        channelId: String?
    ) {
        XLog.d(TAG, "deleteNotificationChannel() called with: packageName = $packageName, channelId = $channelId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!deleteNotificationChannelAsPackage(packageName, channelId)) {
                notificationManager.deleteNotificationChannel(channelId)
            }
        }
    }


    fun createNotificationChannelGroups(
        packageName: String,
        groups: List<NotificationChannelGroup?>
    ) {
        XLog.d(TAG, "createNotificationChannelGroups() called with: packageName = $packageName, groups = $groups")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!createNotificationChannelGroupsAsPackage(packageName, groups)) {
                notificationManager.createNotificationChannelGroups(groups)
            }
        }
    }

    fun getNotificationChannelGroup(
        packageName: String,
        groupId: String?
    ): NotificationChannelGroup? {
        XLog.d(TAG, "getNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getNotificationChannelGroupAsPackage(packageName, groupId)
                ?: notificationManager.getNotificationChannelGroup(groupId)
        } else {
            null
        }
    }

    fun getNotificationChannelGroups(
        packageName: String
    ): List<NotificationChannelGroup?>? {
        XLog.d(TAG, "getNotificationChannelGroups() called with: packageName = $packageName")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.getNotificationChannelGroups()
        } else {
            emptyList()
        }
    }

    fun deleteNotificationChannelGroup(
        packageName: String,
        groupId: String?
    ) {
        XLog.d(TAG, "deleteNotificationChannelGroup() called with: packageName = $packageName, groupId = $groupId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!deleteNotificationChannelGroupAsPackage(packageName, groupId)) {
                notificationManager.deleteNotificationChannelGroup(groupId)
            }
        }
    }

    /**
     * MIUI/HyperOS exposes package-attributed NotificationManager methods which
     * are hidden from the Android SDK.  Keep these calls isolated and best-effort:
     * an AOSP build (or a ROM that blocks hidden API access) simply falls back to
     * the public XMSF operation.  This preserves delivery while allowing SystemUI
     * to resolve the client's channel, icon and focus policy when the bridge is
     * available.
     */
    private fun cancelAsPackage(packageName: String, tag: String?, id: Int): Boolean {
        return invokeHidden(
            "cancelAsPackage",
            arrayOf(String::class.java, String::class.java, Int::class.javaPrimitiveType!!),
            arrayOf(packageName, tag, id)
        ).success
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannelsAsPackage(
        packageName: String,
        channels: List<NotificationChannel?>
    ): Boolean {
        val uid = packageUid(packageName) ?: return false
        val slice = asParceledListSlice(channels) ?: return false
        return invokeService(
            "createNotificationChannelsForPackage",
            arrayOf(packageName, uid, slice)
        ).success
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getNotificationChannelAsPackage(
        packageName: String,
        channelId: String?
    ): NotificationChannel? {
        if (channelId == null) return null
        return getNotificationChannelsAsPackage(packageName)
            ?.firstOrNull { it?.id == channelId }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getNotificationChannelsAsPackage(
        packageName: String
    ): List<NotificationChannel?>? {
        val uid = packageUid(packageName) ?: return null
        val result = invokeService(
            "getNotificationChannelsForPackage",
            arrayOf(packageName, uid, false)
        )
        return unwrapList(result.value)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannelGroupsAsPackage(
        packageName: String,
        groups: List<NotificationChannelGroup?>
    ): Boolean {
        val slice = asParceledListSlice(groups) ?: return false
        return invokeService(
            "createNotificationChannelGroups",
            arrayOf(packageName, slice)
        ).success
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun getNotificationChannelGroupAsPackage(
        packageName: String,
        groupId: String?
    ): NotificationChannelGroup? {
        if (groupId == null) return null
        val uid = packageUid(packageName) ?: return null
        return invokeService(
            "getNotificationChannelGroupForPackage",
            arrayOf(groupId, packageName, uid)
        ).value as? NotificationChannelGroup
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun deleteNotificationChannelGroupAsPackage(
        packageName: String,
        groupId: String?
    ): Boolean {
        if (groupId == null) return false
        return invokeService(
            "deleteNotificationChannelGroup",
            arrayOf(packageName, groupId)
        ).success
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> unwrapList(value: Any?): List<T?>? {
        if (value == null) return null
        if (value is List<*>) return value as List<T?>
        val list = invokeHidden(value, "getList", emptyArray()).value
        @Suppress("UNCHECKED_CAST")
        return list as? List<T?>
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun deleteNotificationChannelAsPackage(
        packageName: String,
        channelId: String?
    ): Boolean {
        if (channelId == null) return false
        val direct = invokeService(
            "deleteNotificationChannel",
            arrayOf(packageName, channelId)
        )
        if (direct.success) return true
        val uid = packageUid(packageName) ?: return false
        return invokeService(
            "deleteNotificationChannelForPackage",
            arrayOf(packageName, uid, channelId)
        ).success
    }

    private data class HiddenCallResult(val success: Boolean, val value: Any?)

    private fun packageUid(packageName: String): Int? {
        if (!::notificationContext.isInitialized) return null
        if (packageName == notificationContext.packageName) return null
        return try {
            notificationContext.packageManager.getPackageUid(packageName, 0)
                .takeIf { it >= 0 }
        } catch (_: Throwable) {
            null
        }
    }

    private fun asParceledListSlice(values: List<*>): Any? {
        return try {
            val type = Class.forName("android.content.pm.ParceledListSlice")
            val constructor = type.getDeclaredConstructor(List::class.java)
            constructor.isAccessible = true
            constructor.newInstance(values)
        } catch (_: Throwable) {
            null
        }
    }

    private fun invokeService(methodName: String, args: Array<Any?>): HiddenCallResult {
        return invokeHidden(notificationService, methodName, args)
    }

    private fun invokeHidden(
        methodName: String,
        parameterTypes: Array<Class<*>>,
        args: Array<Any?>
    ): HiddenCallResult {
        if (!::notificationManager.isInitialized) return HiddenCallResult(false, null)
        return try {
            val method = try {
                notificationManager.javaClass.getDeclaredMethod(methodName, *parameterTypes)
            } catch (_: NoSuchMethodException) {
                notificationManager.javaClass.getMethod(methodName, *parameterTypes)
            }
            method.isAccessible = true
            HiddenCallResult(true, method.invoke(notificationManager, *args))
        } catch (_: Throwable) {
            HiddenCallResult(false, null)
        }
    }

    private fun invokeHidden(
        target: Any?,
        methodName: String,
        args: Array<Any?>
    ): HiddenCallResult {
        if (target == null) return HiddenCallResult(false, null)
        return try {
            val methods = target.javaClass.methods.asSequence() +
                target.javaClass.declaredMethods.asSequence()
            val method = methods.firstOrNull {
                it.name == methodName && parametersAccept(it.parameterTypes, args)
            } ?: return HiddenCallResult(false, null)
            method.isAccessible = true
            HiddenCallResult(true, method.invoke(target, *args))
        } catch (_: Throwable) {
            HiddenCallResult(false, null)
        }
    }

    private fun parametersAccept(types: Array<Class<*>>, args: Array<Any?>): Boolean {
        if (types.size != args.size) return false
        return types.indices.all { index ->
            val argument = args[index]
            if (argument == null) {
                !types[index].isPrimitive
            } else {
                boxed(types[index]).isInstance(argument)
            }
        }
    }

    private fun boxed(type: Class<*>): Class<*> = when (type) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        else -> type
    }

    private fun currentUserId(): Int {
        return try {
            val method = UserHandle::class.java.getDeclaredMethod("myUserId")
            method.isAccessible = true
            (method.invoke(null) as? Int) ?: 0
        } catch (_: Throwable) {
            // Single-user devices (and AOSP SDK stubs) use user 0.
            0
        }
    }

    fun areNotificationsEnabled(
        packageName: String
    ): Boolean {
        XLog.d(TAG, "areNotificationsEnabled() called with: packageName = $packageName")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val uid = packageUid(packageName)
            val attributed = if (uid == null) HiddenCallResult(false, null) else {
                invokeService("areNotificationsEnabledForPackage", arrayOf(packageName, uid))
            }
            (attributed.value as? Boolean) ?: notificationManager.areNotificationsEnabled()
        } else {
            true
        }
    }

    fun getActiveNotifications(
        packageName: String
    ): Array<StatusBarNotification?>? {
        XLog.d(TAG, "getActiveNotifications() called with: packageName = $packageName")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val attributed = if (::notificationContext.isInitialized &&
                packageName != notificationContext.packageName
            ) {
                invokeService(
                    "getAppActiveNotifications",
                    arrayOf(packageName, currentUserId())
                )
            } else {
                HiddenCallResult(false, null)
            }
            val list = unwrapList<StatusBarNotification>(attributed.value)
            if (list != null) {
                list.filterNotNull().toTypedArray()
            } else {
                notificationManager.getActiveNotifications()
            }
        } else {
            emptyArray()
        }
    }

}
