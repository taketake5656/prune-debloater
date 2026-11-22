package io.github.cyb3rl00m.prune

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.cyb3rl00m.prune.core.model.PackageStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 全画面を巡回してスクリーンショットを撮り、実際の UI で無効化と取り消しを 1 回ずつ行う。 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UiWalkthroughTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val repo get() = rule.activity.repository

    private fun waitTag(tag: String, timeout: Long = 20_000) =
        rule.waitUntilAtLeastOneExists(hasTestTag(tag), timeout)

    private fun click(tag: String) {
        waitTag(tag)
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun scrollTo(list: String, tag: String) {
        waitTag(list)
        rule.onNodeWithTag(list).performScrollToNode(hasTestTag(tag))
    }

    private fun confirmHighIfShown() {
        if (rule.onAllNodesWithTag("confirm_high").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithTag("confirm_high").performClick()
            rule.waitForIdle()
        }
    }

    private fun waitScan() {
        rule.waitUntil(60_000) { repo.scan.value.lastScan > 0 && !repo.scan.value.scanning }
        rule.waitForIdle()
    }

    @Test fun walkthrough() {
        Shots.shell("cmd uimode night yes")
        rule.waitForIdle()
        waitScan()
        waitTag("screen_home")
        Shots.take("01-home")
        click("device_card"); waitTag("screen_compat"); Shots.take("01b-compatibility")
        click("back")
        click("tab_debloat"); waitTag("screen_debloat"); Shots.take("02-debloat")
        scrollTo("list_debloat", "level_2"); click("level_2"); waitTag("screen_customize"); Shots.take("03-customize-balanced")
        // 素のエミュレータには Samsung/Google のパッケージが少ないため、Balanced のリストが空になることがある。
        val canReview = runCatching { rule.onNodeWithTag("review_changes").assertIsEnabled() }.isSuccess
        if (canReview) {
            click("review_changes"); waitTag("screen_review"); Shots.take("04-review-no-access")
            click("back")
        }
        click("back")
        scrollTo("list_debloat", "level_4"); click("level_4"); waitTag("screen_customize"); Shots.take("05-customize-custom")
        click("back")
        click("tab_apps"); waitTag("screen_apps"); Shots.take("06-packages")
        click("tab_optimize"); waitTag("screen_optimize"); Shots.take("07-optimize")
        click("section_ANIMATIONS"); waitTag("screen_section_ANIMATIONS"); Shots.take("08-animations")
        click("back")
        click("tab_restore"); waitTag("screen_restore"); Shots.take("09-restore")
        click("tab_home"); click("open_settings"); waitTag("screen_settings"); Shots.take("10-settings")
        click("back")
        scrollTo("list_home", "setup_access"); click("setup_access"); waitTag("screen_setup"); Shots.take("11-setup")
        click("back")
    }

    @Test fun disableAndUndoThroughUi() {
        Shots.shell("cmd uimode night yes")
        waitScan()
        val pkg = listOf("com.android.egg", "com.android.dreams.basic", "com.android.dreams.phototable", "com.android.wallpaper.livepicker")
            .first { p -> repo.app(p)?.let { it.facts.isSystem && it.status == PackageStatus.ENABLED && !it.isLocked } == true }
        repo.access.testShell = UiAutomationShell()
        repo.access.refresh()
        try {
            click("tab_apps")
            waitTag("search")
            rule.onNodeWithTag("search").performTextInput(pkg)
            click("pkg_$pkg"); waitTag("screen_app"); Shots.take("12-app-detail")
            scrollTo("list_app", "action_disable"); click("action_disable")
            waitTag("screen_review"); Shots.take("13-review-disable")
            confirmHighIfShown()
            click("apply")
            waitTag("screen_result", 60_000); Shots.take("14-result")
            assertEquals("disabled", repo.executor.packageState(pkg))
            click("undo_all")
            waitTag("screen_review")
            click("apply")
            waitTag("screen_result", 60_000)
            assertEquals("enabled", repo.executor.packageState(pkg))
            click("done")
            click("back")
            click("tab_restore"); waitTag("screen_restore"); Shots.take("15-restore-history")
        } finally {
            repo.access.testShell = null
            repo.access.refresh()
            Shots.shell("pm enable $pkg")
        }
    }

    @Test fun lightTheme() {
        Shots.shell("cmd uimode night no")
        waitScan()
        rule.waitForIdle()
        waitTag("screen_home")
        Shots.take("16-home-light")
        click("tab_debloat"); waitTag("screen_debloat"); Shots.take("17-debloat-light")
    }
}
