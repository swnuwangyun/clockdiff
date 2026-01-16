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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    var initialTimestamps: LongArray? = null
    var initialRtt: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); setContent { ClockdiffTheme { Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding -> NtpLogUI(modifier = Modifier.padding(innerPadding), activity = this) } } } }
    suspend fun getNtpTimestamps(server: String = "ntp.aliyun.com", port: Int = 123): LongArray = withContext(Dispatchers.IO) {
        val TIME_1970 = 2208988800L; val buffer = ByteArray(48); buffer[0] = 0b00100011; val address = InetAddress.getByName(server)
        DatagramSocket().use { socket -> socket.soTimeout = 5000; val t1 = SystemClock.elapsedRealtimeNanos() / 1_000_000L; val requestPacket = DatagramPacket(buffer, buffer.size, address, port); socket.send(requestPacket)
            val responseBuffer = ByteArray(48); val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size); socket.receive(responsePacket); val t4 = SystemClock.elapsedRealtimeNanos() / 1_000_000L
            val bb = ByteBuffer.wrap(responseBuffer); bb.order(ByteOrder.BIG_ENDIAN)
            bb.position(32); val seconds2 = bb.int.toLong() and 0xffffffffL; val fraction2 = bb.int.toLong() and 0xffffffffL; val t2 = (seconds2 - TIME_1970) * 1000L + ((fraction2 * 1000L) shr 32)
            bb.position(40); val seconds3 = bb.int.toLong() and 0xffffffffL; val fraction3 = bb.int.toLong() and 0xffffffffL; val t3 = (seconds3 - TIME_1970) * 1000L + ((fraction3 * 1000L) shr 32)
            return@withContext longArrayOf(t1, t2, t3, t4)
        }
    }
}

@Composable
fun NtpLogUI(modifier: Modifier = Modifier, activity: MainActivity) {
    var logs by remember { mutableStateOf(listOf<String>()) }
    val scrollState = rememberScrollState()
    val checkpoints = listOf(1, 2, 5, 10, 20, 30, 60)
    val doneFlags = remember { mutableStateMapOf<Int, Boolean>().apply { checkpoints.forEach { put(it,false) } } }
    val rttList = remember { mutableListOf<Pair<Long, LongArray>>() }
    var counter by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val nowstr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        logs = logs + "Start Time: $nowstr"
        while (true) {
            try {
                val ts = activity.getNtpTimestamps()
                val rtt = ts[3] - ts[0]
                counter++
                if (counter >= 10) {
                    logs = logs + "rtt=$rtt t3=${ts[2]} t4=${ts[3]}"
                    counter = 0
                }
                if (activity.initialTimestamps == null) {
                    rttList.add(rtt to ts)
                    if (rttList.size > 3) rttList.removeAt(0)
                    if (rttList.size == 3) {
                        val minRtt = rttList.minOf { it.first }
                        val minEntry = rttList.first { it.first == minRtt }
                        activity.initialTimestamps = minEntry.second
                        activity.initialRtt = minEntry.first
                        logs = logs + "initial rtt=${activity.initialRtt} t3=${minEntry.second[2]} t4=${minEntry.second[3]}"
                    }
                } else {
                    val t3start = activity.initialTimestamps!![2]
                    val t4start = activity.initialTimestamps!![3]
                    val elapsedMin = (ts[3] - t4start) / 60000.0
                    checkpoints.forEach { cp ->
                        if (!doneFlags[cp]!! && elapsedMin >= cp && activity.initialRtt != null && kotlin.math.abs(rtt - activity.initialRtt!!) <= 10) {
                            val driftMs = ((ts[3] - t4start) - (ts[2] - t3start))
                            logs = logs + "checkpoint rtt=$rtt t3=${ts[2]} t4=${ts[3]}"
                            logs = logs + "%d min cost=%d drift=%d ms".format(cp, (ts[3] - t4start), driftMs)
                            doneFlags[cp] = true
                        }
                    }
                }
                scrollState.scrollTo(scrollState.maxValue)
            } catch (e: Exception) {
                logs = logs + "Error: ${e.message}"
            }
            delay(2000) // 改成定时器2秒
        }
    }
    SelectionContainer(modifier = modifier.verticalScroll(scrollState).fillMaxSize()) { Text(text = logs.joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
}

@Preview(showBackground = true)
@Composable
fun PreviewUI() { ClockdiffTheme { Text("Preview", fontFamily = FontFamily.Monospace, fontSize = 12.sp) } }