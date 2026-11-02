package net.matsudamper.sesamilock.sesame.credential

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.RegisteredLock

public sealed interface LockRepositoryState {
    public data object Loading : LockRepositoryState

    public data class Loaded(val locks: List<RegisteredLock>) : LockRepositoryState

    public data object Unrecoverable : LockRepositoryState
}

public enum class AddLockResult {
    ADDED,
    ALREADY_REGISTERED,
    NOT_READY,
}

public class EncryptedLockRepository(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))
    private val cipher = KeystoreCipher(KEY_ALIAS)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<LockRepositoryState>(LockRepositoryState.Loading)
    public val state: StateFlow<LockRepositoryState> = mutableState.asStateFlow()

    public suspend fun load() {
        mutex.withLock {
            mutableState.value = withContext(Dispatchers.IO) { readState() }
        }
    }

    public suspend fun add(lock: RegisteredLock): AddLockResult = mutex.withLock {
        val current = (mutableState.value as? LockRepositoryState.Loaded)?.locks ?: return@withLock AddLockResult.NOT_READY
        if (current.any { it.id == lock.id }) return@withLock AddLockResult.ALREADY_REGISTERED
        val updated = current + lock
        withContext(Dispatchers.IO) { write(updated) }
        mutableState.value = LockRepositoryState.Loaded(updated)
        AddLockResult.ADDED
    }

    public suspend fun remove(lockId: LockId) {
        mutex.withLock {
            val current = (mutableState.value as? LockRepositoryState.Loaded)?.locks ?: return@withLock
            val updated = current.filterNot { it.id == lockId }
            withContext(Dispatchers.IO) { write(updated) }
            mutableState.value = LockRepositoryState.Loaded(updated)
        }
    }

    public suspend fun deleteAll() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                file.delete()
                cipher.deleteKey()
            }
            mutableState.value = LockRepositoryState.Loaded(listOf())
        }
    }

    private fun readState(): LockRepositoryState {
        val encrypted = try {
            file.readFully()
        } catch (_: FileNotFoundException) {
            return LockRepositoryState.Loaded(listOf())
        }
        val plain = cipher.decrypt(encrypted) ?: return LockRepositoryState.Unrecoverable
        val locks = StoredLocksCodec.decode(plain) ?: return LockRepositoryState.Unrecoverable
        return LockRepositoryState.Loaded(locks)
    }

    private fun write(locks: List<RegisteredLock>) {
        val encrypted = cipher.encrypt(StoredLocksCodec.encode(locks))
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    private companion object {
        private const val FILE_NAME = "locks.enc"
        private const val KEY_ALIAS = "sesami_lock_credentials"
    }
}
