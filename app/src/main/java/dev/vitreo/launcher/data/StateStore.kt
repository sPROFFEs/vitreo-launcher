package dev.vitreo.launcher.data

import android.content.Context
import android.util.AtomicFile
import dev.vitreo.launcher.core.LauncherState
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream

class StateStore(context: Context) {
    private val file = File(context.filesDir, "layout.json")
    private val atomic = AtomicFile(file)
    // AtomicFile requires caller synchronization, including across StateStore instances.
    // Home's writer, listener initialization and lock setup all use the same local file.
    private val ioLock = locks.getOrPut(file.absolutePath) { Any() }
    data class Loaded(val state: LauncherState, val warning: String? = null)

    fun load(): Loaded = synchronized(ioLock) {
        // openRead also restores AtomicFile's .bak after interrupted writes.
        if (!file.exists() && !File(file.path + ".bak").exists()) return@synchronized Loaded(LauncherState())
        return@synchronized try { Loaded(atomic.openRead().use { StateCodec.decode(readLimited(it)) }) }
        catch (e: Exception) {
            val recovery = File(file.parentFile, "layout-corrupt-${System.currentTimeMillis()}.json")
            runCatching { if (file.exists()) file.copyTo(recovery) }
            Loaded(LauncherState(), "Could not read your layout. A local recovery copy was attempted.")
        }
    }

    fun save(state: LauncherState): Unit = synchronized(ioLock) {
        val bytes = StateCodec.encode(state).toByteArray(Charsets.UTF_8)
        require(bytes.size <= StateCodec.MAX_BYTES) { "Layout exceeds the storage limit." }
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }

    companion object {
        private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()
        fun readLimited(stream: InputStream): String {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= StateCodec.MAX_BYTES) { "File exceeds 1 MiB." }
                output.write(buffer, 0, count)
            }
            return output.toByteArray().toString(Charsets.UTF_8)
        }
    }
}
