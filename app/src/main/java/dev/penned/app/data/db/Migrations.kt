package dev.penned.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v2→v3：流水关联导入批次（specs/M1.md 第二批：导入批次管理页按批次整体删除）。 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN importBatchId TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_importBatchId` ON `transactions` (`importBatchId`)")
    }
}

/** v3→v4：账户加备注与生效账本（D027：备注只作备忘；scopeAllBooks 默认 0 = 老账户仍只在自己账本可见）。 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN remark TEXT")
        db.execSQL("ALTER TABLE accounts ADD COLUMN scopeAllBooks INTEGER NOT NULL DEFAULT 0")
    }
}

/** v1→v2：流水加 D020~D022 字段；新增 budgets / import_batches（specs/M1.md S1）。 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN feeCents INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN excludeFromStats INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN excludeFromBudget INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                "`categoryId` TEXT, `amountCents` INTEGER NOT NULL, `periodType` TEXT NOT NULL, " +
                "`startEpochDay` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`deleted` INTEGER NOT NULL, `deviceId` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_bookId` ON `budgets` (`bookId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `import_batches` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                "`fileName` TEXT NOT NULL, `count` INTEGER NOT NULL, `importedAt` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, " +
                "`deviceId` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_batches_bookId` ON `import_batches` (`bookId`)")
    }
}
