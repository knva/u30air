package org.aize.u30air

import MainViewModel
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.aize.u30air.ui.theme.U30AirTheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(context) as T
    }
}
fun calculateBatteryColor(progress: Float): Color {

    // 计算红色和绿色之间的插值
    val redComponent = (1 - progress) * 255 // 从255到0
    val greenComponent = progress * 255 // 从0到255

    // 创建新的颜色
    val interpolatedColor = Color(red = redComponent.toInt(), green = greenComponent.toInt(), blue = 0, alpha = 255)
    return interpolatedColor
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            U30AirTheme {

                MainScreen(context = LocalContext.current)

            }
        }
    }
}
fun formatTraffic(value: Float): String {
    return if (value >= 1024*1024) {
        String.format("%.2f MB/s", value / (1024*1024))
    } else {
        String.format("%.2f KB/s", value/1024)
    }
}
@Composable
fun MainScreen(context: Context) {
    val viewModel: MainViewModel = remember { MainViewModelFactory(context) }.let { factory ->
        viewModel(factory = factory)
    }
    // 初始化SharedPreferences
    val sharedPref = LocalContext.current.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
    val savedIpAddress = remember {
        mutableStateOf(sharedPref.getString("ipAddress", ""))
    }
//MainScreen画一个界面，所有元素居中，第一行 ip 让用户输入并且持久保存下来，下次打开还是这个，第二行 电量：数值 % 第三行流量：数值GB
//第四行按钮，点击后获取当前的电量和流量，然后发送到服务器，服务器返回一个json，显示在界面上
//第五行显示服务器返回的json

    val ipAddress = remember { mutableStateOf("") }
    val battery = viewModel.battery.observeAsState()
    val traffic = viewModel.traffic.observeAsState()
    //充电状态
    val charging = viewModel.charging.observeAsState()

//    //网络制式
//    private val _network_type = MutableLiveData<String>()
    val network_type = viewModel.network_type.observeAsState()
//
//    //运营商
//    private val _network_provider_fullname = MutableLiveData<String>()
    val network_provider_fullname = viewModel.network_provider_fullname.observeAsState()
//
//    //信号强度
//    private val _Z5g_rsrp = MutableLiveData<String>()
    val Z5g_rsrp = viewModel.Z5g_rsrp.observeAsState()
//
//    //realtime_time
//    private val _realtime_time = MutableLiveData<String>()
    val realtime_time = viewModel.realtime_time.observeAsState()
//
//    //flux_realtime_rx_thrpt
//    private val _flux_realtime_rx_thrpt = MutableLiveData<String>()
    val fluxRealtimeRx = viewModel.flux_realtime_rx_thrpt.observeAsState()
//
//    //flux_realtime_tx_thrpt
//    private val _flux_realtime_tx_thrpt= MutableLiveData<String>()
    val fluxRealtimeTx = viewModel.flux_realtime_tx_thrpt.observeAsState()

    if(savedIpAddress.value != ""){
        ipAddress.value = savedIpAddress.value!!
    }else{
        ipAddress.value = "192.168.0.1"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "中兴U30AIR状态", modifier = Modifier.padding(top = 32.dp),

            style = MaterialTheme.typography.headlineMedium, // 使用稍大的字体

        )

        TextField(
            value = ipAddress.value,
            onValueChange = { ipAddress.value = it },
            label = { Text("后台IP地址：") },
            modifier = Modifier.padding(top = 16.dp)
        )
        Button(onClick = { /* 保存IP地址的逻辑 */
            //保存ip地址 ，下次加载读取
            with(sharedPref.edit()) {
                putString("ipAddress", ipAddress.value)
                apply()
            }
        }) {
            Text("保存")
        }
        // 电量进度条
        var batteryValue = battery.value?.toIntOrNull()
        if(batteryValue == null){
            batteryValue = 0
        }
        val batteryProgress = if (batteryValue != null) batteryValue / 100f else 0f
        val batteryColor = calculateBatteryColor(batteryProgress)

        LinearProgressIndicator(
            progress = { batteryProgress },
            modifier = Modifier.padding(top = 16.dp),
            color = batteryColor,
        )
        //开机事件 秒换算为小时
        val realtimeHour = realtime_time.value?.toLongOrNull()?.div(3600)
        val realtimeMin = realtime_time.value?.toLongOrNull()?.rem(3600)?.div(60)
        val realtimeSec = realtime_time.value?.toLongOrNull()?.rem(60)
        Text(text = "开机时间：${realtimeHour}小时${realtimeMin}分${realtimeSec}秒", modifier = Modifier.padding(top = 16.dp))



        Text(text = "电量：${battery.value} %", modifier = Modifier.padding(top = 16.dp))
        Text(text = "充电状态：${charging.value}", modifier = Modifier.padding(top = 16.dp))

        // 流量进度条
        var trafficValue = traffic.value?.toFloatOrNull()
        if(trafficValue == null){
            trafficValue = 0f
        }
        val maxTraffic = 200f // 假设最大流量为200GB
        val trafficProgress = if (trafficValue != null) (maxTraffic - trafficValue) / maxTraffic else 0f
        val trafficColor = calculateBatteryColor(trafficProgress)
        LinearProgressIndicator(
            progress = { trafficProgress },
            modifier = Modifier.padding(top = 16.dp),
            color = trafficColor,
        )
        Text(text = "流量剩余：${maxTraffic - trafficValue!!} GB", modifier = Modifier.padding(top = 16.dp))
        Text(text = "流量使用：${traffic.value} GB", modifier = Modifier.padding(top = 16.dp))

        // 网络制式，信号强度 运营商
        Text(text = "网络制式：${network_type.value}", modifier = Modifier.padding(top = 16.dp))
        Text(text = "运营商：${network_provider_fullname.value}", modifier = Modifier.padding(top = 16.dp))
        Text(text = "信号强度：${Z5g_rsrp.value} dbm", modifier = Modifier.padding(top = 16.dp))
        //上传 下载流量显示，除以1024/s
        val rx = fluxRealtimeRx.value?.toFloatOrNull() ?: 0f
        val tx = fluxRealtimeTx.value?.toFloatOrNull() ?: 0f

        val rxFormatted = formatTraffic(rx)
        val txFormatted = formatTraffic(tx)

        // 显示到一行，例如 ↑ xx.xx/Kb/s ↓ xx.xx/Kb/s
        Text(text = "↑ $txFormatted  ↓ $rxFormatted", modifier = Modifier.padding(top = 16.dp))
        // 添加额外的内容来填充空间
        Spacer(modifier = Modifier.weight(1f))

        Text(text = "随便玩玩@出品", modifier = Modifier.padding(top = 16.dp))

    }
}



@Preview(showBackground = true)
@Composable
fun PreviewMainScreen() {
    MainScreen(context = LocalContext.current)
}
