package me.rhunk.snapenhance.core.features.impl

import android.annotation.SuppressLint
import android.system.Os
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.common.util.protobuf.ProtoEditor
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.event.events.impl.UnaryCallEvent
import me.rhunk.snapenhance.core.features.Feature
import me.rhunk.snapenhance.core.util.ktx.getId
import me.rhunk.snapenhance.core.util.ktx.setObjectField
import java.io.FileDescriptor

class SecurityFeatures : Feature("Security Features") {
    private fun transact(option: Int, option2: Long) = kotlin.runCatching { Os.prctl(option, option2, 0, 0, 0) }.getOrNull()

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

        val hovaPageTitleId = context.resources.getId("hova_page_title")

        fun findHovaPageTitle(): TextView? {
            return context.mainActivity?.findViewById(hovaPageTitleId)
        }

        context.coroutineScope.launch {
            while (true) {
                val status = getStatus()
                withContext(Dispatchers.Main) {
                    val textView = findHovaPageTitle() ?: return@withContext
                    if (status == null || status == 0) {
                        textView.text = "SIF not loaded"
                        textView.textSize = 13F
                        textView.setTextColor(Color.Red.toArgb())
                    } else {
                        textView.setTextColor(Color.Green.toArgb())
                        val prefix = textView.text.toString().substringBeforeLast(" (")
                        textView.text = "$prefix (${status})"
                    }
                }
                delay(1000)
            }
        }
    }
}