package com.smartwheel.app

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val link = Link()
    private lateinit var steering: SteeringSensor
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        steering = SteeringSensor(this)
        // Режим Wi-Fi "низкая задержка": отключает энергосбережение радио, пока приложение на экране
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                   else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifiLock = wm.createWifiLock(mode, "smartwheel").apply { setReferenceCounted(false) }
        steering.onAngle = { link.value = it }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Stage3Screen(link, steering) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        steering.start()
        wifiLock?.acquire()
    }

    override fun onPause() {
        super.onPause()
        steering.stop()
        wifiLock?.release()
        link.stop() // не шлём устаревший угол, если приложение свернули
    }
}

@Composable
fun Stage3Screen(link: Link, steering: SteeringSensor) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("sw", 0) }

    var ip by remember { mutableStateOf(prefs.getString("ip", "192.168.1.100") ?: "") }
    var running by remember { mutableStateOf(false) }
    var sent by remember { mutableLongStateOf(0L) }
    var rtt by remember { mutableFloatStateOf(-1f) }
    var err by remember { mutableStateOf<String?>(null) }
    var angle by remember { mutableFloatStateOf(0f) }
    var hz by remember { mutableIntStateOf(0) }
    var upZ by remember { mutableFloatStateOf(0f) }
    var invert by remember { mutableStateOf(steering.invert) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(50)
            running = link.running
            sent = link.sent
            rtt = link.rttMs
            err = link.error
            angle = steering.angle
            hz = steering.hz
            upZ = steering.upZ
        }
    }

    Row(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("SMART WHEEL - этап 3", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it; prefs.edit().putString("ip", it).apply() },
                label = { Text("IP-адрес компьютера") },
                singleLine = true,
                enabled = !running,
                modifier = Modifier.padding(top = 12.dp)
            )
            Button(
                onClick = { if (running) link.stop() else link.start(ip.trim(), 9876) },
                modifier = Modifier.padding(vertical = 12.dp)
            ) { Text(if (running) "Остановить" else "Начать отправку") }
            Text("Отправлено пакетов: $sent")
            Text(
                if (rtt >= 0) "Задержка туда-обратно: " + "%.1f".format(rtt) + " мс"
                else "Ответа от ПК нет"
            )
            err?.let { Text("Ошибка: $it", color = Color.Red) }
        }

        Column(Modifier.weight(1.4f).verticalScroll(rememberScrollState())) {
            Text("Угол руля")
            Text("%.1f".format(angle) + "°", style = MaterialTheme.typography.displayMedium)
            Canvas(Modifier.fillMaxWidth().height(32.dp).padding(vertical = 4.dp)) {
                val w = size.width
                val h = size.height
                drawRect(Color(0xFF2A2A2A), size = size)
                drawRect(Color.White, topLeft = Offset(w / 2 - 2f, 0f), size = Size(4f, h))
                val x = w / 2 + (angle.coerceIn(-90f, 90f) / 90f) * (w / 2)
                drawRect(Color(0xFFFF5A1F), topLeft = Offset(x - 8f, 0f), size = Size(16f, h))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { steering.calibrate() }) { Text("Калибровка центра") }
                Checkbox(
                    checked = invert,
                    onCheckedChange = { invert = it; steering.invert = it },
                    modifier = Modifier.padding(start = 16.dp)
                )
                Text("Инвертировать")
            }
            Text("Датчик: $hz Гц", modifier = Modifier.padding(top = 8.dp))
            if (!steering.available) Text("В телефоне нет нужного датчика", color = Color.Red)
            if (abs(upZ) > 0.6f) Text("Держите телефон вертикально, экраном к себе", color = Color.Yellow)
        }
    }
}
