package rj.qmce.lite.data.emotion

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Limits how many animated stickers (system APNG / market GIF / Giphy) play at once.
 */
object EmotionPlayGate {
    private const val MAX_PLAYING = 2

    private val playing = AtomicInteger(0)
    private val holders = ConcurrentHashMap.newKeySet<String>()

    fun tryAcquire(key: String): Boolean {
        if (key.isBlank()) return false
        if (holders.contains(key)) return true
        while (true) {
            val current = playing.get()
            if (current >= MAX_PLAYING) return false
            if (!playing.compareAndSet(current, current + 1)) continue
            if (!holders.add(key)) {
                playing.decrementAndGet()
                return true
            }
            return true
        }
    }

    fun release(key: String) {
        if (key.isBlank()) return
        if (!holders.remove(key)) return
        playing.updateAndGet { value -> (value - 1).coerceAtLeast(0) }
    }

    fun isHeld(key: String): Boolean = holders.contains(key)
}
