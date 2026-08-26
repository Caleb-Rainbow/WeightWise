package com.example.weight.ui.common

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent

private const val NAV_ANIMATION_DURATION = 300

/** 一级目的地之间（tab 切换）用轻量 fade+scale：坞不随页面滑动，切换是平行层级而非进栈 */
private const val TOP_LEVEL_ANIMATION_DURATION = 120

/**
 * push 转场：两端都是一级目的地时 fade+scale（tab 语义），其余（进入二级页/任务流）
 * 保持水平滑动（层级语义）。[topLevelKeys] 由调用方注入，避免 ui.common 反向依赖路由定义。
 */
fun navTransitionSpec(
    topLevelKeys: Set<Any>,
): AnimatedContentTransitionScope<Scene<Any>>.() -> ContentTransform = {
    if (initialState.key in topLevelKeys && targetState.key in topLevelKeys) {
        ContentTransform(
            targetContentEnter = fadeIn(animationSpec = tween(TOP_LEVEL_ANIMATION_DURATION)) +
                scaleIn(initialScale = 0.96f, animationSpec = tween(TOP_LEVEL_ANIMATION_DURATION)),
            initialContentExit = fadeOut(animationSpec = tween(TOP_LEVEL_ANIMATION_DURATION)) +
                scaleOut(targetScale = 0.98f, animationSpec = tween(TOP_LEVEL_ANIMATION_DURATION)),
        )
    } else {
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
