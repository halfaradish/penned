package dev.penned.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.data.db.entity.TemplateTagRefEntity
import dev.penned.app.data.db.entity.TxTemplateEntity
import dev.penned.app.domain.TxType
import kotlinx.coroutines.flow.Flow

/**
 * 模板列表行（M3-5）：模板 + 分类/账户名 + 标签名（`、` 连接，便于行副标题一行写完）。
 * 排序按 sortOrder（上下移按钮改的就是它），同序按创建时间。
 */
data class TemplateWithTags(
    val id: String,
    val name: String,
    val type: TxType,
    val amountCents: Long,
    val categoryId: String?,
    val accountId: String?,
    val targetAccountId: String?,
    val note: String?,
    val feeCents: Long,
    val sortOrder: Int,
    val categoryName: String?,
    val accountName: String?,
    val targetAccountName: String?,
    /** 标签名（`、` 连接）；没有标签为 null。 */
    val tagNames: String?,
)

/**
 * 记账模板（M3-5；表 M3-0 就建好）。
 *
 * 关系表 `template_tags` 照 `transaction_tags` 先例（无软删字段）：换标签 = 整批删 + 插（[setTagsForTemplate] 单事务全量替换，幂等）。
 * **软删模板不连带删任何流水**——模板从不生成流水（用模板 = 进记一笔预填，确认后才入库）。
 */
@Dao
interface TemplateDao {

    @Query(
        """
        SELECT t.id AS id, t.name AS name, t.type AS type, t.amountCents AS amountCents,
               t.categoryId AS categoryId, t.accountId AS accountId, t.targetAccountId AS targetAccountId,
               t.note AS note, t.feeCents AS feeCents, t.sortOrder AS sortOrder,
               c.name AS categoryName, a.name AS accountName, ta.name AS targetAccountName,
               (SELECT GROUP_CONCAT(g.name, '、') FROM tags g
                  INNER JOIN template_tags r ON r.tagId = g.id
                 WHERE r.templateId = t.id AND g.deleted = 0) AS tagNames
        FROM tx_templates t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN accounts a ON a.id = t.accountId
        LEFT JOIN accounts ta ON ta.id = t.targetAccountId
        WHERE t.deleted = 0 AND t.bookId = :bookId
        ORDER BY t.sortOrder, t.createdAt
        """
    )
    fun observeByBook(bookId: String): Flow<List<TemplateWithTags>>

    @Query("SELECT * FROM tx_templates WHERE deleted = 0 AND bookId = :bookId ORDER BY sortOrder, createdAt")
    suspend fun allInBook(bookId: String): List<TxTemplateEntity>

    /** 不滤 deleted：编辑页要能读回（与 `tagDao().get` 同口径）。 */
    @Query("SELECT * FROM tx_templates WHERE id = :id")
    suspend fun get(id: String): TxTemplateEntity?

    @Upsert
    suspend fun upsert(template: TxTemplateEntity)

    /**
     * 软删模板；**不碰任何流水**（模板从不生成流水），**也不摘标签关联**——
     * 关系行留着，撤销删除时标签跟着一起回来；`tagsForTemplate` 按 `tags.deleted = 0` 过滤，
     * 标签自己软删后照样读不到（与 `transaction_tags` 同一口径）。
     */
    @Query("UPDATE tx_templates SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Transaction
    suspend fun deleteTemplate(id: String, now: Long) = softDelete(id, now)

    @Query("SELECT COUNT(*) FROM tx_templates WHERE deleted = 0 AND bookId = :bookId")
    suspend fun countInBook(bookId: String): Int

    // —— 标签关系（照 transaction_tags）——

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRef(ref: TemplateTagRefEntity)

    @Query("DELETE FROM template_tags WHERE templateId = :templateId")
    suspend fun clearTemplateRefs(templateId: String)

    /** 全量替换模板的标签（单事务；重复 id 只留一条，幂等）。 */
    @Transaction
    suspend fun setTagsForTemplate(templateId: String, tagIds: List<String>) {
        clearTemplateRefs(templateId)
        tagIds.distinct().forEach { insertRef(TemplateTagRefEntity(templateId, it)) }
    }

    /** 模板的标签（编辑页回填）。 */
    @Query(
        """
        SELECT g.* FROM tags g INNER JOIN template_tags r ON r.tagId = g.id
        WHERE r.templateId = :templateId AND g.deleted = 0 ORDER BY g.sortOrder, g.name
        """
    )
    suspend fun tagsForTemplate(templateId: String): List<TagEntity>
}
