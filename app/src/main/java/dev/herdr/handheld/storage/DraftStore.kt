package dev.herdr.handheld.storage

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Each target has its own debounce; switching targets cannot cancel a different draft's save. */
class DraftStore(private val scope: CoroutineScope,private val read: (String)->String?,private val write: (String,String)->Unit,
                 private val saved: (String,String)->Unit,private val failed: ()->Unit) {
    private val pending=mutableMapOf<String,Job>()
    private val versions=mutableMapOf<String,Long>()
    private val lock=Mutex()
    suspend fun load(key: String): String=withContext(Dispatchers.IO) { read(key).orEmpty() }
    fun save(key: String,text: String,debounce: Boolean=false) {
        val version=(versions[key] ?: 0)+1;versions[key]=version
        pending.remove(key)?.cancel()
        pending[key]=scope.launch {
            try {
                if(debounce)delay(400)
                lock.withLock {
                    if(versions[key]!=version)return@withLock
                    withContext(Dispatchers.IO) { write(key,text) }
                    if(versions[key]==version)saved(key,text)
                }
            } catch(e: Exception) { if(e is CancellationException)throw e;failed() }
            finally { if(versions[key]==version)pending.remove(key) }
        }
    }
}
