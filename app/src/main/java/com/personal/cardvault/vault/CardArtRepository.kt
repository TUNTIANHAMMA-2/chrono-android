package com.personal.cardvault.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** 远端卡面候选条目（来自自建 CardFace 服务聚合的 cardfacedb.com / aircards.org / 审核共享库）。 */
@Serializable
data class RemoteCardSkin(
    val id: String,
    val title: String,
    val issuer: String = "",
    val network: String = "",
    val cardType: String = "",
    val source: String = "",
    val previewUrl: String,
    val fullUrl: String
)

@Serializable
private data class RemoteSkinSearchResponse(
    val items: List<RemoteCardSkin> = emptyList()
)

@Serializable
private data class CommunitySkinSubmitRequest(
    val title: String,
    val issuer: String = "",
    val network: String = "",
    val cardType: String = "",
    val imageBase64: String
)

/**
 * 卡面图片本地存储与按需拉取仓库。
 *
 * 隐私边界：
 * - 仅在用户主动触发“匹配卡面”时向自建卡面服务发送发卡行/卡组织/卡种标签；
 * - 绝不上传完整卡号、尾号、有效期、CVV 或备注；
 * - 选定卡面后立即落盘到 App 私有目录 `filesDir/card_art/`，日常浏览保险箱 100% 离线读取。
 */
class CardArtRepository(
    private val context: Context,
    private val baseUrlProvider: () -> String = { DEFAULT_CARDFACE_BASE_URL }
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val artDir: File
        get() = File(context.filesDir, "card_art").apply { mkdirs() }

    /** 读取已落盘的本地卡面图片。 */
    suspend fun loadLocalArt(artFileName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (artFileName.isBlank()) return@withContext null
        val file = File(artDir, File(artFileName).name)
        if (!file.exists()) return@withContext null
        decodeSampledBitmap(file.readBytes())?.asImageBitmap()
    }

    /** 从系统相册 / SAF URI 导入用户本地或 AI 生成的卡面图片，保存为本地 WebP 并返回文件名。 */
    suspend fun importFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            saveBitmapBytes(bytes)
        }.getOrNull()
    }

    /**
     * 按发卡行 / 卡组织 / 卡种 / 可选搜索关键词向自建卡面聚合后端查询候选卡面。
     * [binPrefix] 仅在用户明确点击授权弹窗确认后才传入前 6 位公开 BIN 号段，默认为空。
     */
    suspend fun searchRemoteSkins(
        issuer: String = "",
        network: String = "",
        cardType: String = "",
        query: String = "",
        binPrefix: String = ""
    ): List<RemoteCardSkin> = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider().trimEnd('/')
        if (baseUrl.isBlank()) return@withContext emptyList()
        runCatching {
            val safeBin = binPrefix.filter { it.isDigit() }.take(6)
            val params = buildList {
                if (issuer.isNotBlank()) add("issuer=${enc(issuer)}")
                if (network.isNotBlank()) add("network=${enc(network)}")
                if (cardType.isNotBlank()) add("card_type=${enc(cardType)}")
                if (query.isNotBlank()) add("q=${enc(query)}")
                if (safeBin.length == 6) add("bin=${enc(safeBin)}")
                add("limit=20")
            }.joinToString("&")
            val url = URL("$baseUrl/v1/skins/search?$params")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode !in 200..299) return@withContext emptyList()
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                json.decodeFromString<RemoteSkinSearchResponse>(body).items
            } finally {
                conn.disconnect()
            }
        }.getOrElse { emptyList() }
    }

    /** 拉取候选卡面缩略图用于选择器预览（不落盘）。 */
    suspend fun fetchPreviewBitmap(urlString: String): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val resolved = resolveUrl(urlString)
            val bytes = downloadBytes(resolved) ?: return@withContext null
            decodeSampledBitmap(bytes, maxWidth = 640)?.asImageBitmap()
        }.getOrNull()
    }

    /** 用户选中远端卡面后，下载高清图并落盘到私有目录，返回持久化文件名。 */
    suspend fun downloadAndSaveSkin(skin: RemoteCardSkin): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolved = resolveUrl(skin.fullUrl.ifBlank { skin.previewUrl })
            val bytes = downloadBytes(resolved) ?: return@withContext null
            saveBitmapBytes(bytes)
        }.getOrNull()
    }

    fun deleteLocalArt(artFileName: String) {
        if (artFileName.isBlank()) return
        runCatching { File(artDir, File(artFileName).name).delete() }
    }

    /**
     * 将用户本地或 AI 生成的非敏感卡面图投稿至自建服务待审核库（`pending`）。
     * 绝不上传卡号、尾号、有效期、CVV 或备注。
     */
    suspend fun submitCommunitySkin(
        artFileName: String,
        title: String,
        issuer: String = "",
        network: String = "",
        cardType: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider().trimEnd('/')
        if (baseUrl.isBlank() || artFileName.isBlank()) return@withContext false
        val file = File(artDir, File(artFileName).name)
        if (!file.exists()) return@withContext false

        runCatching {
            val raw = file.readBytes()
            val b64 = Base64.encodeToString(raw, Base64.NO_WRAP)
            val payloadJson = json.encodeToString(
                CommunitySkinSubmitRequest.serializer(),
                CommunitySkinSubmitRequest(
                    title = title.ifBlank { "自定义卡面" },
                    issuer = issuer,
                    network = network,
                    cardType = cardType,
                    imageBase64 = b64,
                ),
            )
            val url = URL("$baseUrl/v1/skins/submit")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            try {
                conn.outputStream.use { it.write(payloadJson.toByteArray(Charsets.UTF_8)) }
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }

    private fun resolveUrl(raw: String): String {
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val base = baseUrlProvider().trimEnd('/')
        return "$base/${raw.trimStart('/')}"
    }

    private fun downloadBytes(urlString: String): ByteArray? {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 10_000
        }
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun saveBitmapBytes(bytes: ByteArray): String? {
        val bitmap = decodeSampledBitmap(bytes, maxWidth = 1920) ?: return null
        val fileName = "skin_${UUID.randomUUID().toString().replace("-", "").take(16)}.webp"
        val outFile = File(artDir, fileName)
        FileOutputStream(outFile).use { fos ->
            @Suppress("DEPRECATION")
            bitmap.compress(Bitmap.CompressFormat.WEBP, 95, fos)
        }
        return fileName
    }

    private fun decodeSampledBitmap(bytes: ByteArray, maxWidth: Int = 1920): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxWidth) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        /** 默认自建卡面聚合服务地址（可在设置或构建配置中覆盖）。 */
        const val DEFAULT_CARDFACE_BASE_URL = ""
    }
}
