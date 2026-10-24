package com.example.whatsappfocusblocker.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Utility to inspect WhatsApp AccessibilityNodeInfo trees safely and find actionable controls.
 *
 * Privacy Invariants:
 * - Scoped strictly to navigation, action menus, and confirmation dialogs.
 * - Never reads message content, personal conversations, or chat history.
 */
object WhatsAppAccessibilityInspector {

    fun inspectCurrentScreen(root: AccessibilityNodeInfo?, packageName: String): WhatsAppScreenSnapshot {
        if (root == null) {
            return WhatsAppScreenSnapshot(packageName = packageName)
        }

        var chatTitle: String? = null
        var hasInvite = false
        var hasSafetyBanner = false
        var hasOverflow = false
        val menuItems = mutableListOf<String>()
        var isBlockDialog = false
        var dialogTitle: String? = null
        var hasReportCheck = false
        var isReportChecked = false
        var isReportAmbiguous = false
        var hasPositiveBlock = false
        var hasBlockedBanner = false

        fun traverse(node: AccessibilityNodeInfo, depth: Int = 0) {
            if (depth > 25) return

            val text = node.text?.toString()?.trim()
            val contentDesc = node.contentDescription?.toString()?.trim()
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val className = node.className?.toString() ?: ""

            // Chat title header identification
            if (viewId.contains("conversation_contact_name") ||
                viewId.contains("conversation_title") ||
                viewId.contains("action_bar_title") ||
                viewId.contains("toolbar")
            ) {
                if (!text.isNullOrBlank() && chatTitle == null) {
                    chatTitle = text
                }
            }

            // Contact not on WhatsApp prompt
            if (text?.contains("Invite to WhatsApp", ignoreCase = true) == true ||
                (text?.contains("Invite", ignoreCase = true) == true && viewId.contains("invite"))
            ) {
                hasInvite = true
            }

            // Safety tools banner "Block" button
            if (viewId.contains("safety_tools") ||
                (text.equals("Block", ignoreCase = true) && (viewId.contains("banner") || viewId.contains("safety")))
            ) {
                hasSafetyBanner = true
            }

            // Overflow menu 3-dots button
            if (contentDesc?.contains("More options", ignoreCase = true) == true ||
                contentDesc?.contains("More", ignoreCase = true) == true ||
                viewId.contains("menuitem_overflow")
            ) {
                hasOverflow = true
            }

            // Menu items in popup menu
            if (className.contains("TextView") && (viewId.contains("title") || viewId.contains("menu_item"))) {
                text?.let { menuItems.add(it) }
            }

            // Block confirmation dialog
            if ((text?.contains("Block", ignoreCase = true) == true && text.contains("?")) ||
                viewId.contains("dialog") ||
                className.contains("Dialog")
            ) {
                isBlockDialog = true
                dialogTitle = text
            }

            // Checkbox for "Report to WhatsApp"
            if (className.contains("CheckBox", ignoreCase = true)) {
                hasReportCheck = true
                isReportChecked = node.isChecked
                val label = (text ?: "") + " " + (contentDesc ?: "")
                if (!label.contains("Report", ignoreCase = true) && !viewId.contains("report")) {
                    isReportAmbiguous = true
                }
            }

            // Confirmation positive "Block" button
            if (className.contains("Button", ignoreCase = true)) {
                if (text.equals("Block", ignoreCase = true) || text.equals("BLOCK", ignoreCase = true)) {
                    hasPositiveBlock = true
                }
            }

            // System feedback banner: "You blocked this contact"
            if (text?.contains("You blocked this contact", ignoreCase = true) == true ||
                text?.contains("blocked this contact", ignoreCase = true) == true ||
                text?.contains("Tap to unblock", ignoreCase = true) == true
            ) {
                hasBlockedBanner = true
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child, depth + 1)
            }
        }

        try {
            traverse(root)
        } catch (_: Exception) {
            // Safe traversal fallback
        }

        return WhatsAppScreenSnapshot(
            packageName = packageName,
            chatTitle = chatTitle,
            hasInviteToWhatsApp = hasInvite,
            hasSafetyBannerBlock = hasSafetyBanner,
            hasOverflowButton = hasOverflow,
            isOverflowMenuVisible = menuItems.isNotEmpty(),
            menuItems = menuItems,
            isBlockDialogVisible = isBlockDialog || hasPositiveBlock,
            blockDialogTitle = dialogTitle,
            hasReportCheckbox = hasReportCheck,
            isReportCheckboxChecked = isReportChecked,
            isReportCheckboxAmbiguous = isReportAmbiguous,
            hasPositiveBlockButton = hasPositiveBlock,
            hasBlockedStatusBanner = hasBlockedBanner
        )
    }

    fun findAndClickOverflow(root: AccessibilityNodeInfo?): Boolean {
        return findFirstNode(root) { node ->
            val contentDesc = node.contentDescription?.toString()
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            node.isClickable && (
                contentDesc?.contains("More options", ignoreCase = true) == true ||
                contentDesc?.contains("More", ignoreCase = true) == true ||
                viewId.contains("menuitem_overflow")
            )
        }?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    fun findAndClickMenuItem(root: AccessibilityNodeInfo?, targetText: String): Boolean {
        return findFirstNode(root) { node ->
            val text = node.text?.toString()
            text?.contains(targetText, ignoreCase = true) == true
        }?.let { node ->
            val clickableTarget = if (node.isClickable) node else node.parent
            clickableTarget?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        } ?: false
    }

    fun findAndClickSafetyBannerBlock(root: AccessibilityNodeInfo?): Boolean {
        return findFirstNode(root) { node ->
            val text = node.text?.toString()
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            text.equals("Block", ignoreCase = true) && (viewId.contains("banner") || viewId.contains("safety") || viewId.contains("block"))
        }?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    fun findAndUncheckReportCheckbox(root: AccessibilityNodeInfo?): Boolean {
        val checkbox = findFirstNode(root) { node ->
            node.className?.toString()?.contains("CheckBox", ignoreCase = true) == true
        } ?: return false

        return if (checkbox.isChecked) {
            checkbox.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            true // already unchecked
        }
    }

    fun findAndClickPositiveBlock(root: AccessibilityNodeInfo?): Boolean {
        return findFirstNode(root) { node ->
            val text = node.text?.toString()
            val className = node.className?.toString() ?: ""
            className.contains("Button", ignoreCase = true) &&
                (text.equals("Block", ignoreCase = true) || text.equals("BLOCK", ignoreCase = true))
        }?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    private fun findFirstNode(
        root: AccessibilityNodeInfo?,
        depth: Int = 0,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (root == null || depth > 25) return null
        if (predicate(root)) return root

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findFirstNode(child, depth + 1, predicate)
            if (found != null) return found
        }
        return null
    }
}
