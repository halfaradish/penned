package dev.penned.app.ui.icons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.penned.app.ui.theme.PennedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 图标集总览截图：自绘图标改路径后用它一眼过目（人工评审用，不是回归基线）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconGalleryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val icons: List<Pair<String, ImageVector>> = listOf(
        "Back" to PennedIcons.Back,
        "ChevronRight" to PennedIcons.ChevronRight,
        "ChevronDown" to PennedIcons.ChevronDown,
        "Search" to PennedIcons.Search,
        "Plus" to PennedIcons.Plus,
        "Close" to PennedIcons.Close,
        "Check" to PennedIcons.Check,
        "Trash" to PennedIcons.Trash,
        "Edit" to PennedIcons.Edit,
        "Calendar" to PennedIcons.Calendar,
        "Wallet" to PennedIcons.Wallet,
        "Pie" to PennedIcons.Pie,
        "Home" to PennedIcons.Home,
        "Person" to PennedIcons.Person,
        "Tag" to PennedIcons.Tag,
        "Lock" to PennedIcons.Lock,
        "Info" to PennedIcons.Info,
        "Database" to PennedIcons.Database,
        "Transfer" to PennedIcons.Transfer,
        "MoreVert" to PennedIcons.MoreVert,
    )

    @Test
    fun captureGallery() {
        composeRule.setContent {
            PennedTheme {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Gallery(icons)
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/test-results/roborazzi/icon_gallery.png")
    }

    @Composable
    private fun Gallery(items: List<Pair<String, ImageVector>>) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            items.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { (name, icon) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, contentDescription = name, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
