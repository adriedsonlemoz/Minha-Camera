package android.content
open class Context {
    val applicationContext: Context get() = this
    open fun getSystemService(name: String): Any? = null
    open fun <T : Any> getSystemService(clazz: Class<T>): T? = null
    companion object { const val WIFI_SERVICE = "wifi" }
}
