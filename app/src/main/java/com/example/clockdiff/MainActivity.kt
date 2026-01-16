package com.example.clockdiff

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.example.clockdiff.ui.theme.ClockdiffTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); setContent { ClockdiffTheme { Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding -> NtpLogUI(modifier = Modifier.padding(innerPadding), activity = this) } } } }
    suspend fun getNtpTimestamps(server: String = "ntp.aliyun.com", port: Int = 123): DoubleArray = withContext(Dispatchers.IO) {
        val TIME_1970 = 2208988800L
        val buffer = ByteArray(48)
        buffer[0] = 0b00100011
        val address = InetAddress.getByName(server)
        DatagramSocket().use { socket ->
            socket.soTimeout = 5000
            val t1 = SystemClock.elapsedRealtimeNanos() / 1_000_000.0
            val requestPacket = DatagramPacket(buffer, buffer.size, address, port)
            socket.send(requestPacket)
            val responseBuffer = ByteArray(48)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responsePacket)
            val t4 = SystemClock.elapsedRealtimeNanos() / 1_000_000.0
            val bb = ByteBuffer.wrap(responseBuffer); bb.order(ByteOrder.BIG_ENDIAN)
            bb.position(32); val seconds2 = bb.int.toLong() and 0xffffffffL; val fraction2 = bb.int.toLong() and 0xffffffffL; val t2 = (seconds2 - TIME_1970 + fraction2 / (2.0.pow(32))) * 1000.0
            bb.position(40); val seconds3 = bb.int.toLong() and 0xffffffffL; val fraction3 = bb.int.toLong() and 0xffffffffL; val t3 = (seconds3 - TIME_1970 + fraction3 / (2.0.pow(32))) * 1000.0
            return@withContext doubleArrayOf(t1, t2, t3, t4)
        }
    }
    private fun Double.pow(p: Int) = this.pow(p.toDouble())
}

@Composable
fun NtpLogUI(modifier: Modifier = Modifier, activity: MainActivity) {
    var logs by remember { mutableStateOf(listOf<String>()) }
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val ts = activity.getNtpTimestamps()
                val line = "t1=%d t2=%d t3=%d t4=%d".format(ts[0].toLong(), ts[1].toLong(), ts[2].toLong(), ts[3].toLong())
                logs = logs + line
                delay(100)
                scrollState.scrollTo(scrollState.maxValue)
            } catch (e: Exception) {
                logs = logs + "Error: ${e.message}"
            }
            delay(1000)
        }
    }
    SelectionContainer(modifier = modifier.verticalScroll(scrollState).fillMaxSize()) { Text(text = logs.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
}

@Preview(showBackground = true)
@Composable
fun PreviewUI() { ClockdiffTheme { Text("Preview", fontFamily = FontFamily.Monospace, fontSize = 8.sp) } }