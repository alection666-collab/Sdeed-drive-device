package com.smartwheel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Stage2Screen() }
            }
        }
    }
}

@Composable
fun Stage2Screen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("sw", 0) }
    val link = remember { Link() }

    var ip by remember { mutableStateOf(prefs.getString("ip", "192.168.1.100") ?: "") }
    var value by remember { mutableFloatStateOf(0.5f) }
    var running by remember { mutableStateOf(false) }
    var sent by remember { mutableLongStateOf(0L) }
    var rtt by remember { mutableFloatStateOf(-1f) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            running = link.running
            sent = link.sent
            rtt = link.rttMs
            err = link.error
        }
    }
    DisposableEffect(Unit) { onDispose { link.stop() } }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("SMART WHEEL - этап 2", style = MaterialTheme.typography.titleLarge)
        Text("Проверка связи телефон -> ПК", Modifier.padding(bottom = 16.dp))
        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it; prefs.edit().putString("ip", it).apply() },
            label = { Text("IP-адрес компьютера") },
            singleLine = true,
            enabled = !running
        )
        Button(
            onClick = { if (running) link.stop() else link.start(ip.trim(), 9876) },
            modifier = Modifier.padding(vertical = 16.dp)
        ) { Text(if (running) "Остановить" else "Начать отправку") }

        Text("Ползунок: " + "%.2f".format(value))
        Slider(value = value, onValueChange = { value = it; link.value = it })
        Text("Отправлено пакетов: $sent")
        Text(
            if (rtt >= 0) "Задержка туда-обратно: " + "%.1f".format(rtt) + " мс"
            else "Ответа от ПК нет"
        )
        err?.let { Text("Ошибка: $it", color = Color.Red) }
    }
}
