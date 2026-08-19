package com.autumn.s44tool

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ExactResult(
    val valid: Boolean = false,
    val target: Double = 0.0,
    val power: Double = 0.0,
    val needPerf: Double = 0.0,
    val capPower: Double = 0.0,
    val needCoeff: Double = 0.0,
    val coeff: Double = 0.0,
    val perf: Double = 0.0,
    val effectivePerf: Double = 0.0,
    val cap: Double = 0.0,
    val selfCheck: String = "待填写",
    val selfCheckSub: String = "填当前游戏战力即可校验",
    val decision: String = ""
)

data class PeakResult(
    val needCoeffPct: Double = 0.0,
    val needPeakScore: Int? = null,
    val gap: Int? = null,
    val source: String = "none",
    val precision: String = "等待映射表"
)

data class MatchResult(
    val values: List<Double> = emptyList(),
    val best: List<Double> = emptyList(),
    val raw: Double = 0.0,
    val performance: Double = 0.0,
    val recycleGames: Int = 0
)

data class CurvePoint(val score: Double, val coeffPct: Double)

object S44Math {
    val referenceCurve = listOf(
        CurvePoint(1200.0,100.0),CurvePoint(1300.0,104.1),CurvePoint(1400.0,110.0),
        CurvePoint(1500.0,117.9),CurvePoint(1600.0,127.6),CurvePoint(1650.0,133.1),
        CurvePoint(1700.0,139.1),CurvePoint(1750.0,145.6),CurvePoint(1800.0,152.6),
        CurvePoint(1850.0,160.0),CurvePoint(1900.0,167.9),CurvePoint(1950.0,176.3),
        CurvePoint(2000.0,185.1),CurvePoint(2050.0,194.4),CurvePoint(2100.0,204.2),
        CurvePoint(2150.0,214.5),CurvePoint(2200.0,225.2),CurvePoint(2250.0,236.4),
        CurvePoint(2300.0,248.0),CurvePoint(2350.0,260.1),CurvePoint(2400.0,265.0)
    )

    fun parseCoeff(value: String): Double? {
        val v = value.trim().toDoubleOrNull() ?: return null
        if (!v.isFinite() || v <= 0.0) return null
        return if (v <= 5.0) v else v / 100.0
    }

    fun exact(
        targetPower: String,
        gamePower: String,
        performance: String,
        cap: String,
        coeffInput: String,
        peakNow: String,
        peakUnlocked: String
    ): ExactResult {
        val target = max(1.0, targetPower.toDoubleOrNull() ?: 10000.0)
        val perf = max(0.0, performance.toDoubleOrNull() ?: 0.0)
        val performanceCap = max(1.0, cap.toDoubleOrNull() ?: 1.0)
        val coeff = parseCoeff(coeffInput) ?: return ExactResult(valid = false)
        val game = gamePower.toDoubleOrNull() ?: 0.0
        val currentPeak = max(0.0, peakNow.toDoubleOrNull() ?: 0.0)
        val unlocked = max(0.0, peakUnlocked.toDoubleOrNull() ?: 0.0)

        val effective = min(perf, performanceCap)
        val power = effective * coeff
        val needPerf = target / coeff
        val capPower = performanceCap * coeff
        val needCoeff = target / performanceCap

        var check = "待填写"
        var checkSub = "填当前游戏战力即可校验"
        if (game > 0.0) {
            val diff = power.roundToInt() - game.roundToInt()
            if (kotlin.math.abs(diff) <= 1) {
                check = "一致"
                checkSub = "公式值 ${fmtInt(power)} · 游戏值 ${fmtInt(game)}"
            } else {
                check = if (diff > 0) "+${fmtInt(diff.toDouble())}" else fmtInt(diff.toDouble())
                checkSub = "公式值与游戏值相差 ${fmtInt(kotlin.math.abs(diff).toDouble())}"
            }
        }
        if (perf > performanceCap) {
            check = "已封顶"
            checkSub = "当前上限 ${fmtInt(performanceCap)} · 额外 ${fmtInt(perf-performanceCap)} 为滚动记录"
        }

        val decision = when {
            power >= target -> "当前输入已经达到目标  公式值约 ${fmtInt(power)} 战力"
            needPerf <= performanceCap -> "固定当前巅峰系数  表现分提高到约 ${fmtInt(ceil(needPerf))} 可达目标"
            else -> "表现分打满 ${fmtInt(performanceCap)} 后约 ${fmtInt(capPower)} 战  需要总巅峰系数至少 ${fmtPct(needCoeff)}"
        } + if (unlocked > 0 && currentPeak > unlocked) {
            "  当前英雄只解锁到 ${fmtInt(unlocked)}  净胜场会限制实际系数阶段"
        } else ""

        return ExactResult(
            valid = true, target = target, power = power, needPerf = needPerf,
            capPower = capPower, needCoeff = needCoeff, coeff = coeff, perf = perf,
            effectivePerf = effective, cap = performanceCap,
            selfCheck = check, selfCheckSub = checkSub, decision = decision
        )
    }

    fun normalizeCurve(points: List<CurvePoint>): List<CurvePoint> =
        points.filter { it.score >= 0 && it.coeffPct > 0 && it.score.isFinite() && it.coeffPct.isFinite() }
            .sortedBy { it.score }

    fun parseCurve(text: String): List<CurvePoint> {
        val list = mutableListOf<CurvePoint>()
        text.split(Regex("\\n+")).forEach { line ->
            val a = line.trim().split(Regex("[,，;\\s]+")).filter { it.isNotBlank() }
            if (a.size >= 2) {
                val s = a[0].toDoubleOrNull()
                val c0 = a[1].toDoubleOrNull()
                if (s != null && c0 != null && s >= 0 && c0 > 0) {
                    val c = if (c0 <= 5.0) c0 * 100.0 else c0
                    list += CurvePoint(s, c)
                }
            }
        }
        return normalizeCurve(list)
    }

    private fun findScore(points: List<CurvePoint>, needPct: Double): Pair<Int,String>? {
        if (points.isEmpty()) return null
        points.firstOrNull { kotlin.math.abs(it.coeffPct - needPct) < 1e-9 }?.let {
            return it.score.roundToInt() to "exact"
        }
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            if (needPct >= a.coeffPct && needPct <= b.coeffPct) {
                val t = (needPct-a.coeffPct) / ((b.coeffPct-a.coeffPct).takeIf { it != 0.0 } ?: 1.0)
                return ceil(a.score + t*(b.score-a.score)).toInt() to "interp"
            }
        }
        if (needPct < points.first().coeffPct) return points.first().score.roundToInt() to "floor"
        return null
    }

    fun peakReverse(
        targetPower: String,
        cap: String,
        currentPeak: String,
        curveText: String,
        estimateAllowed: Boolean
    ): PeakResult {
        val target = max(1.0, targetPower.toDoubleOrNull() ?: 10000.0)
        val performanceCap = max(1.0, cap.toDoubleOrNull() ?: 6100.0)
        val needPct = target / performanceCap * 100.0
        val imported = parseCurve(curveText)
        val points = when {
            imported.size >= 2 -> imported
            estimateAllowed -> referenceCurve
            else -> emptyList()
        }
        val source = when {
            imported.size >= 2 -> "import"
            estimateAllowed -> "reference"
            else -> "none"
        }
        val found = findScore(points, needPct)
        val now = currentPeak.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val score = found?.first
        val gap = score?.let { it - now.roundToInt() }
        val precision = when {
            source == "import" && found?.second == "exact" -> "映射表精确命中"
            source == "import" -> "按导入映射表插值"
            source == "reference" -> "参考曲线估算"
            else -> "等待映射表"
        }
        return PeakResult(needPct, score, gap, source, precision)
    }

    fun matches(text: String, cap: String, recycleRate: String): MatchResult {
        val values = mutableListOf<Double>()
        text.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
            val a = line.split(Regex("[,，;\\s]+")).mapNotNull { it.toDoubleOrNull() }
            when {
                a.size == 1 && a[0] >= 0 -> values += a[0]
                a.size >= 2 && a[0] >= 0 && a[1] > 0 -> values += a[0] * a[1]
            }
        }
        values.sortDescending()
        val best = values.take(45)
        val raw = best.sum()
        val performanceCap = max(1.0, cap.toDoubleOrNull() ?: 6100.0)
        val perf = min(raw, performanceCap)
        val rr = ((recycleRate.toDoubleOrNull() ?: 15.0).coerceIn(0.0,100.0))/100.0
        return MatchResult(values, best, raw, perf, (best.size*rr).roundToInt())
    }

    fun fmtInt(v: Double): String = "%,.0f".format(Locale.US, v)
    fun fmt1(v: Double): String = "%,.1f".format(Locale.US, v)
    fun fmtPct(multiplier: Double): String = "%.2f%%".format(Locale.US, multiplier*100.0)
}

class AppController(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("s44_native", Context.MODE_PRIVATE)

    var targetPower by mutableStateOf(prefs.getString("targetPower","10000") ?: "10000")
    var gamePower by mutableStateOf(prefs.getString("gamePower","") ?: "")
    var rankTier by mutableStateOf(prefs.getString("rankTier","king") ?: "king")
    var rankStars by mutableStateOf(prefs.getString("rankStars","50") ?: "50")
    var performance by mutableStateOf(prefs.getString("performance","5000") ?: "5000")
    var performanceCap by mutableStateOf(prefs.getString("performanceCap","6100") ?: "6100")
    var peakNow by mutableStateOf(prefs.getString("peakNow","1800") ?: "1800")
    var peakUnlocked by mutableStateOf(prefs.getString("peakUnlocked","1800") ?: "1800")
    var coefficientInput by mutableStateOf(prefs.getString("coefficientInput","152.6") ?: "152.6")
    var curveText by mutableStateOf(prefs.getString("curveText","") ?: "")
    var matchText by mutableStateOf(prefs.getString("matchText","") ?: "")
    var matchCap by mutableStateOf(prefs.getString("matchCap","6100") ?: "6100")
    var recycleRate by mutableStateOf(prefs.getString("recycleRate","15") ?: "15")

    var darkTheme by mutableStateOf(prefs.getBoolean("darkTheme", false))
    var estimateAllowed by mutableStateOf(prefs.getBoolean("estimateAllowed", false))
    var haptic by mutableStateOf(prefs.getBoolean("haptic", true))
    var uiSound by mutableStateOf(prefs.getBoolean("uiSound", true))
    var musicAutoplay by mutableStateOf(prefs.getBoolean("musicAutoplay", true))
    var musicLoop by mutableStateOf(prefs.getBoolean("musicLoop", true))
    var musicVolume by mutableStateOf(prefs.getFloat("musicVolume", .55f))
    var glassAlpha by mutableStateOf(prefs.getFloat("glassAlpha", .62f))
    var glassBlur by mutableStateOf(prefs.getFloat("glassBlur", 18f))
    var backgroundVeil by mutableStateOf(prefs.getFloat("backgroundVeil", .18f))
    var fontWeight by mutableStateOf(prefs.getFloat("fontWeight", 500f))

    var customMusicUri by mutableStateOf(prefs.getString("customMusicUri", null))
    var customBackgroundUri by mutableStateOf(prefs.getString("customBackgroundUri", null))
    var customCoverUri by mutableStateOf(prefs.getString("customCoverUri", null))

    var pptAuthorInput by mutableStateOf("")
    var pptSessionVerified by mutableStateOf(false)
    var pptRemembered by mutableStateOf(prefs.getString("pptAuth", "") == PPT_AUTH_SIGNATURE)

    var officialStatus by mutableStateOf("检测官网连接")
    var officialStatusOk by mutableStateOf<Boolean?>(null)

    val exactResult: ExactResult
        get() = S44Math.exact(targetPower, gamePower, performance, performanceCap, coefficientInput, peakNow, peakUnlocked)

    val peakResult: PeakResult
        get() = S44Math.peakReverse(targetPower, performanceCap, peakNow, curveText, estimateAllowed)

    val matchResult: MatchResult
        get() = S44Math.matches(matchText, matchCap, recycleRate)

    fun update(id: String, value: String) {
        when (id) {
            "targetPower" -> targetPower = value
            "gamePower" -> gamePower = value
            "rankStars" -> rankStars = value
            "performance" -> performance = value
            "performanceCap" -> performanceCap = value
            "peakNow" -> peakNow = value
            "peakUnlocked" -> peakUnlocked = value
            "coefficientInput" -> coefficientInput = value
            "curveText" -> curveText = value
            "matchText" -> matchText = value
            "matchCap" -> matchCap = value
            "recycleRate" -> recycleRate = value
            "pptAuthor" -> {
                pptAuthorInput = value.filter { it.isDigit() }.take(10)
                if (pptAuthorInput == PPT_AUTHOR_QQ) pptSessionVerified = true
            }
        }
        save()
    }

    fun setRankTier(value: String) {
        rankTier = value
        if (rankTier == "king" && (rankStars.toIntOrNull() ?: 0) >= 50) {
            performanceCap = "6100"
            matchCap = "6100"
        }
        save()
    }

    fun setDark(v:Boolean){darkTheme=v;save()}
    fun setEstimate(v:Boolean){estimateAllowed=v;save()}
    fun setHaptic(v:Boolean){haptic=v;save()}
    fun setUiSound(v:Boolean){uiSound=v;save()}
    fun setMusicAutoplay(v:Boolean){musicAutoplay=v;save()}
    fun setMusicLoop(v:Boolean){musicLoop=v;save()}
    fun setMusicVolume(v:Float){musicVolume=v.coerceIn(0f,1f);save()}
    fun setGlassAlpha(v:Float){glassAlpha=v.coerceIn(.25f,.9f);save()}
    fun setGlassBlur(v:Float){glassBlur=v.coerceIn(0f,32f);save()}
    fun setBackgroundVeil(v:Float){backgroundVeil=v.coerceIn(0f,.65f);save()}
    fun setFontWeight(v:Float){fontWeight=v.coerceIn(400f,700f);save()}

    fun setCustomMusic(uri: Uri?){customMusicUri=uri?.toString();save()}
    fun setCustomBackground(uri: Uri?){customBackgroundUri=uri?.toString();save()}
    fun setCustomCover(uri: Uri?){customCoverUri=uri?.toString();save()}

    fun isPptVerified(): Boolean =
        pptRemembered || pptSessionVerified || pptAuthorInput == PPT_AUTHOR_QQ

    fun rememberPptVerification(remember: Boolean) {
        pptSessionVerified = true
        pptRemembered = remember
        if (remember) prefs.edit().putString("pptAuth", PPT_AUTH_SIGNATURE).apply()
        else prefs.edit().remove("pptAuth").apply()
    }

    fun restorePptVerification() {
        pptAuthorInput = ""
        pptSessionVerified = false
        pptRemembered = false
        prefs.edit().remove("pptAuth").apply()
    }

    fun snapshotJson(): String {
        val curve = JSONArray()
        S44Math.parseCurve(curveText).forEach { curve.put(JSONArray().put(it.score).put(it.coeffPct)) }
        val matches = JSONArray()
        matchResult.values.forEach { matches.put(it) }
        val r = exactResult
        val result = JSONObject()
            .put("power", if (r.valid) r.power else JSONObject.NULL)
            .put("needPerf", if (r.valid) r.needPerf else JSONObject.NULL)
            .put("capPower", if (r.valid) r.capPower else JSONObject.NULL)
            .put("needCoeff", if (r.valid) r.needCoeff else JSONObject.NULL)

        return JSONObject()
            .put("app","S44英雄战力工具")
            .put("author","秋天")
            .put("targetPower",targetPower.toDoubleOrNull() ?: 10000.0)
            .put("gamePower",gamePower.toDoubleOrNull() ?: JSONObject.NULL)
            .put("performance",performance.toDoubleOrNull() ?: 0.0)
            .put("performanceCap",performanceCap.toDoubleOrNull() ?: 6100.0)
            .put("rankTier",rankTier)
            .put("rankStars",rankStars.toDoubleOrNull() ?: 0.0)
            .put("peakNow",peakNow.toDoubleOrNull() ?: 0.0)
            .put("peakUnlocked",peakUnlocked.toDoubleOrNull() ?: 0.0)
            .put("coefficientInput",coefficientInput.toDoubleOrNull() ?: 0.0)
            .put("curve",curve)
            .put("matches",matches)
            .put("result",result)
            .toString(2)
    }

    fun importJson(text: String) {
        val o = JSONObject(text)
        fun n(key:String, current:String):String =
            if (o.has(key) && !o.isNull(key)) o.optDouble(key, current.toDoubleOrNull() ?: 0.0).toString().trimZeros() else current

        targetPower = n("targetPower",targetPower)
        gamePower = if (o.has("gamePower") && !o.isNull("gamePower")) n("gamePower",gamePower) else ""
        performance = n("performance",performance)
        performanceCap = n("performanceCap",performanceCap)
        rankTier = o.optString("rankTier",rankTier).takeIf { it in listOf("diamond","stellar","king","manual") } ?: "manual"
        rankStars = n("rankStars",rankStars)
        peakNow = n("peakNow",peakNow)
        peakUnlocked = n("peakUnlocked",peakUnlocked)
        coefficientInput = n("coefficientInput",coefficientInput)
        if (o.has("curve")) {
            val arr=o.optJSONArray("curve")
            if(arr!=null){
                val rows=mutableListOf<String>()
                for(i in 0 until arr.length()){
                    val row=arr.optJSONArray(i) ?: continue
                    if(row.length()>=2)rows += "${row.optDouble(0).trimZeros()},${row.optDouble(1).trimZeros()}"
                }
                curveText=rows.joinToString("\n")
            }
        }
        save()
    }

    fun applyImportedFields(fields: Map<String,String>) {
        fields["targetPower"]?.let { targetPower = it.numericClean() }
        fields["gamePower"]?.let { gamePower = it.numericClean() }
        fields["performance"]?.let { performance = it.numericClean() }
        fields["performanceCap"]?.let { performanceCap = it.numericClean() }
        fields["rankStars"]?.let { rankStars = it.numericClean() }
        fields["peakNow"]?.let { peakNow = it.numericClean() }
        fields["peakUnlocked"]?.let { peakUnlocked = it.numericClean() }
        fields["coefficientInput"]?.let { coefficientInput = it.numericClean() }
        fields["rankTier"]?.takeIf { it in listOf("diamond","stellar","king","manual") }?.let { rankTier = it }
        save()
    }

    private fun save() {
        prefs.edit()
            .putString("targetPower",targetPower)
            .putString("gamePower",gamePower)
            .putString("rankTier",rankTier)
            .putString("rankStars",rankStars)
            .putString("performance",performance)
            .putString("performanceCap",performanceCap)
            .putString("peakNow",peakNow)
            .putString("peakUnlocked",peakUnlocked)
            .putString("coefficientInput",coefficientInput)
            .putString("curveText",curveText)
            .putString("matchText",matchText)
            .putString("matchCap",matchCap)
            .putString("recycleRate",recycleRate)
            .putBoolean("darkTheme",darkTheme)
            .putBoolean("estimateAllowed",estimateAllowed)
            .putBoolean("haptic",haptic)
            .putBoolean("uiSound",uiSound)
            .putBoolean("musicAutoplay",musicAutoplay)
            .putBoolean("musicLoop",musicLoop)
            .putFloat("musicVolume",musicVolume)
            .putFloat("glassAlpha",glassAlpha)
            .putFloat("glassBlur",glassBlur)
            .putFloat("backgroundVeil",backgroundVeil)
            .putFloat("fontWeight",fontWeight)
            .apply()
    }

    companion object {
        const val PPT_AUTHOR_QQ = "3694476602"
        const val PPT_AUTH_SIGNATURE = "AUTUMN_3694476602_OK"
    }
}

private fun Double.trimZeros(): String {
    if (!isFinite()) return "0"
    val s = String.format(Locale.US, "%.6f", this).trimEnd('0').trimEnd('.')
    return if (s == "-0") "0" else s
}
private fun String.numericClean(): String {
    val n = replace(",","").replace("%","").trim().toDoubleOrNull() ?: return this
    return n.trimZeros()
}
