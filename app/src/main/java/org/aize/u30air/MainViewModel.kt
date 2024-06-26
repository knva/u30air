import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

class MyCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieStore[url.host] = cookies
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore[url.host] ?: emptyList()
    }
}


class MainViewModel(private val context: Context) : ViewModel() {
    private val sharedPref: SharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)

    private val _ipAddress = MutableStateFlow(sharedPref.getString("ipAddress", "") ?: "")
    val ipAddress: StateFlow<String> = _ipAddress


    private val _battery = MutableLiveData<String>()
    val battery: LiveData<String> = _battery

    private val _traffic = MutableLiveData<String>()
    val traffic: LiveData<String> = _traffic

    //是否充电
    private val _charging = MutableLiveData<Boolean>()
    val charging: LiveData<Boolean> =  _charging

    //网络制式
    private val _network_type = MutableLiveData<String>()
    val network_type: LiveData<String> = _network_type

    //运营商
    private val _network_provider_fullname = MutableLiveData<String>()
    val network_provider_fullname: LiveData<String> = _network_provider_fullname

    //信号强度
    private val _Z5g_rsrp = MutableLiveData<String>()
    val Z5g_rsrp: LiveData<String> = _Z5g_rsrp

    //network_lte_rsrp
    private val _network_lte_rsrp = MutableLiveData<String>()
    val network_lte_rsrp: LiveData<String> = _network_lte_rsrp

    //realtime_time
    private val _realtime_time = MutableLiveData<String>()
    val realtime_time: LiveData<String> = _realtime_time

    //flux_realtime_rx_thrpt
    private val _flux_realtime_rx_thrpt = MutableLiveData<String>()
    val flux_realtime_rx_thrpt: LiveData<String> = _flux_realtime_rx_thrpt

    //flux_realtime_tx_thrpt
    private val _flux_realtime_tx_thrpt= MutableLiveData<String>()
    val flux_realtime_tx_thrpt: LiveData<String> = _flux_realtime_tx_thrpt

    //上一次的json
    private val _lastJson = MutableLiveData<String>()

    init {
        startTimer()
    }
    fun saveIpAddress(ip: String) {
        viewModelScope.launch {
            with(sharedPref.edit()) {
                putString("ipAddress", ip)
                apply()
            }
            _ipAddress.value = ip
        }
    }
    private  suspend fun sendGetRequest(myipAddress: String): String {
        var tmpaddress = myipAddress
        if(tmpaddress==""){
            tmpaddress = "192.168.0.1"
        }
        val client = OkHttpClient.Builder()
            .cookieJar(MyCookieJar())
            .build()

        val url1 = "http://$tmpaddress/"
        val request1 = Request.Builder()
            .url(url1)
            .build()

        val url = "http://$tmpaddress/goform/goform_get_cmd_process?multi_data=1&isTest=true&sms_received_flag_flag=0&sts_received_flag_flag=0&cmd=battery_charging%2Cbattery_vol_percent%2Cbattery_value%2Cbattery_pers%2Cflux_monthly_rx_bytes%2Cflux_monthly_tx_bytes%2Cnetwork_type%2Cnetwork_provider_fullname%2CZ5g_rsrp%2Crealtime_time%2Cflux_realtime_rx_thrpt%2Cflux_realtime_tx_thrpt%2Cnetwork_lte_rsrp&_=1719295421412"
        val request = Request.Builder()
            .url(url) .header("accept", "application/json, text/javascript, */*; q=0.01")
            .header("accept-language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("x-requested-with", "XMLHttpRequest")
            .header("referer", "http://"+tmpaddress+"/index.html")
            .build()

        // 使用 async 并发执行两个请求
        val responses1 = withContext(Dispatchers.IO) {
            listOf(
//                async { client.newCall(request1).execute() },
                async { client.newCall(request).execute() },
            ).awaitAll()
        }

        // 获取第二个请求的响应体
        return responses1[0].body?.string() ?: ""
    }
    private fun startTimer() {
        viewModelScope.launch {
            repeat(Int.MAX_VALUE) {
                try {
                    delay(30) // 每5秒更新一次
                  // val jsondata ="{\"battery_charging\":\"1\",\"battery_vol_percent\":\"100\",\"battery_value\":\"100\",\"battery_pers\":\"\",\"monthly_tx_bytes\":2675052511,\"monthly_rx_bytes\":23600180829,\"monthly_time\":359779,\"flux_monthly_rx_bytes\":23600180829,\"flux_monthly_tx_bytes\":2675052511,\"flux_monthly_time\":359779}"
                    val jsondata = sendGetRequest(ipAddress.value)
                    _lastJson.value=jsondata
                    decodeJson(jsondata)
                }catch ( e: Exception){

                    //如果上一次的json不等于空
                    if(_lastJson.value!=null){
                        val jsondata = _lastJson.value!!
                        decodeJson(jsondata)
                    }else {
                        // 网络请求失败
                        _battery.value = "0"
                        _traffic.value = "0"
                        _charging.value = false
                        _network_type.value = "无"
                        _network_provider_fullname.value = "无服务"
                        _Z5g_rsrp.value = "-999"
                        _realtime_time.value = "0"
                        _flux_realtime_rx_thrpt.value = "0"
                        _flux_realtime_tx_thrpt.value = "0"
                    }
                }finally {

                    delay(1000) // 每5秒更新一次
                }

            }
        }
    }

    private fun decodeJson(jsondata: String) {
        val jsonObject = JSONObject(jsondata)

        // 是否充电
        val chargingValue = jsonObject.getString("battery_charging").toInt()

        // 电池值
        val batteryValue = jsonObject.getString("battery_value")

        // 流量是rx+tx
        val trafficValue = (jsonObject.getLong("monthly_rx_bytes") + jsonObject.getLong("monthly_tx_bytes")) / 1024 / 1024 / 1024

        // 网络类型
        val networkType = jsonObject.optString("network_type")

        // 网络提供商全名
        val networkProviderFullName = jsonObject.optString("network_provider_fullname")

        // 5g rsrp
        val Z5gRsrp = jsonObject.optString("Z5g_rsrp")

        //4g rsrp
        val networkLteRsrp = jsonObject.optString("network_lte_rsrp")

        // 实时时间
        val realtimeTime = jsonObject.optString("realtime_time")

        // 实时接收吞吐量
        val fluxRealtimeRxThrpt = jsonObject.optString("flux_realtime_rx_thrpt").replace("}", "")

        // 实时发送吞吐量
        val fluxRealtimeTxThrpt = jsonObject.optString("flux_realtime_tx_thrpt")

        // 模拟更新电池和流量数据
        _battery.value = batteryValue
        _traffic.value = trafficValue.toString()
        _charging.value = chargingValue == 1
        _network_type.value = networkType
        _network_provider_fullname.value = networkProviderFullName
        _Z5g_rsrp.value = Z5gRsrp
        _network_lte_rsrp.value = networkLteRsrp
        _realtime_time.value = realtimeTime
        _flux_realtime_rx_thrpt.value = fluxRealtimeRxThrpt
        _flux_realtime_tx_thrpt.value = fluxRealtimeTxThrpt
    }
}
