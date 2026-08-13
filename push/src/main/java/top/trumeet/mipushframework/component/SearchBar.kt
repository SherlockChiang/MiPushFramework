package top.trumeet.mipushframework.component

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar as MiuixSearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SearchBar(placeholder: String, onValueChange: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val currentPlaceholder by rememberUpdatedState(placeholder)
    val debounceOnValueChange = rememberDebouncedValueChange(onValueChange)
    val change: (String) -> Unit = { value ->
        query = value
        debounceOnValueChange(value)
    }

    MiuixSearchBar(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        inputField = {
            InputField(
                query = query,
                onQueryChange = change,
                label = currentPlaceholder,
                onSearch = {
                    debounceOnValueChange.flush(it)
                    expanded = false
                },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                leadingIcon = {
                    IconButton(
                        onClick = { expanded = true },
                        backgroundColor = Color.Transparent,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Search,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                        )
                    }
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = { change("") },
                            modifier = Modifier.semantics {
                                contentDescription = "Clear search"
                            },
                            backgroundColor = Color.Transparent,
                        ) {
                            Text(
                                text = "×",
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                fontSize = MiuixTheme.textStyles.title4.fontSize,
                            )
                        }
                    }
                } else {
                    null
                },
            )
        },
        expanded = expanded,
        onExpandedChange = { expanded = it },
        content = {},
    )
}

private class DebouncedValueChange(
    private val onValueChange: (String) -> Unit,
    private val launch: (suspend () -> Unit) -> Job,
) {
    private var job: Job? = null

    operator fun invoke(value: String) {
        job?.cancel()
        job = launch {
            delay(300)
            onValueChange(value)
        }
    }

    fun flush(value: String) {
        job?.cancel()
        onValueChange(value)
    }

    fun cancel() {
        job?.cancel()
    }
}

@Composable
private fun rememberDebouncedValueChange(onValueChange: (String) -> Unit): DebouncedValueChange {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val scope = rememberCoroutineScope()
    val debounced = remember(scope) {
        DebouncedValueChange(
            onValueChange = { currentOnValueChange(it) },
            launch = { block -> scope.launch { block() } },
        )
    }
    DisposableEffect(debounced) {
        onDispose(debounced::cancel)
    }
    return debounced
}
