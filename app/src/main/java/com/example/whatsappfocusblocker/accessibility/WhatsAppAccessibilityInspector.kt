package com.example.whatsappfocusblocker.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Utility to inspect WhatsApp AccessibilityNodeInfo trees safely and find actionable controls.
 *
 * Privacy Invariants:
 * - Scoped strictly to navigation, action menus, and confirmation dialogs.
 * - Never reads message content, personal conversations, or chat history.
 */
object WhatsAppAccessibilityInspector {
    private const val TAG = "WABlocker"

    /**
     * Resolves the genuine root AccessibilityNodeInfo of the WhatsApp window.
     * Guards against evaluating background/overlay windows from the host application.
     */
    fun findWhatsAppRoot(service: AccessibilityService, event: AccessibilityEvent? = null): AccessibilityNodeInfo? {
        // 1. If event is from WhatsApp, trace its source to the root of that window
        if (event != null) {
            val source = event.source
            if (source != null && source.packageName?.toString()?.contains("whatsapp") == true) {
                var current: AccessibilityNodeInfo = source
                while (current.parent != null) {
                    current = current.parent
                }
                return current
            }
        }

        // 2. Check rootInActiveWindow
        val activeRoot = service.rootInActiveWindow
        if (activeRoot != null && activeRoot.packageName?.toString()?.contains("whatsapp") == true) {
            return activeRoot
        }

        // 3. Search interactive windows for WhatsApp window root
        try {
            for (window in service.windows) {
                val root = window.root
                if (root != null && root.packageName?.toString()?.contains("whatsapp") == true) {
                    return root
                }
            }
        } catch (_: Exception) {}

        return activeRoot ?: event?.source
    }

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

            // Chat title header identification (scoped strictly to header text views)
            if (viewId.contains("conversation_contact_name") ||
                viewId.contains("conversation_title") ||
                viewId.contains("action_bar_title") ||
                viewId.contains("contact_name")
            ) {
                val candidate = if (!text.isNullOrBlank()) text else contentDesc
                if (!candidate.isNullOrBlank() && chatTitle == null) {
                    chatTitle = candidate
                }
            }

            // Fallback for toolbar header TextViews
            if (chatTitle == null && className.contains("TextView") && !text.isNullOrBlank()) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                if (viewId.contains("conversation_contact") || viewId.contains("title") ||
                    (bounds.top in 100..380 && bounds.left in 150..850)
                ) {
                    if (!text.contains(":") &&
                        !text.contains("online", ignoreCase = true) &&
                        !text.contains("typing", ignoreCase = true) &&
                        !text.contains("last seen", ignoreCase = true)
                    ) {
                        chatTitle = text
                    }
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
                contentDesc?.contains("আরও বিকল্প", ignoreCase = true) == true ||
                viewId.contains("menuitem_overflow")
            ) {
                hasOverflow = true
            }

            // Menu items in popup menu (both text and button contentDescription)
            val isStatusBanner = text?.contains("unblock", ignoreCase = true) == true ||
                                 text?.contains("blocked this", ignoreCase = true) == true ||
                                 contentDesc?.contains("unblock", ignoreCase = true) == true ||
                                 contentDesc?.contains("blocked this", ignoreCase = true) == true
            if (!isStatusBanner) {
                if (className.contains("TextView") && (viewId.contains("title") || viewId.contains("menu_item"))) {
                    text?.let { menuItems.add(it) }
                }
                if ((text?.equals("More", ignoreCase = true) == true || text?.equals("Block", ignoreCase = true) == true ||
                     text?.equals("আরও") == true || text?.equals("ব্লক") == true ||
                     contentDesc?.equals("More button", ignoreCase = true) == true ||
                     contentDesc?.equals("Block button", ignoreCase = true) == true) &&
                    (className.contains("TextView") || className.contains("Button"))
                ) {
                    val item = text ?: contentDesc
                    item?.let { menuItems.add(it) }
                }
            }

            // Block confirmation dialog
            if (viewId.contains("block_confirmation_dialog_root") ||
                viewId.contains("block_dialog_block_btn") ||
                (text?.contains("Block", ignoreCase = true) == true && text.contains("?")) ||
                (text?.contains("ব্লক", ignoreCase = true) == true && text.contains("?")) ||
                viewId.contains("dialog") ||
                className.contains("Dialog")
            ) {
                isBlockDialog = true
                if (viewId.contains("dialog_title") || dialogTitle == null) {
                    dialogTitle = text
                }
            }

            // Checkbox for "Report to WhatsApp"
            if (className.contains("CheckBox", ignoreCase = true) || viewId.contains("checkbox")) {
                hasReportCheck = true
                isReportChecked = node.isChecked
                val label = (text ?: "") + " " + (contentDesc ?: "")
                if (!label.contains("Report", ignoreCase = true) &&
                    !label.contains("রিপোর্ট", ignoreCase = true) &&
                    !viewId.contains("checkbox") &&
                    !viewId.contains("report")
                ) {
                    isReportAmbiguous = true
                }
            }

            // Confirmation positive "Block" button
            if (viewId.contains("block_dialog_block_btn") ||
                (className.contains("Button", ignoreCase = true) &&
                 (text.equals("Block", ignoreCase = true) ||
                  text.equals("BLOCK", ignoreCase = true) ||
                  text?.contains("ব্লক") == true))
            ) {
                hasPositiveBlock = true
            }

            // System feedback banner: "You blocked this person. Tap to unblock." or "You blocked this contact"
            if (text?.contains("You blocked this person", ignoreCase = true) == true ||
                text?.contains("blocked this person", ignoreCase = true) == true ||
                text?.contains("You blocked this contact", ignoreCase = true) == true ||
                text?.contains("blocked this contact", ignoreCase = true) == true ||
                text?.contains("Tap to unblock", ignoreCase = true) == true ||
                text?.contains("আনব্লক করতে ট্যাপ করুন") == true ||
                text?.contains("আপনি এই ব্যক্তিকে ব্লক করেছেন") == true ||
                text?.contains("আপনি এই পরিচিতিকে ব্লক করেছেন") == true
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

    /**
     * Traverses the target node and its parents to find the nearest clickable container
     * and performs ACTION_CLICK.
     */
    fun performClickOnNodeOrAncestor(node: AccessibilityNodeInfo?): Boolean {
        var current: AccessibilityNodeInfo? = node
        var steps = 0
        while (current != null && steps < 8) {
            if (current.isClickable) {
                val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "Clicked node [id=${current.viewIdResourceName}, class=${current.className}] -> success=$clicked")
                return clicked
            }
            current = current.parent
            steps++
        }
        return false
    }

    fun findAndClickOverflow(root: AccessibilityNodeInfo?): Boolean {
        val node = findFirstNode(root) { n ->
            val contentDesc = n.contentDescription?.toString()
            val viewId = n.viewIdResourceName?.lowercase() ?: ""
            (contentDesc?.contains("More options", ignoreCase = true) == true ||
             contentDesc?.contains("More", ignoreCase = true) == true ||
             contentDesc?.contains("আরও বিকল্প", ignoreCase = true) == true ||
             viewId.contains("menuitem_overflow"))
        }
        val result = performClickOnNodeOrAncestor(node)
        Log.i(TAG, "findAndClickOverflow: found=${node != null}, clicked=$result")
        return result
    }

    fun findAndClickMenuItem(root: AccessibilityNodeInfo?, targetText: String): Boolean {
        val node = findFirstNode(root) { n ->
            val text = n.text?.toString()
            val contentDesc = n.contentDescription?.toString()
            val viewId = n.viewIdResourceName?.lowercase() ?: ""
            val isBanner = text?.contains("unblock", ignoreCase = true) == true ||
                           text?.contains("blocked this", ignoreCase = true) == true ||
                           contentDesc?.contains("unblock", ignoreCase = true) == true ||
                           contentDesc?.contains("blocked this", ignoreCase = true) == true
            if (isBanner) return@findFirstNode false

            (text?.equals(targetText, ignoreCase = true) == true ||
             text?.contains(targetText, ignoreCase = true) == true ||
             contentDesc?.contains("$targetText button", ignoreCase = true) == true ||
             contentDesc?.contains(targetText, ignoreCase = true) == true ||
             (targetText.equals("Block", ignoreCase = true) && (text?.contains("ব্লক") == true || contentDesc?.contains("ব্লক") == true)) ||
             (targetText.equals("More", ignoreCase = true) && (text?.contains("আরও") == true || contentDesc?.contains("আরও") == true)))
        }
        val result = performClickOnNodeOrAncestor(node)
        Log.i(TAG, "findAndClickMenuItem('$targetText'): found=${node != null}, clicked=$result")
        return result
    }

    fun findAndClickSafetyBannerBlock(root: AccessibilityNodeInfo?): Boolean {
        val node = findFirstNode(root) { n ->
            val text = n.text?.toString()
            val viewId = n.viewIdResourceName?.lowercase() ?: ""
            (text?.contains("Block", ignoreCase = true) == true || text?.contains("ব্লক") == true) &&
                (viewId.contains("banner") || viewId.contains("safety") || viewId.contains("block"))
        }
        val result = performClickOnNodeOrAncestor(node)
        Log.i(TAG, "findAndClickSafetyBannerBlock: found=${node != null}, clicked=$result")
        return result
    }

    fun findAndUncheckReportCheckbox(root: AccessibilityNodeInfo?): Boolean {
        val checkbox = findFirstNode(root) { n ->
            n.className?.toString()?.contains("CheckBox", ignoreCase = true) == true ||
            n.viewIdResourceName?.lowercase()?.contains("checkbox") == true
        } ?: return false

        return if (checkbox.isChecked) {
            val result = performClickOnNodeOrAncestor(checkbox)
            Log.i(TAG, "findAndUncheckReportCheckbox: wasChecked=true, clicked=$result")
            result
        } else {
            Log.i(TAG, "findAndUncheckReportCheckbox: already unchecked")
            true // already unchecked
        }
    }

    fun findAndClickPositiveBlock(root: AccessibilityNodeInfo?): Boolean {
        val node = findFirstNode(root) { n ->
            val text = n.text?.toString()
            val viewId = n.viewIdResourceName?.lowercase() ?: ""
            val className = n.className?.toString() ?: ""
            viewId.contains("block_dialog_block_btn") ||
            (className.contains("Button", ignoreCase = true) &&
             (text.equals("Block", ignoreCase = true) ||
              text.equals("BLOCK", ignoreCase = true) ||
              text?.contains("ব্লক") == true))
        }
        val result = performClickOnNodeOrAncestor(node)
        Log.i(TAG, "findAndClickPositiveBlock: found=${node != null}, clicked=$result")
        return result
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
