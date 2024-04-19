package com.qvsorrow.demo.lowlevelvideo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

@DslMarker
annotation class NavBuilder

typealias ComposablePage<Page> = @Composable NavigationScope<Page, Page>.() -> Unit
typealias ComposablePageBuilder<Page, ThisPage> = @Composable NavigationScope<Page, ThisPage>.() -> Unit

@NavBuilder
interface NavigationScope<Page, ThisPage : Page> {
    val page: ThisPage
    val navController: NavController<Page>
}


@Stable
interface NavController<Page> {
    fun navigate(page: Page, addToBackstack: Boolean = true)
    fun navigateBack()
}


@NavBuilder
abstract class NavigationBuilder<Page> {

    @NavBuilder
    abstract fun<P : Page> page(
        matcher: PageMatcher<Page>,
        block: ComposablePageBuilder<Page, P>,
    )

    inline fun <reified P : Page> page(
        noinline block: ComposablePageBuilder<Page, P>,
    ) {
        @Suppress("UNCHECKED_CAST")
        page(typeMatcher<P>() as PageMatcher<Page>, block)
    }
}

typealias PageMatcher<Page> = (Page) -> Boolean

inline fun <reified Page> typeMatcher(): PageMatcher<Page> = { Page::class.isInstance(it) }

