package ua.purchasetracker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ua.purchasetracker.R

/** Перший запуск: що це, як дати доступ до сповіщень, дозвіл на нагадування про бюджет. */
@Composable
fun Onboarding(actions: AppActions, onDone: () -> Unit) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val listenerEnabled by UiEvents.listenerEnabled.collectAsStateWithLifecycle()

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (page) {
                        0 -> {
                            Image(
                                painterResource(R.drawable.ic_launcher),
                                contentDescription = null,
                                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(32.dp)),
                            )
                            Spacer(Modifier.height(28.dp))
                            Text("Tapsum", style = MaterialTheme.typography.displaySmall)
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.slogan), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(20.dp))
                            Text(stringResource(R.string.onb_intro), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        1 -> {
                            Text("🔔", style = MaterialTheme.typography.displayMedium)
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.onb_access_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.onb_access_text), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            ) {
                                Text(
                                    stringResource(R.string.onb_restricted),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                            if (listenerEnabled) {
                                Text("✓ " + stringResource(R.string.access_granted), color = MaterialTheme.colorScheme.primary)
                            } else {
                                Button(onClick = actions.openListenerSettings) { Text(stringResource(R.string.grant_access)) }
                            }
                        }
                        else -> {
                            Text("📊", style = MaterialTheme.typography.displayMedium)
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.onb_budget_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.onb_budget_text), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(20.dp))
                            OutlinedButton(onClick = actions.requestNotificationPermission) { Text(stringResource(R.string.allow_alerts)) }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                repeat(3) { i ->
                    Box(
                        Modifier.padding(4.dp).size(if (i == pager.currentPage) 10.dp else 8.dp).clip(CircleShape)
                            .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDone) { Text(stringResource(R.string.skip)) }
                Spacer(Modifier.weight(1f).width(0.dp))
                Button(onClick = {
                    if (pager.currentPage < 2) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else onDone()
                }) {
                    Text(stringResource(if (pager.currentPage < 2) R.string.next else R.string.start))
                }
            }
        }
    }
}
