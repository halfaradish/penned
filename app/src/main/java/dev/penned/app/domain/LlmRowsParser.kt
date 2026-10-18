package dev.penned.app.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一次识别回的一批账（M4-7 改成**账单列表导入**后的出参，D111）。
 *
 * @param rows 认出来的账（按屏幕从上到下）
 * @param droppedRows **有行但读不出金额**、被丢掉的行数（要在预览页显示出来，不静默丢）
 * @param truncated 超过 [LlmRowsParser.MAX_ROWS] 被截断（列表图一屏十几行，防疯跑）
 */
data class LlmRows(
    val rows: List<LlmDraft>,
    val droppedRows: Int = 0,
    val truncated: Boolean = false,
)

/**
 * 账单列表截图的出参解析（纯函数 + 表驱动，规格 §二·8）。
 *
 * 出参 schema（prompt 要求的形态）：`{"rows":[{…一笔…},{…},…]}`——一笔一个对象，
 * 字段与文字识别共用（见 [LlmDraft]）。
 *
 * 容忍度（模型不会每次都照格式来，实测必踩）：
 * - `{rows:[…]}` / `{items:[…]}` / `{list:[…]}` / `{data:[…]}` / **裸数组** `[…]`
 * - ```json 围栏、JSON 前后各一句闲聊
 * - 模型忽略"回数组"的指示、直接回**一个对象** → 当成一笔处理
 *
 * 判失败（返回 null = 这次识别不算数，UI 显示"没看出这是账单"）：
 * - 内容为空 / 压根不是 JSON / JSON 里既没有数组也没有 `amount`
 *
 * 注意 `{"rows":[]}` **不是失败**：那表示"这张图里没有账单"（模型按 prompt 的约定回的），
 * 调用方按"没读出任何一笔"处理。
 */
object LlmRowsParser {

    /** 一次最多收多少笔（超了截断并标记）。列表长截图一屏十几行，300 够用且防模型疯跑。 */
    const val MAX_ROWS = 300

    /** 兜底键名：模型可能把数组放在别的字段里。 */
    private val ARRAY_KEYS = listOf("rows", "items", "list", "transactions", "data", "entries")

    fun parse(content: String?): LlmRows? {
        val raw = content?.let { LlmDraftParser.stripFence(it) }?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val array = findArray(raw) ?: return null
        return fromArray(array)
    }

    private fun findArray(raw: String): JSONArray? {
        // 直接是数组
        try {
            return JSONArray(raw)
        } catch (_: Exception) {
            // 继续：可能是对象包着数组，或前后有闲聊
        }

        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = try {
            JSONObject(raw.substring(start, end + 1))
        } catch (_: Exception) {
            // 退一步：找第一个 [ 到最后一个 ]（模型回的是裸数组但前后有闲聊）
            val arrStart = raw.indexOf('[')
            val arrEnd = raw.lastIndexOf(']')
            if (arrStart < 0 || arrEnd <= arrStart) return null
            return try {
                JSONArray(raw.substring(arrStart, arrEnd + 1))
            } catch (_: Exception) {
                null
            }
        }

        for (key in ARRAY_KEYS) {
            json.optJSONArray(key)?.let { return it }
        }

        // 模型忽略"回数组"的指示，直接回了一个对象 → 当成一笔
        return if (json.opt("amount") != null) JSONArray().put(json) else null
    }

    private fun fromArray(array: JSONArray): LlmRows {
        val rows = mutableListOf<LlmDraft>()
        var dropped = 0
        var truncated = false
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
            val draft = item?.let { LlmDraftParser.parseObject(it) }
            if (draft == null) {
                // 非对象 / 缺金额 / 金额坏：丢这一行，但计数（预览页要显示"忽略 N 行"）
                dropped++
                continue
            }
            if (rows.size >= MAX_ROWS) {
                truncated = true
                continue
            }
            rows += draft
        }
        return LlmRows(rows = rows, droppedRows = dropped, truncated = truncated)
    }
}

/**
 * 落库备注：**商户名 + 状态/说明**。
 *
 * 口径（D111）：支付宝列表里的「等待确认收货」「自动扣款成功」这类状态文字**不能丢**——
 * prompt 让模型把它写进 `note`，这里与商户名拼起来落成流水的备注，用户能在预览/详情里看到。
 * 两段一样时只留一份（模型常把商户名同时写进 note）。
 */
fun LlmDraft.mergedNote(): String? =
    listOfNotNull(merchant, note)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" ")
        .ifEmpty { null }
