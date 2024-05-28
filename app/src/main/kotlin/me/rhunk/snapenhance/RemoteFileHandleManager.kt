package me.rhunk.snapenhance

import android.os.ParcelFileDescriptor
import me.rhunk.snapenhance.bridge.storage.FileHandle
import me.rhunk.snapenhance.bridge.storage.FileHandleManager
import me.rhunk.snapenhance.common.bridge.FileHandleScope
import me.rhunk.snapenhance.common.bridge.InternalFileHandleType
import me.rhunk.snapenhance.common.bridge.wrapper.LocaleWrapper
import me.rhunk.snapenhance.common.logger.AbstractLogger
import me.rhunk.snapenhance.common.util.ktx.toParcelFileDescriptor
import java.io.File


class LocalFileHandle(
    private val file: File
): FileHandle.Stub() {
    override fun exists() = file.exists()
    override fun create() = file.createNewFile()
    override fun delete() = file.delete()

    override fun open(mode: Int): ParcelFileDescriptor? {
        return runCatching {
            ParcelFileDescriptor.open(file, mode)
        }.onFailure {
            AbstractLogger.directError("Failed to open file handle: ${it.message}", it)
        }.getOrNull()
    }
}

class AssetFileHandle(
    private val context: RemoteSideContext,
    private val assetPath: String
): FileHandle.Stub() {
    override fun exists() = true
    override fun create() = false
    override fun delete() = false

    override fun open(mode: Int): ParcelFileDescriptor? {
        return runCatching {
            context.androidContext.assets.open(assetPath).toParcelFileDescriptor(context.coroutineScope)
        }.onFailure {
            AbstractLogger.directError("Failed to open asset handle: ${it.message}", it)
        }.getOrNull()
    }
}


class RemoteFileHandleManager(
    private val context: RemoteSideContext
): FileHandleManager.Stub() {
    override fun getFileHandle(scope: String, name: String): FileHandle? {
        val fileHandleScope = FileHandleScope.fromValue(scope) ?: run {
            context.log.error("invalid file handle scope: $scope", "FileHandleManager")
            return null
        }
        when (fileHandleScope) {
            FileHandleScope.INTERNAL -> {
                val fileHandleType = InternalFileHandleType.fromValue(name) ?: run {
                    context.log.error("invalid file handle name: $name", "FileHandleManager")
                    return null
                }

                return LocalFileHandle(
                    fileHandleType.resolve(context.androidContext)
                )
            }
            FileHandleScope.LOCALE -> {
                val foundLocale = context.androidContext.resources.assets.list("lang")?.firstOrNull {
                    it.startsWith(name)
                }?.substringBefore(".") ?: return null

                if (name == LocaleWrapper.DEFAULT_LOCALE) {
                    return AssetFileHandle(
                        context,
                        "lang/${LocaleWrapper.DEFAULT_LOCALE}.json"
                    )
                }

                return AssetFileHandle(
                    context,
                    "lang/$foundLocale.json"
                )
            }
            else -> return null
        }
    }
}