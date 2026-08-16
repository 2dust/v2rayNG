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
            item {
                Text(
                    text = stringResource(R.string.translators_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                val githubLogin = contributor.githubLogin
                Text(
                    text = contributor.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (githubLogin != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (githubLogin != null) {
                                Modifier.clickable(role = Role.Button) {
                                    Utils.openUri(context, "https://github.com/$githubLogin")
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
    val githubLogin: String? = null
)

private fun githubContributor(login: String) = Contributor(
    displayName = "@$login",
    githubLogin = login
)

private val translationCredits = listOf(
    TranslationCredit(
        language = "العربية (Arabic)",
        contributors = listOf(githubContributor("MrIbrahem"))
    ),
    TranslationCredit(
        language = "বাংলা (Bengali)",
        contributors = listOf(githubContributor("CodeWithTamim"))
    ),
    TranslationCredit(
        language = "لری بختیاری (Luri Bakhtiari)",
        contributors = listOf(
            githubContributor("hosseinabaspanah"),
            githubContributor("CodeWithTamim")
        )
    ),
    TranslationCredit(
        language = "فارسی (Persian)",
        contributors = listOf(
            githubContributor("TheMRVX"),
            githubContributor("Skh-web6982"),
            githubContributor("Ptechgithub"),
            Contributor("@Pk-web6936"),
            Contributor("@alphax-hue3682"),
            Contributor("@phoenix6936"),
            Contributor("@DecorativeFamily"),
            Contributor("@decorativeman"),
            githubContributor("mh292929"),
            githubContributor("Amir-yazdanmanesh"),
            githubContributor("CUMOON"),
            githubContributor("hadi-norouzi"),
            Contributor("Vahid Farid")
        )
    ),
    TranslationCredit(
        language = "Русский (Russian)",
        contributors = listOf(
            githubContributor("solokot"),
            githubContributor("Liniya"),
            githubContributor("eliotcougar")
        )
    ),
    TranslationCredit(
        language = "Tiếng Việt (Vietnamese)",
        contributors = listOf(
            githubContributor("admarty"),
            githubContributor("user09283"),
            githubContributor("yuhan6665")
        )
    ),
    TranslationCredit(
        language = "简体中文 (Simplified Chinese)",
        contributors = listOf(
            githubContributor("2dust"),
            githubContributor("Yau08")
        )
    ),
    TranslationCredit(
        language = "繁體中文 (Traditional Chinese)",
        contributors = listOf(
            githubContributor("2dust"),
            githubContributor("Yau08"),
            githubContributor("Fubuki0x10DE")
        )
    )
)
