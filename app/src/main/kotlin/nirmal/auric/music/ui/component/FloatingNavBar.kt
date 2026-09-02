/**
 * vivimusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalSharedTransitionApi::class)

package nirmal.auric.music.ui.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nirmal.auric.music.ui.player.FloatingMiniPlayer
import nirmal.auric.music.ui.screens.Screens
import nirmal.auric.music.ui.component.floatingtabbar.FloatingTabBar
import nirmal.auric.music.ui.component.floatingtabbar.FloatingTabBarDefaults

@Composable
fun AppFloatingNavBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    showPlayerAccessory: Boolean = false,
    onAccessoryClick: () -> Unit = {},
) {
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassSupported()

    val backgroundColor = when {
        useGlass -> Color.Transparent
        pureBlack -> Color.Black
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val adaptiveTextColor = if (glassConfig.textColor.isSpecified) {
        glassConfig.textColor
    } else if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color.Black
    } else {
        Color.White
    }

    val selectedIconColor = when {
        useGlass -> adaptiveTextColor
        else -> MaterialTheme.colorScheme.onPrimary
    }
    val unselectedContentColor = when {
        useGlass -> adaptiveTextColor.copy(alpha = 0.65f)
        pureBlack -> Color.White.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val selectedLabelColor = when {
        useGlass -> adaptiveTextColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    val selectedChipColor = MaterialTheme.colorScheme.primary

    val tabBarContentModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = RoundedCornerShape(percent = 50),
        )
    } else {
        Modifier
    }

    val selectedTabKey = navigationItems.firstOrNull { screen ->
        isRouteSelected(currentRoute, screen.route, navigationItems)
    }?.route

    val accessoryContentColor = when {
        useGlass -> adaptiveTextColor
        pureBlack -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    val expandedAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? =
        if (showPlayerAccessory) {
            { accessoryModifier, _ ->
                FloatingMiniPlayer(
                    isInline = false,
                    contentColor = accessoryContentColor,
                    onClick = onAccessoryClick,
                    modifier = accessoryModifier.fillMaxWidth().then(tabBarContentModifier),
                )
            }
        } else {
            null
        }

    FloatingTabBar(
        isInline = false,
        selectedTabKey = selectedTabKey,
        modifier = modifier,
        tabBarContentModifier = tabBarContentModifier,
        expandedAccessory = expandedAccessory,
        colors = FloatingTabBarDefaults.colors(
            backgroundColor = backgroundColor,
            accessoryBackgroundColor = backgroundColor,
        ),
        contentKey = listOf(selectedTabKey, navigationItems, selectedChipColor, unselectedContentColor),
    ) {
        navigationItems.forEach { screen ->
            val isSelected = screen.route == selectedTabKey
            tab(
                key = screen.route,
                title = {
                    Text(
                        text = stringResource(screen.titleId),
                        color = if (isSelected) selectedLabelColor else unselectedContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                icon = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) selectedChipColor else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isSelected) screen.iconIdActive else screen.iconIdInactive
                            ),
                            contentDescription = stringResource(screen.titleId),
                            tint = if (isSelected) selectedIconColor else unselectedContentColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                onClick = { onItemClick(screen, isSelected) },
            )
        }
    }
}
