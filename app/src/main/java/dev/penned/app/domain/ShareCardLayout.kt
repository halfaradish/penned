package dev.penned.app.domain

/**
 * 数据小卡片的**版式计划**（M3-16，纯函数 + 表驱动测试在 `ShareCardLayoutTest`）。
 *
 * 三款卡片（月汇总 / 占比 / 单笔小票）共用同一套"画什么"的描述：先算出一串
 * [CardText]（文字 + 位置 + 字号 + 语义角色），渲染层（`ShareCardRenderer`）只负责把它们画到 Bitmap 上。
 * 这样"尺寸/关键文案落位"能**在纯 JVM 里断言**（不用真出图），出图只做人工过目。
 *
 * 颜色不在这里定：只给**语义角色**（[CardRole]），由渲染层用**当前主题 token** 映射成实际颜色——
 * 换主题卡片跟着变（规格 M3-16 的口径）。
 */
object ShareCardLayout {

    /** 卡片画布尺寸（px）：固定 1080 宽，高度按内容类型定（分享出去的比例稳定）。 */
    const val WIDTH = 1080
    const val SUMMARY_HEIGHT = 1440
    const val DONUT_HEIGHT = 1440
    const val RECEIPT_HEIGHT = 1350

    /** 文字的角色：渲染层按当前主题取色。 */
    enum class CardRole {
        /** 品牌（标题、强调数字）。 */
        BRAND,

        /** 品牌墨色（次级标题、标签）。 */
        BRAND_INK,

        /** 正文墨色。 */
        ON_SURFACE,

        /** 弱化文字（页脚、单位、日期）。 */
        ON_SURFACE_VARIANT,

        /** 支出红。 */
        EXPENSE,

        /** 收入青绿。 */
        INCOME,

        /** 卡片底（不画字，占位说明用）。 */
        SURFACE,
    }

    /** 一行文字：内容 + 左上角坐标（px）+ 字号（px）+ 角色 + 是否加粗。 */
    data class CardText(
        val text: String,
        val x: Int,
        val y: Int,
        val sizePx: Int,
        val role: CardRole,
        val bold: Boolean = false,
    )

    /** 一张卡片的完整计划：画布尺寸 + 文案 + 色块（进度条这类）。 */
    data class Plan(
        val width: Int,
        val height: Int,
        val title: String,
        val texts: List<CardText>,
        /** 顶部品牌条 / 进度条这类矩形：x, y, w, h + 角色。 */
        val bars: List<CardBar> = emptyList(),
        /** 占比卡的分段（起始比例、占比、角色槽位序号）；空 = 不是占比卡。 */
        val segments: List<CardSegment> = emptyList(),
    ) {
        /** 页脚（每张卡都有：来源与口径，不带任何用户标识）。 */
        val footer: CardText get() = texts.last()
    }

    data class CardBar(val x: Int, val y: Int, val width: Int, val height: Int, val role: CardRole)

    data class CardSegment(val startFraction: Float, val fraction: Float, val slot: Int)

    // —— 版式常量（px，画布 1080 宽）——
    private const val MARGIN = 72
    private const val TITLE_SIZE = 48
    private const val LABEL_SIZE = 34
    private const val VALUE_SIZE = 64
    private const val HERO_SIZE = 96
    private const val FOOTER_SIZE = 28
    private const val BAR_HEIGHT = 12

    /** 页脚：来源 + 口径说明（**不带用户标识、不带账户全名**）。 */
    private const val FOOTER = "由「一笔一记」生成"

    /** ① 月汇总卡：月份 + 收入/支出/结余 + 预算进度（没设预算就不画进度条）。 */
    fun monthSummary(
        monthLabel: String,
        incomeCents: Long,
        expenseCents: Long,
        budgetTotalCents: Long?,
        budgetSpentCents: Long?,
    ): Plan {
        val texts = mutableListOf(
            CardText(monthLabel, MARGIN, 200, TITLE_SIZE, CardRole.BRAND_INK, bold = true),
            CardText("本月结余", MARGIN, 330, LABEL_SIZE, CardRole.ON_SURFACE_VARIANT),
            CardText(
                Money.format(incomeCents - expenseCents),
                MARGIN,
                450,
                HERO_SIZE,
                if (incomeCents - expenseCents < 0) CardRole.EXPENSE else CardRole.BRAND,
                bold = true,
            ),
            CardText("收入", MARGIN, 620, LABEL_SIZE, CardRole.ON_SURFACE_VARIANT),
            CardText(Money.format(incomeCents), MARGIN, 700, VALUE_SIZE, CardRole.INCOME, bold = true),
            CardText("支出", MARGIN + 480, 620, LABEL_SIZE, CardRole.ON_SURFACE_VARIANT),
            CardText(Money.format(expenseCents), MARGIN + 480, 700, VALUE_SIZE, CardRole.EXPENSE, bold = true),
        )
        val bars = mutableListOf<CardBar>()
        if (budgetTotalCents != null && budgetSpentCents != null && budgetTotalCents > 0L) {
            val fraction = BudgetMath.state(budgetTotalCents, budgetSpentCents).usedFraction
            texts += CardText("本月预算", MARGIN, 900, LABEL_SIZE, CardRole.ON_SURFACE_VARIANT)
            texts += CardText(
                "${Money.format(budgetSpentCents)} / ${Money.format(budgetTotalCents)}",
                MARGIN,
                980,
                VALUE_SIZE,
                if (budgetSpentCents > budgetTotalCents) CardRole.EXPENSE else CardRole.ON_SURFACE,
                bold = true,
            )
            bars += CardBar(MARGIN, 1050, WIDTH - MARGIN * 2, BAR_HEIGHT, CardRole.SURFACE)
            bars += CardBar(
                MARGIN,
                1050,
                ((WIDTH - MARGIN * 2) * fraction).toInt().coerceAtLeast(1),
                BAR_HEIGHT,
                if (budgetSpentCents > budgetTotalCents) CardRole.EXPENSE else CardRole.BRAND,
            )
        }
        texts += CardText(FOOTER, MARGIN, SUMMARY_HEIGHT - 80, FOOTER_SIZE, CardRole.ON_SURFACE_VARIANT)
        return Plan(WIDTH, SUMMARY_HEIGHT, "月汇总", texts, bars)
    }

    /**
     * ② 统计占比卡：当期 + 单位（支出/收入）+ Top 分类（最多 6 条）+ 环形分段。
     * `categories` 是已经算好占比的行（`StatsMath.CategoryTotal`）。
     */
    fun categoryShare(
        periodLabel: String,
        unit: String,
        totalCents: Long,
        categories: List<StatsMath.CategoryTotal>,
    ): Plan {
        val top = categories.take(MAX_SHARE_CATEGORIES)
        val texts = mutableListOf(
            CardText(periodLabel, MARGIN, 200, TITLE_SIZE, CardRole.BRAND_INK, bold = true),
            CardText("总$unit", MARGIN, 330, LABEL_SIZE, CardRole.ON_SURFACE_VARIANT),
            CardText(
                Money.format(totalCents),
                MARGIN,
                450,
                HERO_SIZE,
                if (unit == "支出") CardRole.EXPENSE else CardRole.INCOME,
                bold = true,
            ),
        )
        // 环形图占位：圆心与半径（渲染层画环；分段按占比）
        val segments = mutableListOf<CardSegment>()
        var start = 0f
        top.forEachIndexed { index, item ->
            segments += CardSegment(start, item.fraction.coerceIn(0f, 1f), index)
            start += item.fraction
        }
        // 图例：每行 = 名称 + 占比（金额不列——环与占比已经说明问题，多一列会挤到叠字）
        top.forEachIndexed { index, item ->
            val y = DONUT_LEGEND_TOP + index * DONUT_LEGEND_ROW
            texts += CardText(item.name, DONUT_LEGEND_X, y, LABEL_SIZE, CardRole.ON_SURFACE, bold = true)
            texts += CardText(
                "${(item.fraction * 100).toInt()}%",
                DONUT_PERCENT_X,
                y,
                LABEL_SIZE,
                CardRole.ON_SURFACE_VARIANT,
            )
        }
        texts += CardText(FOOTER, MARGIN, DONUT_HEIGHT - 80, FOOTER_SIZE, CardRole.ON_SURFACE_VARIANT)
        return Plan(WIDTH, DONUT_HEIGHT, "${unit}占比", texts, segments = segments)
    }

    /**
     * ③ 单笔账单小票：分类 + 金额 + 日期 + 备注 + 账户。
     * **不含账户全名以外的任何身份信息**（规格：无用户标识）——账户名照实（用户自己写的），
     * 不打印设备号、账本 id 这些。
     */
    fun receipt(
        categoryName: String,
        amountLabel: String,
        dateLabel: String,
        note: String?,
        accountName: String?,
        isExpense: Boolean,
    ): Plan {
        val texts = mutableListOf(
            CardText("账单小票", MARGIN, 200, TITLE_SIZE, CardRole.BRAND_INK, bold = true),
            CardText(categoryName, MARGIN, 340, LABEL_SIZE, CardRole.ON_SURFACE_VARIANT),
            CardText(
                amountLabel,
                MARGIN,
                460,
                HERO_SIZE,
                if (isExpense) CardRole.EXPENSE else CardRole.INCOME,
                bold = true,
            ),
            CardText("日期", MARGIN, 640, LABEL_SIZE, CardRole.ON_SURFACE_VARIANT),
            CardText(dateLabel, MARGIN + 220, 640, LABEL_SIZE, CardRole.ON_SURFACE, bold = true),
        )
        var y = 760
        if (accountName != null) {
            texts += CardText("账户", MARGIN, y, LABEL_SIZE, CardRole.ON_SURFACE_VARIANT)
            texts += CardText(accountName, MARGIN + 220, y, LABEL_SIZE, CardRole.ON_SURFACE, bold = true)
            y += 120
        }
        if (!note.isNullOrBlank()) {
            texts += CardText("备注", MARGIN, y, LABEL_SIZE, CardRole.ON_SURFACE_VARIANT)
            texts += CardText(note, MARGIN + 220, y, LABEL_SIZE, CardRole.ON_SURFACE, bold = true)
        }
        texts += CardText(FOOTER, MARGIN, RECEIPT_HEIGHT - 80, FOOTER_SIZE, CardRole.ON_SURFACE_VARIANT)
        return Plan(WIDTH, RECEIPT_HEIGHT, "账单小票", texts)
    }

    // —— 占比卡专用版式（环在左下、图例在右，两列：名称 + 占比）——
    /** 环心与半径（px）：画在左下区，右边留给图例。 */
    const val DONUT_CX = 300f
    const val DONUT_CY = 820f
    const val DONUT_RADIUS = 170f
    /** 图例：首行 y、行距、名称列 x、占比列 x。 */
    const val DONUT_LEGEND_TOP = 640
    const val DONUT_LEGEND_ROW = 110
    const val DONUT_LEGEND_X = 620
    const val DONUT_PERCENT_X = 900

    /** 占比卡最多列几条（再多字就挤了；超出的部分由"其他"在占比里体现）。 */
    const val MAX_SHARE_CATEGORIES = 6
}
