package dev.penned.app.domain

import org.json.JSONObject

/**
 * 模型返回的一笔账（M4-2，规格 §三 M4-2 / R4）——**四条路径共用的出参形态**。
 *
 * 出参 schema（文本与图像共用）：`{"type":"EXPENSE|INCOME","amount":12.34,"merchant":"肯德基",
 * "time":"2026-10-17 12:30","note":"…"}`。**分类与账户永远不出现在这里**：LLM 不许回分类/账户，
 * 它们一律本地映射（防幻觉的唯一有效手段，§二·4）。
 */
data class LlmDraft(
    /** EXPENSE / INCOME；模型没给或给歪了都按**支出**（与一句话本地解析的缺省一致）。 */
    val type: TxType,
    /** 金额（分）。**缺金额 = 整条判失败**（宁可不记，不记个没数的）。 */
    val amountCents: Long,
    val merchant: String?,
    /** 发生时间（本地墙钟分钟）；模型没给/解析不出 = null，由调用方取"现在/今天"。 */
    val occurredAtEpochMinute: Long?,
    val note: String?,
)

object LlmDraftParser {

    /**
     * 解析 `choices[0].message.content` 里的那段 JSON（**调用方先把 content 取出来**）。
     *
     * 口径（规格 §三 M4-2 + 本地强校验）：
     * - 只认 JSON 对象；模型回的闲聊、```json 围栏、多余字段都当噪声处理掉
     * - **金额必填**：数字或字符串都收（字符串走 `MoneyParse`，`¥1,234.56` / `12元` 也吃）；缺 / 解析不出 → null
     * - 时间尽力解析（`yyyy-MM-dd HH:mm[:ss]`、容 `-`/`/` 分隔），解析不出 → null（**不猜**）
     * - 类型缺省/认不出 → 支出；商户与备注去空白，空串当没给
     *
     * 返回 null = 这次识别不算数（UI 显示失败并可重试）。
     */
    fun parse(content: String?): LlmDraft? {
        val raw = content?.let { stripFence(it) }?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val json = try {
            JSONObject(raw)
        } catch (_: Exception) {
            // 模型爱在 JSON 前后加一句话：退一步找第一个 { 到最后一个 } 再试一次
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            try {
                JSONObject(raw.substring(start, end + 1))
            } catch (_: Exception) {
                return null
            }
        }
        return parseObject(json)
    }

    /**
     * 单个 JSON 对象 → 一笔账（`parse` 与 `LlmRowsParser` 共用这一处字段口径）。
     * 返回 null = 这条不算数（**缺金额 / 金额坏**）。
     */
    fun parseObject(json: JSONObject): LlmDraft? {
        val amountCents = json.opt("amount").let { value ->
            when (value) {
                null, JSONObject.NULL -> null
                is Number -> MoneyParse.centsFromNumber(value.toDouble())
                is String -> MoneyParse.toCents(value)
                else -> null
            }
        } ?: return null

        val type = when (json.optString("type").trim().uppercase()) {
            "INCOME" -> TxType.INCOME
            else -> TxType.EXPENSE
        }

        return LlmDraft(
            type = type,
            amountCents = amountCents,
            merchant = json.optString("merchant").trim().ifEmpty { null },
            occurredAtEpochMinute = parseTimeMinute(json.optString("time")),
            note = json.optString("note").trim().ifEmpty { null },
        )
    }

    /** ```json … ``` 围栏剥掉（模型很爱加，加了也不是"错"——剥完还是严格 JSON 校验）。 */
    internal fun stripFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    /**
     * 时间文本 → `dateEpochMinute`。
     *
     * **口径**（与 `AddDate`/导入器/详情页同一约定）：model 给的是**本地墙钟**，落库要的是
     * **真 epoch 分钟**——过一遍系统时区换算，**不能**写成 `epochDay * 1440 + 时分`。
     *
     * 认什么形态由 [TextTime] 定（D111 起放宽：账单列表截图的 `10月18日 09:23` / `10-16 22:42` /
     * `昨天 23:38` 都认，无年按"最近一次过去"推）；认不出返回 null（**不猜**，退回"现在"）。
     */
    private fun parseTimeMinute(text: String?): Long? = TextTime.minuteFromLooseText(text)
}
