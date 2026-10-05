package dev.penned.app.data.seed

import dev.penned.app.data.db.PennedDatabase
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.domain.CategoryType
import java.util.UUID

/**
 * 首启种子：默认账本 + 默认分类树。
 * 支出一级/二级取自一木真实导出观察（docs/一木导出反分析.md），为通用默认而非照抄素材。
 */
object DefaultSeed {

    val expenseTree: LinkedHashMap<String, List<String>> = linkedMapOf(
        "食品餐饮" to listOf("早餐", "午餐", "晚餐", "夜宵", "饮料酒水", "休闲零食", "水果", "生鲜食品", "粮油调味"),
        "购物消费" to listOf("日常家居", "手机数码", "服饰运动", "个护美妆", "虚拟充值", "小玩意"),
        "出行交通" to listOf("公共交通", "共享类", "打车", "电车充电", "路费", "保养修车", "火车"),
        "文化教育" to listOf("书报杂志", "文具", "培训考试", "教材", "学费", "印费"),
        "休闲娱乐" to listOf("游戏", "旅游度假", "电影唱歌"),
        "居家生活" to listOf("水电燃气", "物业费", "维修"),
        "健康医疗" to listOf("医院", "体检", "滋补保健"),
        "其他" to emptyList(),
    )

    val incomeList: List<String> = listOf("工资", "兼职", "理财收益", "红包", "其他收入")

    /** 库里没有账本才种。返回种入的账本；已存在返回 null。 */
    suspend fun seedIfEmpty(db: PennedDatabase, deviceId: String, now: Long): BookEntity? {
        if (db.bookDao().count() > 0) return null
        val book = BookEntity(
            id = UUID.randomUUID().toString(),
            name = "日常账本",
            sortOrder = 0,
            createdAt = now,
            updatedAt = now,
            deviceId = deviceId,
        )
        db.bookDao().upsert(book)

        val categories = buildList {
            var order = 0
            expenseTree.forEach { (parentName, subs) ->
                val parentId = UUID.randomUUID().toString()
                add(
                    CategoryEntity(
                        id = parentId, bookId = book.id, parentId = null,
                        name = parentName, type = CategoryType.EXPENSE, sortOrder = order++,
                        createdAt = now, updatedAt = now, deviceId = deviceId,
                    )
                )
                subs.forEach { sub ->
                    add(
                        CategoryEntity(
                            id = UUID.randomUUID().toString(), bookId = book.id, parentId = parentId,
                            name = sub, type = CategoryType.EXPENSE, sortOrder = order++,
                            createdAt = now, updatedAt = now, deviceId = deviceId,
                        )
                    )
                }
            }
            incomeList.forEach { name ->
                add(
                    CategoryEntity(
                        id = UUID.randomUUID().toString(), bookId = book.id, parentId = null,
                        name = name, type = CategoryType.INCOME, sortOrder = order++,
                        createdAt = now, updatedAt = now, deviceId = deviceId,
                    )
                )
            }
        }
        db.categoryDao().upsertAll(categories)
        return book
    }
}
