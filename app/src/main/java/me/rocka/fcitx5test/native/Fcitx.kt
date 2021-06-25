package me.rocka.fcitx5test.native

import android.content.Context
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.rocka.fcitx5test.copyFileOrDir
import java.util.concurrent.atomic.AtomicBoolean

class Fcitx(private val context: Context) : DefaultLifecycleObserver {

    private var fcitxJob: Job? = null

    /**
     * Subscribe this flow to receive event sent from fcitx
     */
    val eventFlow = eventFlow_.asSharedFlow()


    fun sendKey(key: String) = sendKeyToFcitx(key)
    fun sendKey(c: Char) = sendKeyToFcitx(c)
    fun select(idx: Int) = selectCandidate(idx)

    init {
        if (isRunning.get())
            throw IllegalAccessException("Fcitx5 is already running!")
    }

    private companion object JNI: CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO) {
        private var isRunning = AtomicBoolean(false)

        private val eventFlow_ =
            MutableSharedFlow<FcitxEvent<*>>(
                extraBufferCapacity = 15,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )


        init {
            System.loadLibrary("native-lib")
        }

        @JvmStatic
        external fun startupFcitx(appData: String, appLib: String, extData: String): Int

        @JvmStatic
        external fun exitFcitx()

        @JvmStatic
        @JvmName("sendKeyToFcitxString")
        external fun sendKeyToFcitx(key: String)

        @JvmStatic
        @JvmName("sendKeyToFcitxChar")
        external fun sendKeyToFcitx(c: Char)

        @JvmStatic
        external fun selectCandidate(idx: Int)

        /**
         * Called from native-lib
         */
        @Suppress("unused")
        @JvmStatic
        fun handleFcitxEvent(type: Int, vararg params: Any) {
            Log.d(
                "FcitxEvent",
                "type=${type}, params=${params.run { "[$size]" + joinToString(",") }}"
            )
            launch {
                eventFlow_.emit(FcitxEvent.create(type, params.asList()))
            }
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        with(context) {
            fcitxJob = launch {
                copyFileOrDir("fcitx5")
                // TODO: should be set in a callback which indicates fcitx has started
                isRunning.set(true)
                startupFcitx(
                    applicationInfo.dataDir,
                    applicationInfo.nativeLibraryDir,
                    getExternalFilesDir(null)!!.absolutePath
                )
                isRunning.set(false)
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        exitFcitx()
        runBlocking {
            fcitxJob?.cancelAndJoin()
        }
        fcitxJob = null
    }

}