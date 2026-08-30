package io.github.tonyxmelon.aisudoku.vision

/**
 * Loads the OpenCV native library exactly once.
 *
 * How to load differs by platform and the module must not care which it is on: the JVM
 * uses `nu.pattern.OpenCV.loadShared()`, Android uses `OpenCVLoader.initLocal()`. The
 * caller supplies that as a lambda the first time anything in this module is used.
 */
object OpenCvNatives {

    @Volatile
    private var loaded = false

    val isLoaded: Boolean get() = loaded

    /** Runs [loader] on the first call only. Safe to call from anywhere, any number of times. */
    @Synchronized
    fun ensureLoaded(loader: () -> Unit) {
        if (loaded) return
        loader()
        loaded = true
    }
}
