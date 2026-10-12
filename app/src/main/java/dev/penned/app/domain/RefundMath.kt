package dev.penned.app.domain

/**
 * 退款 / 优惠（M3-2，D069「保存净额」）。
 *
 * 落库的 `amountCents` **恒为净额** = 原价 − 退款 − 优惠；`refundCents`/`discountCents` 存各自原值
 * （保留"原价多少、退了多少"的事实）。编辑时把净额反推回原价填进金额框，往返幂等。
 *
 * 为什么是净额：余额与统计只读 `amountCents`，一行的口径不动；导入的历史数据（金额列本就是净额）
 * 与新录入天然自洽——若改成"存原值、重算时再减"，余额/统计的每个口径都要改，还会和导入数据打架。
 */
object RefundMath {

    /**
     * 保存：净额 = 原价 − 退款 − 优惠。
     * 下限 0：退超了不拦（一木同口径），但金额恒为正（D001），最多记 0。
     */
    fun netCents(originalCents: Long, refundCents: Long, discountCents: Long): Long =
        (originalCents - refundCents - discountCents).coerceAtLeast(0L)

    /**
     * 编辑回填：反推原价 = 净额 + 退款 + 优惠（[netCents] 的逆）。
     * 退款/优惠都是 0 时等于净额本身——绝大多数流水走的就是这条路径。
     */
    fun originalCents(netCents: Long, refundCents: Long, discountCents: Long): Long =
        netCents + refundCents + discountCents

    /** 全额退款（详情页「已全额退款」标记）：退了钱且净额为 0。 */
    fun isFullyRefunded(netCents: Long, refundCents: Long): Boolean =
        refundCents > 0L && netCents == 0L

    /**
     * 记一笔信息条的「实付」行：把输入的三个数写成一条算式，例如
     * `实付 12.00（20.00 − 退款 3.00 − 优惠 5.00）`。都没填时返回 null（不占位置）。
     */
    fun summaryText(originalCents: Long, refundCents: Long, discountCents: Long): String? {
        if (refundCents <= 0L && discountCents <= 0L) return null
        val parts = buildList {
            add(Money.format(originalCents))
            if (refundCents > 0L) add("退款 ${Money.format(refundCents)}")
            if (discountCents > 0L) add("优惠 ${Money.format(discountCents)}")
        }
        val net = netCents(originalCents, refundCents, discountCents)
        return "实付 ${Money.format(net)}（${parts.joinToString(" − ")}）"
    }
}
