package dev.penned.app.data.llm

import dev.penned.app.domain.LlmConfig
import dev.penned.app.domain.LlmSuggestions

/**
 * 导入里的 LLM 辅助（M4-6）：**只做两件事**——列映射建议与商户分类建议（R12）。
 *
 * 外发边界（§二·13，假服务器测试会逐条断）：
 * - 列映射：**默认只发文件表头行**；认不出必需列时用户可点「让 AI 再看 3 行样例」，
 *   那时补发的前 3 行**必须先过 `LlmSuggestions.maskRow`**（数字全部换成 `#`）——
 *   否则金额/时间/单号就跟着出去了，与"一律不出设备"的硬口径直接冲突（M4-6 把这层掩码写进链路）
 * - 商户分类：只发**去重后的商户名**（≤100/批）+ 现有分类名（含"一级 / 二级"路径）；
 *   **不发金额、不发时间、不发单号、不发备注**
 *
 * 出参一律经 `LlmSuggestions` 本地校验（列名要在表头里真实存在、分类只按名匹配）。
 */
class LlmImportAssist(private val client: LlmClient = LlmClient()) {

    /** ① 列映射建议：只发**表头行**（+ 可选 3 行**掩码后**的样例行）。 */
    suspend fun suggestColumnMapping(
        config: LlmConfig,
        header: List<String>,
        sampleRows: List<List<String>> = emptyList(),
    ): LlmContentResult {
        val prompt = buildString {
            appendLine("下面是一份账单文件的表头（每列用 | 分隔）。请把每个字段映射到列名上，只回 JSON。")
            appendLine("字段：time(交易时间)、amount(金额)、direction(收/支)、merchant(交易对方)、item(商品说明)、")
            appendLine("status(交易状态)、externalId(交易单号)、payment(支付方式)、category(交易分类)、note(备注)。")
            appendLine("找不到的字段就给空串。**列名必须原样来自表头**，不要自己编。")
            appendLine("表头：${header.joinToString(" | ")}")
            if (sampleRows.isNotEmpty()) {
                appendLine("（用户同意后补发的前 ${sampleRows.size} 行样例，**已把数字全部替换成 #**，只看形状）")
                sampleRows.forEach { row ->
                    appendLine(LlmSuggestions.maskRow(row).joinToString(" | "))
                }
            }
            appendLine("只回 JSON，例如 {\"time\":\"交易时间\",\"amount\":\"金额(元)\",\"direction\":\"收/支\"}")
        }
        return client.complete(config, prompt)
    }

    /** ② 商户分类建议：只发商户名与分类名（一批 ≤100 个商户）。 */
    suspend fun suggestMerchantCategories(
        config: LlmConfig,
        merchants: List<String>,
        categoryPaths: List<String>,
    ): LlmContentResult {
        val prompt = buildString {
            appendLine("下面是一批商户名，请各归到给定分类里最合适的一个。只回 JSON：商户名 → 分类名。")
            appendLine("**只能从给出的分类名里选**（可以带「 / 」写成一级 / 二级），拿不准就给空串。")
            appendLine("分类名：${categoryPaths.joinToString("、")}")
            appendLine("商户名：")
            merchants.forEach { appendLine("- $it") }
            appendLine("只回 JSON，例如 {\"肯打鸡\":\"食品餐饮\",\"某咖啡\":\"食品餐饮 / 早餐\"}")
        }
        return client.complete(config, prompt)
    }

    val clientRef: LlmClient get() = client
}
