package ua.purchasetracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ua.purchasetracker.ui.AppActions
import ua.purchasetracker.ui.EditRequest
import ua.purchasetracker.ui.NfcState
import ua.purchasetracker.ui.TapsumApp
import ua.purchasetracker.ui.TapsumTheme
import ua.purchasetracker.ui.UiEvents

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Toast.makeText(this, R.string.alerts_denied, Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        handleIntent(intent)
        val actions = AppActions(
            openListenerSettings = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            openNfcSettings = { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
            openLanguageSettings = ::openLanguageSettings,
            exportCsv = ::exportCsv,
            requestNotificationPermission = ::requestNotificationPermission,
        )
        setContent { TapsumTheme { TapsumApp(actions) } }
    }

    override fun onResume() {
        super.onResume()
        UiEvents.listenerEnabled.value =
            NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val adapter = nfcAdapter
        UiEvents.nfcState.value = when {
            adapter == null -> NfcState.UNAVAILABLE
            !adapter.isEnabled -> NfcState.DISABLED
            else -> NfcState.OK
        }
        // Поки додаток відкритий, телефон читає NFC-мітки (наліпка біля каси, кавоварки тощо).
        if (adapter != null && adapter.isEnabled) {
            adapter.enableReaderMode(
                this,
                ::onTagScanned,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                null,
            )
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.let { if (it.isEnabled) it.disableReaderMode(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** NFC-мітка з URI purchase://add?amount=..&merchant=.. відкрила додаток. */
    @Suppress("DEPRECATION")
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return
        val uri = intent.data ?: return
        val tag: Tag? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        UiEvents.editRequest.value = requestFromUri(uri, tag?.id?.toHex())
    }

    /** Фоновий потік NFC: мітку піднесли до телефона. */
    private fun onTagScanned(tag: Tag) {
        val tagId = tag.id?.toHex() ?: return
        val uri = try {
            Ndef.get(tag)?.cachedNdefMessage?.records?.firstNotNullOfOrNull { it.toUri() }
        } catch (_: Exception) {
            null
        }
        val request = if (uri != null && uri.scheme == "purchase") {
            requestFromUri(uri, tagId)
        } else {
            EditRequest(merchant = AppSettings.tagMerchant(this, tagId).orEmpty(), tagId = tagId)
        }
        UiEvents.editRequest.value = request
    }

    private fun requestFromUri(uri: Uri, tagId: String?): EditRequest = EditRequest(
        amount = uri.getQueryParameter("amount").orEmpty(),
        merchant = uri.getQueryParameter("merchant") ?: tagId?.let { AppSettings.tagMerchant(this, it) }.orEmpty(),
        currency = uri.getQueryParameter("currency")?.uppercase(),
        tagId = tagId,
    )

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Toast.makeText(this, R.string.alerts_enabled, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openLanguageSettings() {
        if (Build.VERSION.SDK_INT >= 33) {
            startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }

    private fun exportCsv() {
        val csv = Repo.toCsv(Repo.purchases.value) { id -> categoryLabel(this, Repo.categoryById(id)) }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Tapsum.csv")
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        startActivity(Intent.createChooser(send, getString(R.string.export_csv)))
    }
}

private fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }
