package com.example.chaos

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.chaos.domain.state.LiraViewModel
import com.example.chaos.navigation.LiraApp
import com.example.chaos.ui.theme.CHAOSTheme

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private val viewModel: LiraViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            CHAOSTheme {
                LiraApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableNfcReaderMode()
    }

    override fun onPause() {
        super.onPause()
        disableNfcReaderMode()
    }

    private fun enableNfcReaderMode() {
        nfcAdapter?.let { adapter ->
            if (adapter.isEnabled) {
                val flags = NfcAdapter.FLAG_READER_NFC_A or
                        NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or
                        NfcAdapter.FLAG_READER_NFC_V or
                        NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

                val options = Bundle()
                adapter.enableReaderMode(this, this, flags, options)
            }
        }
    }

    private fun disableNfcReaderMode() {
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag?) {
        if (tag == null) return
        val idBytes = tag.id ?: return
        val hexString = idBytes.joinToString(":") { "%02X".format(it) }
        Log.d("LiraNfc", "Discovered Tag UID: $hexString")

        runOnUiThread {
            if (viewModel.uiState.value.isPhoneNfcEnabled) {
                viewModel.handlePhoneNfcScanned(hexString)
            }
        }
    }
}