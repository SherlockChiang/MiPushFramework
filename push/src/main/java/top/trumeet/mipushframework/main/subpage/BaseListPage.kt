package top.trumeet.mipushframework.main.subpage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import top.trumeet.mipushframework.component.initIconCache
import top.yukonga.miuix.kmp.basic.Surface

@Composable
fun Page(content: @Composable () -> Unit) {
    val context = LocalContext.current
    initIconCache(context)

    Surface(modifier = Modifier.fillMaxSize(), content = content)
}
