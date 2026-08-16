package com.v2ray.ang.ui

import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.util.Utils

class TranslatorsActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        TranslatorsScreen(onBackClick = { finish() })
    }
}

@Composable
fun TranslatorsScreen(onBackClick: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_translators),
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(translationCredits, key = { it.language }) { credit ->
                TranslationCreditCard(credit)
            }
            item {
                AuditNoteCard()
            }
            item {
                NavigationBarsSpacer()
            }
        }
    }
}

@Composable
private fun TranslationCreditCard(credit: TranslationCredit) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = credit.language,
                style = MaterialTheme.typography.titleMedium
            )
            credit.contributors.forEach { contributor ->
                val linkUrl = contributor.linkUrl
                Text(
                    text = contributor.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (linkUrl != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (linkUrl != null) {
                                Modifier.clickable(role = Role.Button) {
                                    Utils.openUri(context, linkUrl)
                                }
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun AuditNoteCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.translators_audit_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.translators_audit_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private data class TranslationCredit(
    val language: String,
    val contributors: List<Contributor>
)

private data class Contributor(
    val displayName: String,
    val linkUrl: String?
)

private fun contributor(displayName: String, linkUrl: String?) = Contributor(displayName, linkUrl)

private val translationCredits = listOf(
    TranslationCredit(
        language = "العربية (Arabic)",
        contributors = listOf(contributor("@MrIbrahem", "https://github.com/MrIbrahem"))
    ),
    TranslationCredit(
        language = "বাংলা (Bengali)",
        contributors = listOf(contributor("@CodeWithTamim", "https://github.com/CodeWithTamim"))
    ),
    TranslationCredit(
        language = "لری بختیاری (Luri Bakhtiari)",
        contributors = listOf(
            contributor("@hosseinabaspanah", "https://github.com/hosseinabaspanah"),
            contributor("@CodeWithTamim", "https://github.com/CodeWithTamim")
        )
    ),
    TranslationCredit(
        language = "فارسی (Persian)",
        contributors = listOf(
            contributor("@TheMRVX", "https://github.com/TheMRVX"),
            contributor("@Skh-web6982", "https://github.com/Skh-web6982"),
            contributor("@Ptechgithub", "https://github.com/Ptechgithub"),
            contributor("@Pk-web6936", null),
            contributor("@alphax-hue3682", null),
            contributor("@phoenix6936", null),
            contributor("@DecorativeFamily", null),
            contributor("@decorativeman", null),
            contributor("@mh292929", "https://github.com/mh292929"),
            contributor("@Amir-yazdanmanesh", "https://github.com/Amir-yazdanmanesh"),
            contributor("@CUMOON", "https://github.com/CUMOON"),
            contributor("@hadi-norouzi", "https://github.com/hadi-norouzi"),
            contributor("Vahid Farid", null)
        )
    ),
    TranslationCredit(
        language = "Русский (Russian)",
        contributors = listOf(
            contributor("@solokot", "https://github.com/solokot"),
            contributor("@Liniya", "https://github.com/Liniya"),
            contributor("@eliotcougar", "https://github.com/eliotcougar")
        )
    ),
    TranslationCredit(
        language = "Tiếng Việt (Vietnamese)",
        contributors = listOf(
            contributor("@admarty", "https://github.com/admarty"),
            contributor("@user09283", "https://github.com/user09283"),
            contributor("@yuhan6665", "https://github.com/yuhan6665")
        )
    ),
    TranslationCredit(
        language = "简体中文 (Simplified Chinese)",
        contributors = listOf(
            contributor("@2dust", "https://github.com/2dust"),
            contributor("@Yau08", "https://github.com/Yau08")
        )
    ),
    TranslationCredit(
        language = "繁體中文 (Traditional Chinese)",
        contributors = listOf(
            contributor("@2dust", "https://github.com/2dust"),
            contributor("@Yau08", "https://github.com/Yau08"),
            contributor("@Fubuki0x10DE", "https://github.com/Fubuki0x10DE")
        )
    )
)
