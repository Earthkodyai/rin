package io.github.earthkodyai.rinalarm.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.earthkodyai.rinalarm.R

/** One block of the privacy notice, as the screen draws it. */
sealed interface PrivacyBlock {
  data class Title(val text: String) : PrivacyBlock

  data class Heading(val text: String) : PrivacyBlock

  data class Paragraph(val text: String) : PrivacyBlock

  data class Bullet(val text: String) : PrivacyBlock
}

/**
 * Reads PRIVACY.md (6.3) into blocks: `# ` title, `## ` headings, `- ` bullets, other lines as paragraphs. The notice
 * is written to need nothing more, and PrivacyTextTest keeps it that way.
 */
object PrivacyText {
  fun parse(markdown: String): List<PrivacyBlock> =
    markdown.lines().map(String::trim).filter(String::isNotEmpty).map { line ->
      when {
        line.startsWith("## ") -> PrivacyBlock.Heading(line.removePrefix("## "))
        line.startsWith("# ") -> PrivacyBlock.Title(line.removePrefix("# "))
        line.startsWith("- ") -> PrivacyBlock.Bullet(line.removePrefix("- "))
        else -> PrivacyBlock.Paragraph(line)
      }
    }

  /** The notice this build carries, or null when it cannot be read. */
  fun load(context: Context): List<PrivacyBlock>? =
    runCatching { context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) } }.getOrNull()

  const val ASSET = "privacy/PRIVACY.md"
}

/** "How Rin uses your data" (09-security-privacy): the same notice as on the web, readable offline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val blocks = remember { PrivacyText.load(context) }
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.privacy_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.editor_back))
          }
        },
      )
    }
  ) { padding ->
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (blocks == null) {
        Text(stringResource(R.string.privacy_missing), style = MaterialTheme.typography.bodyMedium)
      } else {
        blocks.forEach { Block(it) }
      }
    }
  }
}

@Composable
private fun Block(block: PrivacyBlock) {
  when (block) {
    // The top bar already names the page.
    is PrivacyBlock.Title -> Unit
    is PrivacyBlock.Heading ->
      Text(block.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    is PrivacyBlock.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyMedium)
    is PrivacyBlock.Bullet ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Text(block.text, style = MaterialTheme.typography.bodyMedium)
      }
  }
}
