package io.github.vivekg7.badal

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** The app's few long-lived objects. No DI framework: there is one of each. */
class App : Application() {
    /** For work that must outlive the screen that started it, such as setting a wallpaper. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var store: Store
    lateinit var images: Images
    lateinit var engine: Engine
    lateinit var library: Library

    override fun onCreate() {
        super.onCreate()
        store = Store(File(filesDir, "state.json"))
        images = Images(this)
        engine = Engine(this, store, images)
        library = Library(store, images, engine)
    }
}

val Context.app get() = applicationContext as App
