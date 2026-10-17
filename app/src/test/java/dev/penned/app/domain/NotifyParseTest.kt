package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知文案解析（M4-8）：**脱敏 fixture**，微信 ≥6 条、支付宝 ≥6 条（真实通知文案没有样本，
 * 这些都是按两家常见形态自己写的——board 已记）。外加"解析不出就丢弃"的一批边界。
 *
 * 口径：特征词命中 + 两位小数金额 + 方向能判出，三者缺一即 null（宁可漏，不可错记）。
 */
class NotifyParseTest {

    private val wx = "com.tencent.mm"
    private val ali = "com.eg.android.AlipayGphone"

    @Test
    fun `微信 6 条：付款 收款 转账 红包退回 提现 群收款`() {
        val cases = listOf(
            Triple("微信支付", "微信支付 12.50 已支付", true),
            Triple("微信收款", "微信支付 收款 88.00 元已到账", false),
            Triple("微信支付", "转账 52.00 元 支付成功", true),
            Triple("微信", "红包退回 8.88 元已退款", false),
            Triple("微信支付", "零钱提现 100.00 元 扣款成功", true),
            Triple("微信群收款", "收款到账 30.00 元", false),
        )
        cases.forEach { (title, text, expense) ->
            val draft = NotifyParse.parse(wx, title, text)
            assertEquals("该条应解析出：$text", true, draft != null)
            assertEquals("方向应与预期一致：$text", expense, draft!!.expense)
            assertEquals(wx, draft.packageName)
        }
    }

    @Test
    fun `支付宝 6 条：付款 收款 花呗扣款 转账 余额宝收益 退款`() {
        val cases = listOf(
            Triple("支付宝", "支付宝 付款成功 12.00 元", true),
            Triple("支付宝", "收款到账 20.00 元", false),
            Triple("支付宝", "花呗 扣款 99.00 元成功", true),
            Triple("支付宝", "转账 报至对方 52.00 元 支付成功", true),
            Triple("余额宝", "余额宝 收益 1.23 元已存入", false),
            Triple("支付宝", "退款 6.00 元 已到账", false),
        )
        cases.forEach { (title, text, expense) ->
            val draft = NotifyParse.parse(ali, title, text)
            assertEquals("该条应解析出：$text", true, draft != null)
            assertEquals("方向应与预期一致：$text", expense, draft!!.expense)
        }
    }

    @Test
    fun `金额与方向都要能判出 否则丢弃`() {
        // 没有金额
        assertNull(NotifyParse.parse(wx, "微信支付", "支付成功"))
        // 金额不是两位小数（表单里的时间/单号不该当钱）
        assertNull(NotifyParse.parse(wx, "微信支付", "支付成功 12.5 元"))
        // 方向判不出（特征词没命中方向词）
        assertNull(NotifyParse.parse(wx, "微信", "你今天有 12.00 元的记录"))
        // 方向两边都命中（含糊文案）→ 丢弃，不猜
        assertNull(NotifyParse.parse(ali, "支付宝", "退款 收款到账 支出 12.00 元"))
        // 金额为 0
        assertNull(NotifyParse.parse(wx, "微信支付", "支付成功 0.00 元"))
    }

    @Test
    fun `白名单外的 App 一律不解析`() {
        assertNull(NotifyParse.parse("com.example.other", "支付宝", "支付成功 12.00 元"))
        assertNull(NotifyParse.parse("com.android.sms", "短信", "支出 12.00 元"))
    }

    @Test
    fun `空文案与只有标题都不算`() {
        assertNull(NotifyParse.parse(wx, null, null))
        assertNull(NotifyParse.parse(wx, "", "   "))
    }

    @Test
    fun `金额带符号与千分位也能认`() {
        assertEquals(123_456L, NotifyParse.parse(wx, "微信支付", "付款成功 ¥1,234.56")!!.amountCents)
        assertEquals(1200L, NotifyParse.parse(wx, "微信支付", "支付成功 12.00元")!!.amountCents)
    }

    @Test
    fun `提醒文案`() {
        val draft = NotifyParse.parse(wx, "微信支付", "支付成功 12.50 元")!!
        assertEquals("识别到一笔 12.50 的支出，点这里确认", NotifyParse.alertText(draft))
    }

    @Test
    fun `白名单解析：逗号串 去空白 空串等于空集`() {
        assertEquals(setOf("a", "b"), NotifyParse.whitelist("a, b ,"))
        assertTrue(NotifyParse.whitelist(null).isEmpty())
        assertTrue(NotifyParse.whitelist("").isEmpty())
        assertTrue(NotifyParse.whitelist("   ").isEmpty())
    }
}
