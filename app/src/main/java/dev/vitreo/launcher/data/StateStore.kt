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
    data class Loaded(val state: LauncherState, val warning: String? = null)

    fun load(): Loaded {
        // openRead also restores AtomicFile's .bak after interrupted writes.
        if (!file.exists() && !File(file.path + ".bak").exists()) return Loaded(LauncherState())
        return try { Loaded(atomic.openRead().use { StateCodec.decode(readLimited(it)) }) }
        catch (e: Exception) {
            val recovery = File(file.parentFile, "layout-corrupt-${System.currentTimeMillis()}.json")
            runCatching { if (file.exists()) file.copyTo(recovery) }
            Loaded(LauncherState(), "No se pudo leer el escritorio. Se ha intentado conservar una copia local del archivo da\u00f1ado.")
        }
    }

    fun save(state: LauncherState) {
        val bytes = StateCodec.encode(state).toByteArray(Charsets.UTF_8)
        require(bytes.size <= StateCodec.MAX_BYTES) { "El escritorio supera el limite de almacenamiento." }
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }

    companion object {
        fun readLimited(stream: InputStream): String {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= StateCodec.MAX_BYTES) { "El archivo supera 1 MiB." }
                output.write(buffer, 0, count)
            }
            return output.toByteArray().toString(Charsets.UTF_8)
        }
    }
}
