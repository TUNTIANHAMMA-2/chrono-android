package com.personal.cardvault.vault

import android.content.res.AssetManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

/** 离线 BIN 解析结果（支持前 8 位优先、前 6 位兜底）。 */
data class BinInfo(
    val bin: String,
    val brand: String,
    val cardType: String,
    val category: String,
    val issuerRaw: String,
    val issuerDisplay: String,
    val countryCode: String
)

@Serializable
private data class BinMeta(
    val brands: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val cats: List<String> = emptyList(),
    val issuers: List<String> = emptyList()
)

private data class RawBinEntry(
    val brandIdx: Int,
    val typeIdx: Int,
    val issuerIdx: Int,
    val catIdx: Int,
    val countryCode: String
)

/**
 * 100% 本地离线 BIN 码解析引擎（零网络请求、毫秒级按前 2 位分桶懒加载）。
 *
 * 数据源自 `assets/bins/meta.json.gz` 与 `assets/bins/<2-digit>.tsv.gz`（37.4 万条全球 BIN 记录，含 2.1 万条大中华区 BIN）。
 */
class BinLookupEngine(
    private val assetOpener: (String) -> InputStream?
) {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile
    private var cachedMeta: BinMeta? = null
    private val bucketCache = ConcurrentHashMap<String, Map<String, RawBinEntry>>()

    /**
     * 解析卡号前 8 位或前 6 位 BIN 码。
     * 优先精确匹配 8 位 BIN，未命中时自动回退 6 位 BIN。
     */
    fun lookup(cardNumberOrPrefix: String): BinInfo? {
        val digits = cardNumberOrPrefix.filter { it.isDigit() }
        if (digits.length < 6) return null

        val meta = loadMeta() ?: return null
        val bucketKey = digits.substring(0, 2)
        val bucket = loadBucket(bucketKey)

        val matchedKey = when {
            digits.length >= 8 && bucket.containsKey(digits.substring(0, 8)) -> digits.substring(0, 8)
            bucket.containsKey(digits.substring(0, 6)) -> digits.substring(0, 6)
            else -> return null
        }
        val entry = bucket[matchedKey] ?: return null

        val rawBrand = meta.brands.getOrElse(entry.brandIdx) { "" }
        val rawType = meta.types.getOrElse(entry.typeIdx) { "" }
        val rawCat = meta.cats.getOrElse(entry.catIdx) { "" }
        val rawIssuer = meta.issuers.getOrElse(entry.issuerIdx) { "" }

        return BinInfo(
            bin = matchedKey,
            brand = normalizeBrand(rawBrand, matchedKey),
            cardType = normalizeCardType(rawType),
            category = rawCat,
            issuerRaw = rawIssuer,
            issuerDisplay = normalizeIssuerDisplay(rawIssuer),
            countryCode = entry.countryCode
        )
    }

    private fun loadMeta(): BinMeta? {
        cachedMeta?.let { return it }
        synchronized(this) {
            cachedMeta?.let { return it }
            // AAPT2 打包 APK 时会自动解压 .gz 并去掉后缀，因此优先尝试 .gz，不存在时回退读取未压缩版本
            val gzStream = assetOpener("bins/meta.json.gz")
            if (gzStream != null) {
                return runCatching {
                    GZIPInputStream(gzStream).bufferedReader(Charsets.UTF_8).use { reader ->
                        json.decodeFromString<BinMeta>(reader.readText())
                    }
                }.getOrNull()?.also { cachedMeta = it }
            }
            val plainStream = assetOpener("bins/meta.json") ?: return null
            return runCatching {
                plainStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    json.decodeFromString<BinMeta>(reader.readText())
                }
            }.getOrNull()?.also { cachedMeta = it }
        }
    }

    private fun loadBucket(twoDigits: String): Map<String, RawBinEntry> {
        bucketCache[twoDigits]?.let { return it }
        val gzStream = assetOpener("bins/$twoDigits.tsv.gz")
        val parsed = if (gzStream != null) {
            runCatching {
                GZIPInputStream(gzStream).use { gz -> parseBucketStream(gz) }
            }.getOrElse { emptyMap() }
        } else {
            val plainStream = assetOpener("bins/$twoDigits.tsv") ?: return emptyMap()
            runCatching {
                plainStream.use { parseBucketStream(it) }
            }.getOrElse { emptyMap() }
        }
        bucketCache[twoDigits] = parsed
        return parsed
    }

    private fun parseBucketStream(stream: InputStream): Map<String, RawBinEntry> {
        val map = HashMap<String, RawBinEntry>(4096)
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
            for (line in lines) {
                val parts = line.split('\t')
                if (parts.size < 6) continue
                val b = parts[0]
                val brandIdx = parts[1].toIntOrNull() ?: continue
                val typeIdx = parts[2].toIntOrNull() ?: continue
                val issuerIdx = parts[3].toIntOrNull() ?: continue
                val catIdx = parts[4].toIntOrNull() ?: continue
                val iso = parts[5]
                map[b] = RawBinEntry(brandIdx, typeIdx, issuerIdx, catIdx, iso)
            }
        }
        return map
    }

    companion object {
        @Volatile
        private var sharedInstance: BinLookupEngine? = null

        fun fromAssets(assets: AssetManager): BinLookupEngine {
            sharedInstance?.let { return it }
            return synchronized(this) {
                sharedInstance ?: BinLookupEngine { path ->
                    runCatching { assets.open(path) }.getOrNull()
                }.also { sharedInstance = it }
            }
        }

        internal fun normalizeBrand(rawBrand: String, bin: String): String {
            val upper = rawBrand.uppercase()
            return when {
                upper.contains("UNIONPAY") || upper.contains("CHINA UNION") || bin.startsWith("62") -> "UNIONPAY"
                upper.contains("MASTERCARD") || upper.contains("MASTER") -> "MASTERCARD"
                upper.contains("VISA") -> "VISA"
                upper.contains("AMERICAN EXPRESS") || upper.contains("AMEX") -> "AMEX"
                upper.contains("JCB") -> "JCB"
                upper.contains("DISCOVER") -> "DISCOVER"
                else -> upper
            }
        }

        internal fun normalizeCardType(rawType: String): String {
            val upper = rawType.uppercase()
            return when {
                upper.contains("DEBIT") -> "DEBIT"
                upper.contains("CREDIT") -> "CREDIT"
                upper.contains("PREPAID") -> "PREPAID"
                else -> upper
            }
        }

        /** 将国际 BIN 库里的英文发卡行名称规范为国内常用中文银行名，同时保留境外发卡行原名。 */
        internal fun normalizeIssuerDisplay(rawIssuer: String): String {
            val upper = rawIssuer.uppercase()
            return when {
                upper.contains("INDUSTRIAL AND COMMERCIAL BANK OF CHINA") || upper.contains("ICBC") -> "中国工商银行"
                upper.contains("CHINA MERCHANTS BANK") || upper.contains("CMB") -> "招商银行"
                upper.contains("CHINA CONSTRUCTION BANK") || upper.contains("CCB") -> "中国建设银行"
                upper.contains("AGRICULTURAL BANK OF CHINA") -> "中国农业银行"
                upper.contains("BANK OF COMMUNICATIONS") || upper.contains("BOCOM") -> "交通银行"
                upper.contains("POSTAL SAVINGS BANK OF CHINA") || upper.contains("PSBC") -> "中国邮政储蓄银行"
                upper.contains("CHINA CITIC BANK") || upper.contains("CITIC BANK") -> "中信银行"
                upper.contains("SHANGHAI PUDONG DEVELOPMENT") || upper.contains("SPDB") -> "浦发银行"
                upper.contains("CHINA EVERBRIGHT BANK") -> "光大银行"
                upper.contains("CHINA MINSHENG") -> "民生银行"
                upper.contains("PING AN BANK") || upper.contains("PINGAN") -> "平安银行"
                upper.contains("CHINA GUANGFA") || upper.contains("GUANGDONG DEVELOPMENT") -> "广发银行"
                upper.contains("INDUSTRIAL BANK CO") -> "兴业银行"
                upper.contains("BANK OF CHINA") && !upper.contains("AGRICULTURAL") &&
                    !upper.contains("CONSTRUCTION") && !upper.contains("COMMERCIAL") &&
                    !upper.contains("POSTAL") -> "中国银行"
                upper.contains("HSBC") -> "汇丰银行"
                upper.contains("BANK OF SHANGHAI") -> "上海银行"
                upper.contains("BANK OF BEIJING") -> "北京银行"
                upper.contains("HUA XIA BANK") || upper.contains("HUAXIA BANK") -> "华夏银行"
                upper.contains("CHINA ZHESHANG BANK") -> "浙商银行"
                else -> rawIssuer.trim()
            }
        }
    }
}
