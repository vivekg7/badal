package io.github.vivekg7.badal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.vivekg7.badal.ui.BadalTheme
import io.github.vivekg7.badal.ui.Shell

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { BadalTheme { Shell() } }
    }
}
