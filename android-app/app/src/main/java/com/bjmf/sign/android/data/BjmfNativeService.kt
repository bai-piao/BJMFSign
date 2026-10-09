package com.bjmf.sign.android.data

import android.util.Base64
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup

class BjmfNativeService {
    data class QrSession(
        val id: String,
        val qr: LoginQr,
        val client: OkHttpClient,
    )

    suspend fun createQrSession(): QrSession = withContext(Dispatchers.IO) {
        val cookieJar = SimpleCookieJar()
        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val html = getString(client, QR_LOGIN_URL, mobileHeaders())
        val qrUrl = extractQrUrl(html)
        val bytes = getBytes(client, qrUrl, mobileHeaders())
        val imageDataUrl = "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        QrSession(
            id = UUID.randomUUID().toString(),
            qr = LoginQr(imageDataUrl = imageDataUrl),
            client = client,
        )
    }

    suspend fun pollQrLogin(session: QrSession): LoginAccount? = withContext(Dispatchers.IO) {
        val json = getString(session.client, "$QR_LOGIN_URL?op=checklogin", mobileHeaders())
        val data = JSONObject(json)
        if (!data.optBoolean("status")) return@withContext null
        val redirectUrl = data.optString("url")
        if (redirectUrl.isBlank()) return@withContext null
        val cookie = getCookieFromRedirect(redirectUrl)
        val info = getUserAndClassInfo(cookie)
        LoginAccount(cookie = cookie, userInfo = info)
    }

    suspend fun getUserAndClassInfo(cookie: String): UserInfo = withContext(Dispatchers.IO) {
        val cookieHeader = extractRememberCookie(cookie)
        val headers = browserHeaders(cookieHeader)
        val myHtml = getString(sharedClient, "$BJMF_BASE/student/my", headers)
        val classHtml = getString(sharedClient, "$BJMF_BASE/student", headers)

        val userName = Regex("uname:'([^']+)'")
            .find(myHtml)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .ifBlank { "未找到" }

        val classInfo = parseClassInfo(classHtml)
        UserInfo(
            name = userName,
            classId = classInfo.classId,
            className = classInfo.className,
            classCode = classInfo.classCode,
        )
    }

    suspend fun runSign(task: BjmfTask): SignResult = withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        fun log(text: String) {
            lines.add(text)
        }

        try {
            val userInfo = getUserAndClassInfo(task.cookie)
            val userName = userInfo.name.takeIf { it != "未找到" } ?: task.name
            val classId = userInfo.classId.takeIf { it != "未找到" && it.isNotBlank() } ?: task.classId
            if (userInfo.name == "未找到" || classId.isBlank()) {
                logHeader(::log, task, userName, classId)
                log("无法获取用户或班级信息，可能是 Cookie 过期")
                return@withContext SignResult(task.id, task.name, "skip", lines.joinToString("\n"))
            }

            logHeader(::log, task, userName, classId)

            val session = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            runCatching {
                getString(session, "$BJMF_BASE/student/my", browserHeaders(task.cookie))
            }.onFailure {
                log("会话预热失败: ${it.message}")
            }

            val punchTypes = linkedMapOf<String, String>()
            var lastHtml = ""
            var lastCode = 0

            listOf("punchs", "daka").forEach { module ->
                val listUrl = "$BJMF_BASE/student/course/$classId/$module"
                val response = execute(
                    client = session,
                    request = Request.Builder()
                        .url(listUrl)
                        .headers(browserHeaders(task.cookie).newBuilder().set("Referer", "$BJMF_BASE/student/course/$classId").build())
                        .get()
                        .build(),
                )
                lastHtml = response.body
                lastCode = response.code

                Regex("punchcard_(\\d+)").findAll(response.body).forEach { match ->
                    punchTypes.putIfAbsent(match.groupValues[1], module)
                }

                Regex("/student/(punch\\w+|daka)/course/\\d+/(\\d+)").findAll(response.body).forEach { match ->
                    punchTypes.putIfAbsent(match.groupValues[2], match.groupValues[1])
                }

                Regex("/student/(punch\\w+|daka)/course/\\d+/(\\d+)").find(response.finalUrl)?.let { match ->
                    punchTypes.putIfAbsent(match.groupValues[2], match.groupValues[1])
                }
            }

            if (punchTypes.isEmpty()) {
                val soup = Jsoup.parse(lastHtml)
                val successInfo = soup.selectFirst(".punch-success-info")?.text().orEmpty()
                val statusInfo = soup.selectFirst(".punch-status")?.text().orEmpty()
                val status = if ("已签到" in successInfo || "已签到" in statusInfo) {
                    "already_signed"
                } else {
                    "no_sign_in"
                }
                log(if (status == "already_signed") "检测到已完成签到" else "未找到在进行的签到/不在签到时间内")
                log("Debug: Status Code: $lastCode")
                notify(task, status)
                return@withContext SignResult(task.id, task.name, status, lines.joinToString("\n"))
            }

            for ((punchId, punchType) in punchTypes) {
                log("签到项: $punchId (模块: $punchType)")
                val postUrl = "$BJMF_BASE/student/$punchType/course/$classId/$punchId"
                val form = FormBody.Builder()
                    .add("id", punchId)
                    .add("lat", task.lat)
                    .add("lng", task.lng)
                    .add("acc", task.acc)
                    .add("res", "")
                    .add("gps_addr", "")
                    .build()
                val response = execute(
                    client = session,
                    request = Request.Builder()
                        .url(postUrl)
                        .headers(browserHeaders(task.cookie))
                        .post(form)
                        .build(),
                )
                if (response.code != 200) {
                    log("请求失败，状态码: ${response.code}")
                    notify(task, "error")
                    return@withContext SignResult(task.id, task.name, "error", lines.joinToString("\n"))
                }

                val title = Jsoup.parse(response.body).selectFirst("#title")?.text().orEmpty()
                val status = when {
                    "已签到" in title -> "already_signed"
                    "未开始" in title -> "not_started"
                    else -> "success"
                }
                log(
                    when (status) {
                        "already_signed" -> "已签到！无需再次签到"
                        "not_started" -> "未开始签到，请稍后"
                        else -> "本次签到成功"
                    },
                )
                notify(task, status)
                return@withContext SignResult(task.id, task.name, status, lines.joinToString("\n"))
            }

            notify(task, "no_sign_in")
            SignResult(task.id, task.name, "no_sign_in", lines.joinToString("\n"))
        } catch (e: Exception) {
            log("发生错误: ${e.message}")
            notify(task, "error")
            SignResult(task.id, task.name, "error", lines.joinToString("\n"))
        }
    }

    suspend fun sendSummary(wxKey: String, results: List<SignResult>) = withContext(Dispatchers.IO) {
        if (wxKey.isBlank() || results.isEmpty()) return@withContext
        val failed = results.filterNot { it.status in setOf("success", "already_signed") }
        val title = if (failed.isEmpty()) "全部签到成功" else "部分失败"
        val detail = results.joinToString("\n") { result ->
            "${result.taskName}${if (result.status in setOf("success", "already_signed")) "成功" else "失败"}"
        }
        postForm(
            url = "https://sctapi.ftqq.com/$wxKey.send",
            fields = mapOf("text" to title, "desp" to detail),
        )
    }

    private suspend fun notify(task: BjmfTask, status: String) {
        val success = status in setOf("success", "already_signed")
        val desc = statusDescription(status)
        val message = if (success) "签到成功！" else "签到失败，原因：$desc"
        val now = currentTime()
        if (task.wxKey.isNotBlank()) {
            runCatching {
                postForm(
                    url = "https://sctapi.ftqq.com/${task.wxKey}.send",
                    fields = mapOf("text" to "$now  $message", "desp" to desc),
                )
            }
        }
        if (task.qqKey.isNotBlank()) {
            runCatching {
                postForm(
                    url = "https://qmsg.zendee.cn/send/${task.qqKey}",
                    fields = mapOf("msg" to "$now  $message"),
                )
            }
        }
    }

    private fun logHeader(log: (String) -> Unit, task: BjmfTask, userName: String, classId: String) {
        log("==================${currentTime()}===================")
        log("=========== 用户和班级信息 ===============")
        log("任务 ID: ${task.id}")
        log("用户姓名: $userName")
        log("班级标识: $classId")
        log("=========== 位置信息 ===============")
        log("纬度(lat): ${task.lat}")
        log("经度(lng): ${task.lng}")
        log("精度(acc): ${task.acc}")
        log("=========== 签到结果 ===============")
    }

    private fun parseClassInfo(html: String): ClassInfo {
        val gconfig = Regex("var gconfig=\\{([^}]+)\\}").find(html)?.groupValues?.getOrNull(1).orEmpty()
        val cname = Regex("cname:'([^']+)'").find(gconfig)?.groupValues?.getOrNull(1)
        val classIdFromConfig = Regex("id:'([^']+)'").find(gconfig)?.groupValues?.getOrNull(1)
        val soup = Jsoup.parse(html, BJMF_BASE)

        var classId = classIdFromConfig.orEmpty()
        var className = cname.orEmpty()
        soup.select("a[href]").forEach { link ->
            val match = Regex("/student/course/(\\d+)").find(link.attr("href"))
            if (match != null && classId.isBlank()) {
                classId = match.groupValues[1]
                className = link.text().trim()
            }
        }

        var classCode = "未找到"
        soup.select("div.card").firstOrNull { "班级码" in it.text() }?.let { card ->
            Regex("班级码\\s*([A-Z0-9]{4,10})").find(card.text())?.groupValues?.getOrNull(1)?.let {
                classCode = it
            }
        }
        if (classCode == "未找到") {
            val candidates = Regex("\\b[A-Z0-9]{4,8}\\b")
                .findAll(html)
                .map { it.value }
                .filterNot { code -> code.all(Char::isDigit) && (code.length > 6 || code.toIntOrNull()?.let { it > 2030 } == true) }
                .toList()
            classCode = candidates.firstOrNull { it.length in 5..6 && !it.all(Char::isDigit) }
                ?: candidates.firstOrNull()
                ?: "未找到"
        }

        return ClassInfo(
            classId = classId.ifBlank { "未找到" },
            className = className.ifBlank { "未找到" },
            classCode = classCode,
        )
    }

    private fun extractQrUrl(html: String): String {
        val soup = Jsoup.parse(html, QR_LOGIN_URL)
        val src = soup.selectFirst("div#qrcode img[src]")?.attr("abs:src")
            ?: soup.select("img[src]").firstOrNull { "ticket=" in it.attr("src") }?.attr("abs:src")
        return src?.takeIf { it.isNotBlank() } ?: error("未找到二维码图片")
    }

    private fun getCookieFromRedirect(redirectUrl: String): String {
        val cookieJar = SimpleCookieJar()
        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val query = redirectUrl.substringAfter("?", missingDelimiterValue = "")
        if (query.isBlank()) error("登录跳转地址无效")
        val request = Request.Builder()
            .url("$BJ_LOGIN/student/uidlogin?$query")
            .headers(uidLoginHeaders())
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code !in 300..399) {
                error("获取 Cookie 失败：HTTP ${response.code}")
            }
        }
        val cookie = cookieJar.all()
            .filterNot { it.name == "s" }
            .firstOrNull()
            ?: error("未获取到有效 Cookie")
        return "${cookie.name}=${cookie.value}"
    }

    private fun extractRememberCookie(cookie: String): String {
        return Regex("remember_student_[^=;]+=[^;]+").find(cookie)?.value ?: cookie
    }

    private fun mobileHeaders(): Headers = Headers.Builder()
        .set("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G981B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.162 Mobile Safari/537.36 MicroMessenger/7.0.10.1580(0x27000A50) Process/tools NetType/WIFI Language/zh_CN ABI/arm64")
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
        .set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .set("Referer", "https://wx.qq.com/")
        .set("X-Requested-With", "XMLHttpRequest")
        .build()

    private fun browserHeaders(cookie: String): Headers = Headers.Builder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; X64; Linux; Android 9;) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Firefox/92.0 WeChat/x86_64 Weixin NetType/4G Language/zh_CN ABI/x86_64")
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/wxpic,image/tpg,image/webp,image/apng,*/*;q=0.8")
        .set("Cookie", cookie)
        .build()

    private fun uidLoginHeaders(): Headers = Headers.Builder()
        .set("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36 Edg/142.0.0.0")
        .set("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
        .set("referer", "https://login.b8n.cn/")
        .set("accept-language", "zh-CN,zh;q=0.9,en;q=0.8")
        .build()

    private fun getString(client: OkHttpClient, url: String, headers: Headers): String {
        val response = execute(client, Request.Builder().url(url).headers(headers).get().build())
        if (response.code !in 200..299) error("请求失败：HTTP ${response.code}")
        return response.body
    }

    private fun getBytes(client: OkHttpClient, url: String, headers: Headers): ByteArray {
        val request = Request.Builder().url(url).headers(headers).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("请求失败：HTTP ${response.code}")
            return response.body?.bytes() ?: error("响应为空")
        }
    }

    private fun execute(client: OkHttpClient, request: Request): HttpTextResponse {
        client.newCall(request).execute().use { response ->
            return HttpTextResponse(
                code = response.code,
                body = response.body?.string().orEmpty(),
                finalUrl = response.request.url.toString(),
            )
        }
    }

    private fun postForm(url: String, fields: Map<String, String>) {
        val body = FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder().url(url).post(body).build()
        sharedClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("通知发送失败：HTTP ${response.code}")
        }
    }

    private fun statusDescription(status: String): String = when (status) {
        "success" -> "签到成功"
        "already_signed" -> "已签到，无需重复"
        "not_started" -> "未开始签到，请稍后"
        "no_sign_in" -> "当前无可用签到"
        "skip" -> "跳过（信息不完整或 Cookie 失效）"
        "error" -> "执行出错"
        else -> "未知状态: $status"
    }

    private fun currentTime(): String = timeFormatter.format(Date())

    private data class ClassInfo(
        val classId: String,
        val className: String,
        val classCode: String,
    )

    private data class HttpTextResponse(
        val code: Int,
        val body: String,
        val finalUrl: String,
    )

    companion object {
        private const val QR_LOGIN_URL = "https://bjmf.k8n.cn/weixin/qrlogin/student"
        private const val BJMF_BASE = "https://bjmf.k8n.cn"
        private const val BJ_LOGIN = "https://bj.k8n.cn"
        private val sharedClient = OkHttpClient()
        private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
    }
}
