package com.example.nfcHost

import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nfcHost.ui.theme.MVPNFCHostTheme

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var urlToShare: String = ""
    private var isNfcActive: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize NFC adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Check if NFC is available on this device
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device", Toast.LENGTH_LONG).show()
        }

        setContent {
            var url by remember { mutableStateOf("") }
            var nfcActiveState by remember { mutableStateOf(false) }

            MVPNFCHostTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NFCUrlScreen(
                        url = url,
                        onUrlChange = {
                            url = it
                            urlToShare = it

                            // Update the URL in the HCE service
                            updateHceServiceUrl(it)
                        },
                        isNfcAvailable = nfcAdapter != null,
                        isNfcActive = nfcActiveState,
                        onNfcActiveChange = {
                            nfcActiveState = it
                            isNfcActive = it
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    /**
     * Updates the URL in the HCE service
     */
    private fun updateHceServiceUrl(url: String) {
        // Format the URL correctly (add https:// if needed)
        val formattedUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else if (url.isNotBlank()) {
            "https://$url"
        } else {
            ""
        }

        PilotHostApduService.setPayload(payload = formattedUrl, url = true)
        Log.d("MainActivity", "Updated URL in HCE service: $formattedUrl")
    }
}


@Composable
fun NFCUrlScreen(
    url: String,
    onUrlChange: (String) -> Unit,
    isNfcAvailable: Boolean,
    isNfcActive: Boolean,
    onNfcActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showUrlError by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App title
        Text(
            text = context.getString(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // URL input field
        OutlinedTextField(
            value = url,
            onValueChange = {
                onUrlChange(it)
                showUrlError = false
            },
            label = { Text(context.getString(R.string.url_label)) },
            placeholder = { Text(context.getString(R.string.url_hint)) },
            singleLine = true,
            isError = showUrlError,
            supportingText = {
                if (showUrlError) {
                    Text(
                        text = context.getString(R.string.url_validation_error),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Create,
                    contentDescription = "URL"
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // NFC Animation if active
        if (isNfcActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                PulsingAnimation()
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Share button
            Button(
                onClick = {
//                    if (!isValidUrl(url)) {
//                        showUrlError = true
//                        Toast.makeText(context, context.getString(R.string.empty_url_error), Toast.LENGTH_SHORT).show()
//                    } else {
                    onNfcActiveChange(true)
                    Toast.makeText(
                        context,
                        context.getString(R.string.ready_to_share),
                        Toast.LENGTH_LONG
                    ).show()
//                    }
                },
                enabled = isNfcAvailable && url.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = if (isNfcActive) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isNfcActive) context.getString(R.string.nfc_active) else context.getString(
                            R.string.share_button
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // Clear button
            OutlinedButton(
                onClick = {
                    onUrlChange("")
                    onNfcActiveChange(false)
                    showUrlError = false
                },
                modifier = Modifier.weight(0.4f)
            ) {
                Text(context.getString(R.string.clear_button))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Status message
        if (!isNfcAvailable) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = context.getString(R.string.nfc_not_available),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        } else if (isNfcActive) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = context.getString(R.string.nfc_active),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = context.getString(R.string.nfc_hold_instruction),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = context.getString(R.string.nfc_instructions),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // How it works section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = context.getString(R.string.how_it_works),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = context.getString(R.string.step1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = context.getString(R.string.step2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = context.getString(R.string.step3),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = context.getString(R.string.step4),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PulsingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "NFC pulse animation")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse alpha"
    )

    Box(
        modifier = Modifier
            .size(100.dp * scale)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Create,
            contentDescription = "NFC Active",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(36.dp)
        )
    }
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MVPNFCHostTheme {
        Greeting("Android")
    }
}