package com.example.weight.ui.common

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent

private const val NAV_ANIMATION_DURATION = 300

val navTransitionSpec:  AnimatedContentTransitionScope<Scene<Any>>.() -> ContentTransform = {
    ContentTransform(
        targetContentEnter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(NAV_ANIMATION_DURATION)
        ) + fadeIn(animationSpec = tween(NAV_ANIMATION_DURATION)),
        initialContentExit = slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(NAV_ANIMATION_DURATION)
        ) + fadeOut(animationSpec = tween(NAV_ANIMATION_DURATION))
    )
}

val navPopTransitionSpec:  AnimatedContentTransitionScope<Scene<Any>>.() -> ContentTransform = {
    ContentTransform(
        targetContentEnter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(NAV_ANIMATION_DURATION)
        ) + fadeIn(animationSpec = tween(NAV_ANIMATION_DURATION)),
        initialContentExit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(NAV_ANIMATION_DURATION)
        ) + fadeOut(animationSpec = tween(NAV_ANIMATION_DURATION))
    )
}

val prependNavTransitionSpec:AnimatedContentTransitionScope<Scene<Any>>.(
    @NavigationEvent.SwipeEdge Int
) -> ContentTransform ={
    ContentTransform(
        targetContentEnter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(NAV_ANIMATION_DURATION)
        ) + fadeIn(animationSpec = tween(NAV_ANIMATION_DURATION)),
        initialContentExit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(NAV_ANIMATION_DURATION)
        ) + fadeOut(animationSpec = tween(NAV_ANIMATION_DURATION))
    )
}