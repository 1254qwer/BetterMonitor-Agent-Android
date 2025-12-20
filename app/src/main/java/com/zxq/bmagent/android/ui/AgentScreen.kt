package com.zxq.bmagent.android.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zxq.bmagent.android.data.AgentRepository
import com.zxq.bmagent.android.service.AgentService

@Composable
fun AgentScreen(repository: AgentRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isRunning by repository.isRunning.collectAsState()
    val isConnected by repository.isConnected.collectAsState()
    val logs by repository.logs.collectAsState()

    // Local state for inputs to allow editing before saving/starting
    // We initialize from repository values.
    // Note: If repository values change remotely, this won't update unless we react to it.
    // For simplicity, we assume single source of truth is user input -> repository.
    var serverId by remember { mutableStateOf(repository.serverId) }
    var serverKey by remember { mutableStateOf(repository.serverKey) }
    var serverUrl by remember { mutableStateOf(repository.serverUrl) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Better Monitor Agent", style = MaterialTheme.typography.headlineMedium)
        Text(
                text =
                        if (isConnected) "Status: Connected"
                        else if (isRunning) "Status: Connecting..." else "Status: Stopped",
                color =
                        if (isConnected) Color.Green
                        else if (isRunning) Color.Yellow else Color.Red,
                modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    repository.serverUrl = it
                },
                label = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
                value = serverId,
                onValueChange = {
                    serverId = it
                    repository.serverId = it
                },
                label = { Text("Server ID") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
                value = serverKey,
                onValueChange = {
                    serverKey = it
                    repository.serverKey = it
                },
                label = { Text("Secret Key") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(
                    onClick = {
                        val intent = Intent(context, AgentService::class.java)
                        if (isRunning) {
                            intent.action = AgentService.ACTION_STOP
                            context.startForegroundService(intent)
                        } else {
                            intent.action = AgentService.ACTION_START
                            context.startForegroundService(intent)
                        }
                    },
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor =
                                            if (isRunning) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary
                            ),
                    modifier = Modifier.weight(1f)
            ) { Text(if (isRunning) "Stop Agent" else "Start Agent") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Logs:", style = MaterialTheme.typography.titleMedium)

        Surface(
                modifier =
                        Modifier.fillMaxWidth()
                                .weight(1f)
                                .background(Color.Black, shape = RoundedCornerShape(8.dp))
                                .padding(top = 8.dp),
                color = Color.Black // Terminal-like background
        ) {
            val listState = rememberLazyListState()

            // Auto-scroll to bottom when logs change
            LaunchedEffect(logs.size) {
                if (logs.isNotEmpty()) {
                    listState.animateScrollToItem(logs.size - 1)
                }
            }

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(logs) { log ->
                    Text(
                            text = log,
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                    )
                }
            }
        }
    }
}
