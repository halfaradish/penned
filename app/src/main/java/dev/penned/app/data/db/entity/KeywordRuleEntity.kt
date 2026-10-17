package dev.penned.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.penned.app.domain.RuleTargetType

/**
 * 关键词规则（M4-0，规格 M4 §三 M4-0 / D100）：**关键词 → 分类或标签**，双目标共用一张表。
 *
 * 它是四条入账路径（一句话 / 账单文件 / 截图 / 通知）共用的**本地映射**底座——命中只是"建议值"，
 * 用户在结果卡/预览页上随时可改（LLM 永远不许回分类 ID，见规格 §二·4）。
 *
 * 归属账本（`bookId`）：目标（分类/标签）都是账本内实体，规则跟着账本走。
 * 匹配口径见 `dev.penned.app.domain.KeywordRules.match`（按 `priority` 升序取第一条命中，
 * 同优先级按 `createdAt`；`enabled = false` 整条跳过）。
 *
 * 本表由 `MIGRATION_8_9` 建。
 */
@Entity(tableName = "keyword_rules", indices = [Index("bookId")])
data class KeywordRuleEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    /** 匹配用的关键词（"包含"匹配，去首尾空白、大小写不敏感）。空关键词**拒绝入库**。 */
    val keyword: String,
    /** 命中后填分类还是标签。 */
    val targetType: RuleTargetType,
    /** 目标实体的 id（`categories.id` 或 `tags.id`）。 */
    val targetId: String,
    /** 排序即优先级：小的先匹配（上下移按钮改的就是它）。 */
    val priority: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)
