package android.net.wifi
class WifiManager {
    fun createMulticastLock(tag: String): MulticastLock = MulticastLock()
    class MulticastLock {
        fun setReferenceCounted(referenceCounted: Boolean) {}
        fun acquire() {}
        fun release() {}
        val isHeld: Boolean get() = false
    }
}
