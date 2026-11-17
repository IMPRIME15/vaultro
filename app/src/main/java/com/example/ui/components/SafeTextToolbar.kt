package com.example.ui.components

import android.graphics.Rect as AndroidRect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

private const val MENU_ID_COPY = 0
private const val MENU_ID_PASTE = 1
private const val MENU_ID_CUT = 2
private const val MENU_ID_SELECT_ALL = 3

/**
 * Drop-in replacement for Compose's default AndroidTextToolbar that prevents duplicate
 * FloatingActionMode.finish() calls (e.g., when Copy/Paste/Cut or rapid navigation/dialog
 * dismissal already cleared DecorView's mFloatingActionMode).
 */
class SafeAndroidTextToolbar(
    private val view: View
) : TextToolbar {

    private var actionMode: ActionMode? = null
    private var isFinishingMode = false
    private val destroyedModes: MutableSet<ActionMode> =
        Collections.newSetFromMap(WeakHashMap())

    private var rect: Rect = Rect.Zero
    private var onCopyRequested: (() -> Unit)? = null
    private var onPasteRequested: (() -> Unit)? = null
    private var onCutRequested: (() -> Unit)? = null
    private var onSelectAllRequested: (() -> Unit)? = null

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    private val actionModeCallback = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            if (mode == null || menu == null || !view.isAttachedToWindow) {
                return false
            }
            val hasAnyAction = onCopyRequested != null ||
                onPasteRequested != null ||
                onCutRequested != null ||
                onSelectAllRequested != null
            if (!hasAnyAction) {
                return false
            }
            syncAllMenuItems(menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            if (mode == null || menu == null || !view.isAttachedToWindow) {
                return false
            }
            syncAllMenuItems(menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            when (item?.itemId) {
                MENU_ID_COPY -> onCopyRequested?.invoke()
                MENU_ID_PASTE -> onPasteRequested?.invoke()
                MENU_ID_CUT -> onCutRequested?.invoke()
                MENU_ID_SELECT_ALL -> onSelectAllRequested?.invoke()
                else -> return false
            }
            // Only finish if the action callback did not already hide/destroy this mode.
            finishModeSafely(mode)
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            if (mode != null) {
                destroyedModes.add(mode)
            }
            if (actionMode === mode) {
                actionMode = null
                status = TextToolbarStatus.Hidden
            }
        }

        override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: AndroidRect?) {
            outRect?.set(
                rect.left.roundToInt(),
                rect.top.roundToInt(),
                rect.right.roundToInt(),
                rect.bottom.roundToInt()
            )
        }
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        this.rect = rect
        this.onCopyRequested = onCopyRequested
        this.onPasteRequested = onPasteRequested
        this.onCutRequested = onCutRequested
        this.onSelectAllRequested = onSelectAllRequested

        val hasAnyAction = onCopyRequested != null ||
            onPasteRequested != null ||
            onCutRequested != null ||
            onSelectAllRequested != null

        if (!view.isAttachedToWindow || !hasAnyAction) {
            hide()
            return
        }

        val current = actionMode
        if (current != null && !destroyedModes.contains(current)) {
            status = TextToolbarStatus.Shown
            current.invalidate()
            current.invalidateContentRect()
        } else {
            actionMode = null
            status = TextToolbarStatus.Shown
            val started = view.startActionMode(actionModeCallback, ActionMode.TYPE_FLOATING)
            if (started != null) {
                actionMode = started
            } else {
                status = TextToolbarStatus.Hidden
            }
        }
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        val current = actionMode ?: return
        actionMode = null
        finishModeSafely(current)
    }

    private fun finishModeSafely(mode: ActionMode?) {
        if (mode == null) return
        if (isFinishingMode) return
        if (!destroyedModes.add(mode)) return
        if (actionMode === mode) {
            actionMode = null
        }
        status = TextToolbarStatus.Hidden
        if (!view.isAttachedToWindow) return
        isFinishingMode = true
        try {
            mode.finish()
        } finally {
            isFinishingMode = false
        }
    }

    private fun syncAllMenuItems(menu: Menu) {
        syncMenuItem(menu, MENU_ID_COPY, 0, android.R.string.copy, onCopyRequested != null)
        syncMenuItem(menu, MENU_ID_PASTE, 1, android.R.string.paste, onPasteRequested != null)
        syncMenuItem(menu, MENU_ID_CUT, 2, android.R.string.cut, onCutRequested != null)
        syncMenuItem(menu, MENU_ID_SELECT_ALL, 3, android.R.string.selectAll, onSelectAllRequested != null)
    }

    private fun syncMenuItem(
        menu: Menu,
        itemId: Int,
        order: Int,
        titleRes: Int,
        enabled: Boolean
    ) {
        val existing = menu.findItem(itemId)
        if (enabled) {
            if (existing == null) {
                menu.add(Menu.NONE, itemId, order, titleRes)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }
        } else if (existing != null) {
            menu.removeItem(itemId)
        }
    }
}

@Composable
fun ProvideSafeTextToolbar(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val safeToolbar = remember(view) { SafeAndroidTextToolbar(view) }

    DisposableEffect(view, safeToolbar) {
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                safeToolbar.hide()
            }
        }
        view.addOnAttachStateChangeListener(attachListener)
        onDispose {
            view.removeOnAttachStateChangeListener(attachListener)
            safeToolbar.hide()
        }
    }

    CompositionLocalProvider(LocalTextToolbar provides safeToolbar) {
        content()
    }
}
