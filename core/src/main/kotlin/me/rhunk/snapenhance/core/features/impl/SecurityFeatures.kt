package me.rhunk.snapenhance.core.features.impl

import android.annotation.SuppressLint
import android.system.Os
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotInterested
import me.rhunk.snapenhance.common.config.MOD_DETECTION_VERSION_CHECK
import me.rhunk.snapenhance.common.config.VersionRequirement
import me.rhunk.snapenhance.common.util.protobuf.ProtoEditor
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.event.events.impl.UnaryCallEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import java.io.FileDescriptor

class SecurityFeatures : Feature("Security Features") {
    private fun transact(option: Int, option2: Long) = runCatching { Os.prctl(option, option2, 0, 0, 0) }.getOrNull()

    private val token by lazy {
        transact(0, 0)
    }

    private fun getStatus() = token?.run {
        transact(this, 0)?.toString(2)?.padStart(32, '0')?.count { it == '1' }
    }

    @SuppressLint("SetTextI18n")
    override fun init() {
        token // pre init token

        context.event.subscribe(UnaryCallEvent::class) { event ->
            if (!event.uri.contains("/Login")) return@subscribe

            // intercept login response
            event.addResponseCallback {
                val response = ProtoReader(buffer)
                val isBlocked = when {
                    event.uri.contains("TLv") -> response.getVarInt(1) == 14L
                    else -> response.getVarInt(1) == 16L
                }

                val errorDataIndex = when {
                    response.contains(11) -> 11
                    response.contains(10) -> 10
                    response.contains(8) -> 8
                    else -> return@addResponseCallback
                }

                if (isBlocked) {
                    val status = transact(token ?: return@addResponseCallback, 1)?.let {
                        val buffer = ByteArray(8192)
                        val fd = FileDescriptor().apply {
                            setObjectField("descriptor", it)
                        }
                        val read = Os.read(fd, buffer, 0, buffer.size)
                        Os.close(fd)
                        buffer.copyOfRange(0, read).decodeToString()
                    }!!

                    buffer = ProtoEditor(buffer).apply {
                        edit(errorDataIndex) {
                            remove(1)
                            addString(1, status)
                        }
                    }.toByteArray()
                }
            }
        }

        val status = getStatus()
        val canCheckVersion = context.bridgeClient.getDebugProp("disable_mod_detection_version_check", "false") != "true"
        val snapchatVersionCode = context.androidContext.packageManager.getPackageInfo(context.androidContext.packageName, 0).longVersionCode

        if (canCheckVersion && MOD_DETECTION_VERSION_CHECK.checkVersion(snapchatVersionCode)?.second == VersionRequirement.OLDER_REQUIRED && (status == null || status < 2)) {
            onNextActivityCreate {
                context.inAppOverlay.showStatusToast(
                    icon = Icons.Filled.NotInterested,
                    text = "SnapEnhance is not compatible with this version of Snapchat without SIF and will result in a ban.\nUse Snapchat ${MOD_DETECTION_VERSION_CHECK.maxVersion?.first ?: "0.0.0"} or older to avoid detections or use test accounts.",
                    durationMs = 10000,
                    maxLines = 6
                )
            }
        }
    }
}