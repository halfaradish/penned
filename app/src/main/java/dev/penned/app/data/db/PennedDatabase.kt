package dev.penned.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.penned.app.data.db.dao.AccountDao
import dev.penned.app.data.db.dao.BillFilterDao
import dev.penned.app.data.db.dao.BookDao
import dev.penned.app.data.db.dao.BudgetDao
import dev.penned.app.data.db.dao.CategoryDao
import dev.penned.app.data.db.dao.DebtDao
import dev.penned.app.data.db.dao.ImportBatchDao
import dev.penned.app.data.db.dao.KeywordRuleDao
import dev.penned.app.data.db.dao.PendingEntryDao
import dev.penned.app.data.db.dao.RecurringDao
import dev.penned.app.data.db.dao.SnapshotDao
import dev.penned.app.data.db.dao.TagDao
import dev.penned.app.data.db.dao.TemplateDao
import dev.penned.app.data.db.dao.TransactionDao
import dev.penned.app.data.db.dao.WishlistDao
import dev.penned.app.data.db.entity.AccountBookRefEntity
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.AssetSnapshotEntity
import dev.penned.app.data.db.entity.AttachmentEntity
import dev.penned.app.data.db.entity.BillFilterEntity
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.BudgetEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.DebtEntity
import dev.penned.app.data.db.entity.ImportBatchEntity
import dev.penned.app.data.db.entity.KeywordRuleEntity
import dev.penned.app.data.db.entity.PendingEntryEntity
import dev.penned.app.data.db.entity.RecurringRuleEntity
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.data.db.entity.TemplateTagRefEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.data.db.entity.TransactionTagRefEntity
import dev.penned.app.data.db.entity.TxTemplateEntity
import dev.penned.app.data.db.entity.WishlistItemEntity

@Database(
    entities = [
        BookEntity::class,
        AccountEntity::class,
        AccountBookRefEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TagEntity::class,
        TransactionTagRefEntity::class,
        AttachmentEntity::class,
        BudgetEntity::class,
        ImportBatchEntity::class,
        TxTemplateEntity::class,
        TemplateTagRefEntity::class,
        RecurringRuleEntity::class,
        BillFilterEntity::class,
        AssetSnapshotEntity::class,
        DebtEntity::class,
        WishlistItemEntity::class,
        KeywordRuleEntity::class,
        PendingEntryEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PennedDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun budgetDao(): BudgetDao
    abstract fun tagDao(): TagDao
    abstract fun debtDao(): DebtDao
    abstract fun templateDao(): TemplateDao
    abstract fun recurringDao(): RecurringDao
    abstract fun billFilterDao(): BillFilterDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun keywordRuleDao(): KeywordRuleDao
    abstract fun pendingEntryDao(): PendingEntryDao

    companion object {
        /** 全部迁移。生产构建与 migration 测试共用这一份，测试不会漏掉新迁移。 */
        val MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9,
        )

        fun build(context: Context): PennedDatabase =
            Room.databaseBuilder(context, PennedDatabase::class.java, "penned.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
