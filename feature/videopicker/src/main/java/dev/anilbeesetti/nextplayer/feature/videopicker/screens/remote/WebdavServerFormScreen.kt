package dev.anilbeesetti.nextplayer.feature.videopicker.screens.remote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.WebdavServer
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@Composable
fun WebdavServerFormScreen(
    serverId: Long,
    onNavigateUp: () -> Unit,
    viewModel: RemoteViewModel = hiltViewModel(),
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val initialServer = remember(serverId, servers) {
        if (serverId != 0L) servers.find { it.id == serverId } else null
    }

    var isTimeout by remember(serverId) { mutableStateOf(false) }

    LaunchedEffect(serverId, initialServer) {
        if (serverId != 0L && initialServer == null) {
            kotlinx.coroutines.delay(2000L)
            isTimeout = true
        }
    }

    when {
        serverId != 0L && initialServer == null -> {
            WebdavServerLoadingScreen(
                isTimeout = isTimeout,
                onNavigateUp = onNavigateUp,
            )
        }
        else -> {
            WebdavServerFormContent(
                initialServer = initialServer,
                uiState = uiState,
                onNavigateUp = onNavigateUp,
                onSave = { server -> viewModel.saveServer(server, onSuccess = onNavigateUp) },
                onTestConnection = viewModel::testConnection,
                onClearMessages = viewModel::clearMessages,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebdavServerLoadingScreen(
    isTimeout: Boolean,
    onNavigateUp: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Server") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(NextIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            if (isTimeout) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Server not found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "The requested server could not be loaded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                CircularProgressIndicator()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebdavServerFormContent(
    initialServer: WebdavServer?,
    uiState: RemoteUiState,
    onNavigateUp: () -> Unit,
    onSave: (WebdavServer) -> Unit,
    onTestConnection: (WebdavServer) -> Unit,
    onClearMessages: () -> Unit,
) {
    val isEditMode = initialServer != null
    val snackbarHostState = remember { SnackbarHostState() }

    var name by rememberSaveable { mutableStateOf(initialServer?.name ?: "") }
    var host by rememberSaveable { mutableStateOf(initialServer?.host ?: "") }
    var port by rememberSaveable { mutableStateOf(initialServer?.port?.toString() ?: "443") }
    var path by rememberSaveable { mutableStateOf(initialServer?.path ?: "/") }
    var username by rememberSaveable { mutableStateOf(initialServer?.username ?: "") }
    var password by rememberSaveable { mutableStateOf(initialServer?.password ?: "") }
    var useSsl by rememberSaveable { mutableStateOf(initialServer?.useSsl ?: true) }
    var allowSelfSigned by rememberSaveable { mutableStateOf(initialServer?.allowSelfSigned ?: false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var hostError by remember { mutableStateOf<String?>(null) }
    var portError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearMessages()
        }
    }
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearMessages()
        }
    }

    fun onSslToggle(enabled: Boolean) {
        useSsl = enabled
        if (port == "443" && !enabled) port = "80"
        else if (port == "80" && enabled) port = "443"
        if (!enabled) allowSelfSigned = false
    }

    fun validate(): Boolean {
        var valid = true
        nameError = if (name.isBlank()) { valid = false; "Server name is required" } else null
        hostError = if (host.isBlank()) { valid = false; "Host is required" } else null
        val portInt = port.toIntOrNull()
        portError = when {
            portInt == null -> { valid = false; "Enter a valid port number" }
            portInt !in 1..65535 -> { valid = false; "Port must be between 1 and 65535" }
            else -> null
        }
        return valid
    }

    fun buildServer() = WebdavServer(
        id = initialServer?.id ?: 0L,
        name = name.trim(),
        host = host.trim(),
        port = port.toInt(),
        path = path.trim().ifBlank { "/" },
        username = username.trim(),
        password = password,
        useSsl = useSsl,
        allowSelfSigned = allowSelfSigned,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Server" else "Add Server") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(NextIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("General")

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = null },
                label = { Text("Server name") },
                placeholder = { Text("e.g., My NAS") },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            SectionLabel("Connection")

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Use HTTPS (SSL)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (useSsl) "Secure connection" else "Insecure connection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = useSsl, onCheckedChange = ::onSslToggle)
            }

            AnimatedVisibility(visible = useSsl) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow self-signed certificates", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Allows untrusted self-signed SSL connections (e.g., personal NAS)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = allowSelfSigned,
                        onCheckedChange = { allowSelfSigned = it },
                    )
                }
            }

            OutlinedTextField(
                value = host,
                onValueChange = { host = it; hostError = null },
                label = { Text("Host") },
                placeholder = { Text("e.g., 192.168.1.100 or nas.example.com") },
                isError = hostError != null,
                supportingText = hostError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it; portError = null },
                    label = { Text("Port") },
                    isError = portError != null,
                    supportingText = portError?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.weight(0.35f),
                )

                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Path") },
                    placeholder = { Text("/") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.weight(0.65f),
                )
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            SectionLabel("Authentication")

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                placeholder = { Text("Anonymous if empty") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) NextIcons.VisibilityOff
                            else NextIcons.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            val previewUrl = remember(host, port, path, useSsl) {
                if (host.isNotBlank()) {
                    val scheme = if (useSsl) "https" else "http"
                    val normalizedPath = if (path.startsWith("/")) path else "/$path"
                    val p = port.toIntOrNull() ?: (if (useSsl) 443 else 80)
                    "$scheme://$host:$p$normalizedPath"
                } else ""
            }
            if (previewUrl.isNotBlank()) {
                Text(
                    text = previewUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { if (validate()) onTestConnection(buildServer()) },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .height(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text("Test Connection")
            }

            Button(
                onClick = { if (validate()) onSave(buildServer()) },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isEditMode) "Save Changes" else "Add Server")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}