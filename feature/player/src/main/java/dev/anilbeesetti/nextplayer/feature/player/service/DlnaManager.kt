package dev.anilbeesetti.nextplayer.feature.player.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface

enum class DlnaTransportState {
    PLAYING, PAUSED, STOPPED, TRANSITIONING, UNKNOWN;

    companion object {
        fun fromString(state: String?): DlnaTransportState = when(state?.uppercase()) {
            "PLAYING" -> PLAYING
            "PAUSED_PLAYBACK" -> PAUSED
            "STOPPED" -> STOPPED
            "TRANSITIONING" -> TRANSITIONING
            else -> UNKNOWN
        }
    }
}

enum class StopReason { NONE, DEVICE_UNREACHABLE }

data class DlnaPlaybackState(
    val isActive: Boolean = false,
    val mediaId: String = "",
    val transportState: DlnaTransportState = DlnaTransportState.UNKNOWN,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val currentDevice: DlnaManager.DlnaDevice? = null,
    val stopReason: StopReason = StopReason.NONE
) {
    val isPlaying get() = transportState == DlnaTransportState.PLAYING
}

object DlnaManager {

    private const val TAG = "DlnaManager"
    private const val PORT = 8080

    private var server: LocalMediaServer? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    var currentDevice: DlnaDevice? = null

    private val _playbackState = MutableStateFlow(DlnaPlaybackState())
    val playbackState = _playbackState.asStateFlow()

    private var pollingJob: kotlinx.coroutines.Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var currentCastingPath: String? = null

    private var cachedAvTransportUrl: String? = null
    private var cachedLocation: String? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    data class DlnaDevice(
        val name: String,
        val location: String,
        val address: String,
        val isTV: Boolean,
    )

    fun acquireMulticastLock(context: Context) {
        if (multicastLock?.isHeld == true) return
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("dlna_multicast").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    fun releaseMulticastLock() {
        if (multicastLock?.isHeld == true) {
            multicastLock?.release()
        }
        multicastLock = null
    }

    private fun acquireCastingLocks(context: Context) {
        val appContext = context.applicationContext

        if (wakeLock == null) {
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NextPlayer::DlnaWakeLock").apply {
                acquire(6 * 60 * 60 * 1000L)
            }
        }

        if (wifiLock == null) {
            val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "NextPlayer::DlnaWifiLock").apply {
                acquire()
            }
        }
    }

    private fun releaseCastingLocks() {
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (e: Exception) { Log.w(TAG, "WakeLock release error: $e") }
        wakeLock = null

        try { wifiLock?.takeIf { it.isHeld }?.release() } catch (e: Exception) { Log.w(TAG, "WifiLock release error: $e") }
        wifiLock = null
    }

    fun ensureCastingLocks(context: Context) {
        if (wakeLock?.isHeld != true || wifiLock?.isHeld != true) {
            acquireCastingLocks(context)
        }
    }

    suspend fun searchDevices(context: Context): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val discovered = mutableMapOf<String, String>()

        acquireMulticastLock(context)
        try {
            val socket = java.net.MulticastSocket()
            socket.timeToLive = 4
            socket.soTimeout = 3000

            val validInterfaces = NetworkInterface.getNetworkInterfaces()?.toList()
                ?.filter { iface ->
                    iface.isUp && !iface.isLoopback && !iface.isVirtual &&
                            iface.inetAddresses.toList()
                                .filterIsInstance<Inet4Address>()
                                .any { isPrivateIp(it) }
                } ?: emptyList()

            val group = java.net.InetAddress.getByName("239.255.255.250")

            val searchTargets = listOf(
                "urn:schemas-upnp-org:device:MediaRenderer:1",
                "urn:schemas-upnp-org:service:AVTransport:1",
            )

            coroutineScope {
                val sendJob = launch {
                    for (iface in validInterfaces) {
                        try {
                            socket.networkInterface = iface
                            for (st in searchTargets) {
                                val msg = "M-SEARCH * HTTP/1.1\r\n" +
                                        "HOST: 239.255.255.250:1900\r\n" +
                                        "MAN: \"ssdp:discover\"\r\n" +
                                        "MX: 2\r\n" +
                                        "ST: $st\r\n" +
                                        "\r\n"

                                val packet = java.net.DatagramPacket(msg.toByteArray(), msg.length, group, 1900)

                                repeat(3) {
                                    if (!isActive) return@launch
                                    socket.send(packet)
                                    delay(50)
                                }
                            }
                        } catch (e: java.net.SocketException) {
                            break
                        } catch (e: Exception) {
                            Log.w("DLNA_SEARCH", "Failed to send via ${iface.name}: ${e.message}")
                        }
                    }
                }

                try {
                    val buf = ByteArray(2048)
                    val deadline = System.currentTimeMillis() + 3000

                    while (System.currentTimeMillis() < deadline) {
                        try {
                            val packet = java.net.DatagramPacket(buf, buf.size)
                            socket.receive(packet)

                            val response = String(packet.data, 0, packet.length)
                            val address = packet.address.hostAddress ?: continue
                            val location = Regex("LOCATION:\\s*(.+)", RegexOption.IGNORE_CASE)
                                .find(response)?.groupValues?.get(1)?.trim() ?: continue

                            if (!discovered.containsKey(address)) {
                                discovered[address] = location
                            }
                        } catch (_: java.net.SocketTimeoutException) {
                            break
                        }
                    }
                } finally {
                    sendJob.cancel()
                    socket.close()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to discover DLNA devices: $e")
        } finally {
            releaseMulticastLock()
        }

        coroutineScope {
            discovered.map { (address, location) ->
                async {
                    fetchDeviceInfo(location, address)
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun fetchDeviceInfo(location: String, address: String): DlnaDevice {
        return try {
            val xml = java.net.URL(location).openConnection().run {
                connectTimeout = 1500
                readTimeout = 1500
                getInputStream().bufferedReader().readText()
            }

            val friendlyName = Regex("<friendlyName>([^<]+)</friendlyName>")
                .find(xml)?.groupValues?.get(1)?.trim()
            val manufacturer = Regex("<manufacturer>([^<]+)</manufacturer>")
                .find(xml)?.groupValues?.get(1)?.trim() ?: ""
            val modelName = Regex("<modelName>([^<]+)</modelName>")
                .find(xml)?.groupValues?.get(1)?.trim() ?: ""

            val isTV = listOf("Samsung", "LG", "Sony", "Philips", "Hisense", "TCL")
                .any { manufacturer.contains(it, true) }

            val displayName = when {
                !friendlyName.isNullOrBlank() -> friendlyName
                manufacturer.isNotBlank() && modelName.isNotBlank() -> "$manufacturer $modelName"
                else -> "DLNA Device ($address)"
            }

            DlnaDevice(
                name = displayName,
                location = location,
                address = address,
                isTV = isTV
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse device info ($address): $e")
            DlnaDevice("DLNA Device ($address)", location, address, false)
        }
    }

    private fun startPolling(device: DlnaDevice, initialPath: String) {
        pollingJob?.cancel()
        _playbackState.value = DlnaPlaybackState(isActive = true, currentDevice = device, mediaId = initialPath)

        pollingJob = scope.launch {
            var errorCount = 0
            while (isActive) {
                try {
                    val stateStr = pollPlaybackState(device)

                    if (stateStr == null) {
                        errorCount++
                        if (errorCount >= 5) {
                            _playbackState.update { it.copy(stopReason = StopReason.DEVICE_UNREACHABLE) }
                            stopCastingInternal()
                            break
                        }
                        delay(1000)
                        continue
                    }

                    errorCount = 0
                    val transportState = DlnaTransportState.fromString(stateStr)

                    var position = _playbackState.value.positionMs
                    var duration = _playbackState.value.durationMs

                    if (transportState == DlnaTransportState.PLAYING) {
                        pollPositionInfo(device)?.let { (pos, dur) ->
                            position = pos
                            duration = dur
                        }
                    }

                    _playbackState.update {
                        it.copy(
                            transportState = transportState,
                            positionMs = position,
                            durationMs = duration
                        )
                    }
                } catch (e: Exception) {
                    errorCount++
                    if (errorCount >= 3) {
                        _playbackState.update { it.copy(stopReason = StopReason.DEVICE_UNREACHABLE) }
                        stopCastingInternal()
                        break
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopCastingInternal() {
        cachedAvTransportUrl = null
        cachedLocation = null
        pollingJob?.cancel()
        _playbackState.update { current -> DlnaPlaybackState(stopReason = current.stopReason) }
        currentDevice = null
        stopServer()
        releaseCastingLocks()
    }

    suspend fun startCasting(
        context: Context,
        source: CastMediaSource,
        device: DlnaDevice,
        okHttpClient: OkHttpClient,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (source is CastMediaSource.LocalFile && !source.file.exists()) {
            onError("File not found")
            return
        }

        acquireCastingLocks(context)

        stopServer()
        try {
            server = LocalMediaServer(PORT, source, okHttpClient).apply {
                start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            }
        } catch (e: Exception) {
            onError("Failed to start server: ${e.message}")
            return
        }

        delay(200)

        val mediaId = source.toMediaId()
        context.startForegroundService(
            CastingService.startIntent(context, mediaId, source.subtitleFile?.absolutePath)
        )

        val ip = getLocalIp(context) ?: run { onError("Local IP address not found"); return }
        val success = sendDlnaPlay(
            location = device.location,
            videoUrl = "http://$ip:$PORT/video",
            subtitleUrl = source.toSubtitleUrl(ip, PORT),
            title = source.toTitle(),
            mimeType = source.toMimeType(),
        )

        if (success) {
            currentDevice = device
            currentCastingPath = mediaId
            startPolling(device, mediaId)
            onSuccess()
        } else {
            onError("Failed to cast to device")
        }
    }

    suspend fun updateCastingSource(
        context: Context,
        source: CastMediaSource,
        device: DlnaDevice,
    ) = withContext(Dispatchers.IO) {
        val mediaId = source.toMediaId()

        ensureCastingLocks(context)

        _playbackState.update {
            it.copy(
                positionMs = 0L,
                durationMs = 0L,
                transportState = DlnaTransportState.TRANSITIONING,
                mediaId = mediaId,
            )
        }

        currentCastingPath = mediaId
        server?.updateSource(source)

        val ip = getLocalIp(context) ?: return@withContext
        sendDlnaPlay(
            location = device.location,
            videoUrl = "http://$ip:$PORT/video",
            subtitleUrl = source.toSubtitleUrl(ip, PORT),
            title = source.toTitle(),
            mimeType = source.toMimeType(),
        )
    }

    suspend fun stopCasting(context: Context) = withContext(Dispatchers.IO) {
        currentDevice?.let { device ->
            try {
                val avTransportUrl = getAvTransportUrl(device.location)
                val stopBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID>
    </u:Stop>
  </s:Body>
</s:Envelope>"""
                avTransportUrl?.let { soapPost(it, "urn:schemas-upnp-org:service:AVTransport:1#Stop", stopBody) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send Stop command: $e")
            }
        }
        stopCastingInternal()
    }

    private suspend fun getAvTransportUrl(locationUrl: String): String? = withContext(Dispatchers.IO) {
        if (locationUrl == cachedLocation && cachedAvTransportUrl != null)
            return@withContext cachedAvTransportUrl

        try {
            val xml = java.net.URL(locationUrl).readText()
            val serviceBlocks = xml.split("<service>")
            for (block in serviceBlocks) {
                if (block.contains("AVTransport", ignoreCase = true)) {
                    val controlUrl = Regex("<controlURL>([^<]+)</controlURL>")
                        .find(block)?.groupValues?.get(1)?.trim() ?: continue

                    val uri = java.net.URI(locationUrl)
                    val port = if (uri.port == -1) "" else ":${uri.port}"
                    val baseUrl = "${uri.scheme}://${uri.host}$port"
                    val fullUrl = if (controlUrl.startsWith("/")) "$baseUrl$controlUrl"
                    else "$baseUrl/$controlUrl"

                    cachedAvTransportUrl = fullUrl
                    cachedLocation = locationUrl
                    return@withContext fullUrl
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AVTransport URL: $e")
            null
        }
    }

    private suspend fun sendDlnaPlay(
        location: String,
        videoUrl: String,
        subtitleUrl: String?,
        title: String,
        mimeType: String = "video/mp4"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val avTransportUrl = getAvTransportUrl(location) ?: location

            // Stop
            val stopBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body><u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><InstanceID>0</InstanceID></u:Stop></s:Body>
</s:Envelope>"""
            soapPost(avTransportUrl, "urn:schemas-upnp-org:service:AVTransport:1#Stop", stopBody)

            val protocolInfo = "http-get:*:$mimeType:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

            val subtitleRes = if (subtitleUrl != null) buildString {
                append("""<res protocolInfo="http-get:*:text/srt:*">$subtitleUrl</res>""")
                append("""<res protocolInfo="http-get:*:text/x-srt:*">$subtitleUrl</res>""")
                append("""<res protocolInfo="http-get:*:text/vtt:*">$subtitleUrl</res>""")
                append("""<sec:CaptionInfoEx sec:type="srt">$subtitleUrl</sec:CaptionInfoEx>""")
                append("""<sec:CaptionInfo sec:type="srt">$subtitleUrl</sec:CaptionInfo>""")
            } else ""

            val didlRaw = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
            xmlns:dc="http://purl.org/dc/elements/1.1/"
            xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
            xmlns:sec="http://www.sec.co.kr/dlna">
          <item id="0" parentID="-1" restricted="1">
            <dc:title>$title</dc:title>
            <upnp:class>object.item.videoItem.movie</upnp:class>
            <res protocolInfo="$protocolInfo">$videoUrl</res>
            $subtitleRes
          </item>
        </DIDL-Lite>"""

            val escapedDidl = didlRaw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")

            val setUriBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID>
      <CurrentURI>$videoUrl</CurrentURI>
      <CurrentURIMetaData>$escapedDidl</CurrentURIMetaData>
    </u:SetAVTransportURI>
  </s:Body>
</s:Envelope>"""

            val setResult = soapPost(avTransportUrl, "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI", setUriBody)
            if (setResult >= 400) return@withContext false

            val playBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID><Speed>1</Speed>
    </u:Play>
  </s:Body>
</s:Envelope>"""

            val playResult = soapPost(avTransportUrl, "urn:schemas-upnp-org:service:AVTransport:1#Play", playBody)
            playResult < 400

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SOAP request: $e")
            false
        }
    }

    private fun soapPost(url: String, soapAction: String, body: String): Int {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("SOAPAction", "\"$soapAction\"")
            doOutput = true
            connectTimeout = 5000
            readTimeout = 5000
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        if (code >= 400) {
            val error = try { connection.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
            Log.e(TAG, "SOAP error response body: $error")
        }
        connection.disconnect()
        return code
    }

    fun release() {
        stopServer()
    }

    private fun stopServer() {
        server?.stop()
        server = null
    }

    private fun getLocalIp(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork

        if (activeNetwork != null) {
            val caps = cm.getNetworkCapabilities(activeNetwork)
            if (caps != null &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            ) {
                val ip = cm.getLinkProperties(activeNetwork)
                    ?.linkAddresses
                    ?.map { it.address }
                    ?.filterIsInstance<Inet4Address>()
                    ?.firstOrNull { isPrivateIp(it) }
                    ?.hostAddress
                if (ip != null) return ip
            }
        }

        val excludedInterfaces = listOf("tun", "ppp", "rmnet", "lo", "dummy")
        return NetworkInterface.getNetworkInterfaces()
            ?.toList()
            ?.filter { iface ->
                iface.isUp && !iface.isLoopback && !iface.isVirtual &&
                        excludedInterfaces.none { iface.name.startsWith(it) }
            }
            ?.flatMap { it.inetAddresses.toList() }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { isPrivateIp(it) }
            ?.hostAddress
    }

    private fun isPrivateIp(addr: Inet4Address): Boolean {
        if (addr.isLoopbackAddress) return false

        val bytes = addr.address
        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF

        return when (first) {
            10 -> true
            172 -> second in 16..31
            192 -> second == 168
            else -> false
        }
    }

    private fun soapPostWithResponse(url: String, soapAction: String, body: String): String {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("SOAPAction", "\"$soapAction\"")
            doOutput = true
            connectTimeout = 5000
            readTimeout = 5000
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        if (code >= 400) throw IOException("SOAP error: $code")
        return connection.inputStream.bufferedReader().readText()
    }

    suspend fun pollPlaybackState(device: DlnaDevice): String? = withContext(Dispatchers.IO) {
        try {
            val avTransportUrl = getAvTransportUrl(device.location) ?: return@withContext null
            val body = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:GetTransportInfo xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID>
    </u:GetTransportInfo>
  </s:Body>
</s:Envelope>"""
            val response = soapPostWithResponse(avTransportUrl,
                "urn:schemas-upnp-org:service:AVTransport:1#GetTransportInfo", body)

            Regex("<CurrentTransportState>([^<]+)</CurrentTransportState>")
                .find(response)?.groupValues?.get(1)
        } catch (e: Exception) { null }
    }

    suspend fun pollPositionInfo(device: DlnaDevice): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        try {
            val avTransportUrl = getAvTransportUrl(device.location) ?: return@withContext null
            val body = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:GetPositionInfo xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID>
    </u:GetPositionInfo>
  </s:Body>
</s:Envelope>"""
            val response = soapPostWithResponse(avTransportUrl,
                "urn:schemas-upnp-org:service:AVTransport:1#GetPositionInfo", body)

            fun parseTime(s: String): Long {
                val parts = s.trim().split(":")
                if (parts.isEmpty()) return 0L

                val secondsStr = parts.last()
                val secondsMillis = (secondsStr.toDoubleOrNull() ?: 0.0) * 1000

                return when (parts.size) {
                    3 -> {
                        val h = parts[0].toLongOrNull() ?: 0L
                        val m = parts[1].toLongOrNull() ?: 0L
                        (h * 3600000L) + (m * 60000L) + secondsMillis.toLong()
                    }
                    2 -> {
                        val m = parts[0].toLongOrNull() ?: 0L
                        (m * 60000L) + secondsMillis.toLong()
                    }
                    1 -> {
                        secondsMillis.toLong()
                    }
                    else -> 0L
                }
            }

            val duration = Regex("<TrackDuration>([^<]+)</TrackDuration>").find(response)
                ?.groupValues?.get(1)?.let { parseTime(it) } ?: 0L
            val position = Regex("<RelTime>([^<]+)</RelTime>").find(response)
                ?.groupValues?.get(1)?.let { parseTime(it) } ?: 0L

            Pair(position, duration)
        } catch (e: Exception) { null }
    }

    suspend fun seekTo(device: DlnaDevice, positionMs: Long) = withContext(Dispatchers.IO) {
        try {
            val avTransportUrl = getAvTransportUrl(device.location) ?: return@withContext
            val h = positionMs / 3600000
            val m = (positionMs % 3600000) / 60000
            val s = (positionMs % 60000) / 1000
            val target = "%02d:%02d:%02d".format(h, m, s)
            val body = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:Seek xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID>
      <Unit>REL_TIME</Unit>
      <Target>$target</Target>
    </u:Seek>
  </s:Body>
</s:Envelope>"""
            soapPost(avTransportUrl, "urn:schemas-upnp-org:service:AVTransport:1#Seek", body)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek: ${e.message}")
        }
    }

    suspend fun pause(device: DlnaDevice) = withContext(Dispatchers.IO) {
        try {
            val url = getAvTransportUrl(device.location) ?: return@withContext
            val body = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body><u:Pause xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><InstanceID>0</InstanceID></u:Pause></s:Body>
</s:Envelope>"""
            soapPost(url, "urn:schemas-upnp-org:service:AVTransport:1#Pause", body)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause: ${e.message}")
        }
    }

    suspend fun play(device: DlnaDevice) = withContext(Dispatchers.IO) {
        try {
            val url = getAvTransportUrl(device.location) ?: return@withContext
            val body = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body><u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><InstanceID>0</InstanceID><Speed>1</Speed></u:Play></s:Body>
</s:Envelope>"""
            soapPost(url, "urn:schemas-upnp-org:service:AVTransport:1#Play", body)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause: ${e.message}")
        }
    }
}
