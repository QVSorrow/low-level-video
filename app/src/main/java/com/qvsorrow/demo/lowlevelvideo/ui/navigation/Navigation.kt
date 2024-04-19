package com.qvsorrow.demo.lowlevelvideo.ui.navigation

import android.os.Parcelable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.movableContentWithReceiverOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlin.experimental.ExperimentalTypeInference


private class PageDescription<Page>(
    val matcher: PageMatcher<Page>,
    val composable: ComposablePage<Page>,
)

private operator fun <Page> Iterable<PageDescription<Page>>.get(page: Page): ComposablePage<Page>? {
    return find { it.matcher(page) }?.composable
}

@Stable
private class InternalNavController<Page : Parcelable>(
    start: Page,
    private val pages: List<PageDescription<Page>>,
    private val scope: CoroutineScope,
    private val saveableStateHolder: SaveableStateHolder,
) : NavController<Page> {

    private val pageBackStack = mutableStateListOf<Key<Page>>()
    private val composableBackStack = mutableStateListOf<ComposablePage<Page>>()
    var currentPage: Key<Page> by mutableStateOf(Key(start, 0))
    var isBack: Boolean by mutableStateOf(false)
    var currentComposable: ComposablePage<Page> by mutableStateOf(
        saveable(
            currentPage,
            pages[start]!!,
        )
    )


    override fun navigate(page: Page, addToBackstack: Boolean) {
        scope.launch {
            val composable = pages[page] ?: return@launch
            if (addToBackstack) {
                pageBackStack.add(currentPage)
                composableBackStack.add(currentComposable)
            }
            val key = Key(page, pageBackStack.size)
            currentPage = key
            isBack = false
            currentComposable = saveable(key, composable)
        }
    }

    override fun navigateBack() {
        scope.launch {
            if (pageBackStack.isEmpty()) return@launch
            val key = currentPage
            saveableStateHolder.removeState(key)
            currentPage = pageBackStack.removeLast()
            isBack = true
            currentComposable = composableBackStack.removeLast()
        }
    }

    private fun saveable(key: Key<Page>, composable: ComposablePage<Page>): ComposablePage<Page> {
        return movableContentWithReceiverOf<NavigationScope<Page, Page>> {
            saveableStateHolder.SaveableStateProvider(key) {
                composable(this)
            }
        }
    }
}

@Parcelize
private data class Key<Page : Parcelable>(
    val page: Page,
    val id: Int,
) : Parcelable

private class MapNavigationBuilder<Page : Parcelable>(
    private val start: Page,
    private val scope: CoroutineScope,
    private val saveableStateHolder: SaveableStateHolder,
) : NavigationBuilder<Page>() {

    private val pages = mutableListOf<PageDescription<Page>>()

    @Suppress("UNCHECKED_CAST")
    override fun <P : Page> page(
        matcher: PageMatcher<Page>,
        block: ComposablePageBuilder<Page, P>
    ) {
        val function: ComposablePage<Page> = {
            block.invoke(this as NavigationScope<Page, P>)
        }

        pages.add(PageDescription(matcher, function))
    }

    fun build(): NavController<Page> {
        check(pages.any { it.matcher(start) }) { "Start page ($start) must be initialized via NavigationBuilder.page function" }
        return InternalNavController(start, pages, scope, saveableStateHolder)
    }
}

@Composable
private fun <Page : Parcelable> rememberNavController(
    startPage: Page, block: NavigationBuilder<Page>.() -> Unit
): NavController<Page> {
    val scope = rememberCoroutineScope()
    val saveableStateHolder = rememberSaveableStateHolder()
    return remember {
        val builder = MapNavigationBuilder(startPage, scope, saveableStateHolder)
        builder.block()
        builder.build()
    }
}

private class PageNavigationScope<Page>(
    override val page: Page,
    override val navController: NavController<Page>,
) : NavigationScope<Page, Page>

@OptIn(ExperimentalTypeInference::class)
@Composable
fun <Page : Parcelable> Navigation(
    startPage: Page,
    @BuilderInference block: NavigationBuilder<Page>.() -> Unit,
) {
    val navController = rememberNavController(startPage, block) as InternalNavController

    AnimatedContent(
        navController.currentComposable,
        label = "Navigation Transition",
        transitionSpec = {
            if (!navController.isBack) {
                val enterFade = fadeIn(tween(200))
                val enterSlide = slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    tween(200),
                )
                val exitFade = fadeOut(tween(200))
                (enterFade + enterSlide).togetherWith(exitFade)
            } else {
                val enterFade = fadeIn(tween(200))
                val exitSlide = slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    tween(200),
                )
                val exitFade = fadeOut(tween(100, 100))
                (enterFade).togetherWith(exitSlide + exitFade)
            }
        },
    ) { page ->
        val scope =
            remember(page) { PageNavigationScope(navController.currentPage.page, navController) }
        page(scope)
    }
}
