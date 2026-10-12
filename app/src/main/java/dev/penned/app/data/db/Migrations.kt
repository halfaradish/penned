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

/** v4→v5：账户-生效账本多对多表（D028：生效账本可勾选多个账本）。 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `account_books` (`accountId` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, " +
                "`deviceId` TEXT NOT NULL, PRIMARY KEY(`accountId`, `bookId`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_account_books_bookId` ON `account_books` (`bookId`)")
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

/**
 * v5→v6：M3 一次建齐所需的表与列（specs/M3.md M3-0）。
 *
 * - 流水：`reimburseCents`（M3-3 报销金额，缺省 0 = 按净额全额）、`recurringRuleId`（M3-6）、`debtId`（D068 债务）
 * - 账户：年费四列（M3-11）
 * - 新表：模板 / 模板标签 / 周期规则 / 账单组合 / 理财快照 / 债务
 *
 * 之后 M3 各切片只写 DAO/UI，不再逐片升版本（唯一例外：M3-15 愿望清单，v6→v7）。
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN reimburseCents INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN recurringRuleId TEXT")
        db.execSQL("ALTER TABLE transactions ADD COLUMN debtId TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_recurringRuleId` ON `transactions` (`recurringRuleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_debtId` ON `transactions` (`debtId`)")

        db.execSQL("ALTER TABLE accounts ADD COLUMN annualFeeCents INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE accounts ADD COLUMN feeMonth INTEGER")
        db.execSQL("ALTER TABLE accounts ADD COLUMN feeDay INTEGER")
        db.execSQL("ALTER TABLE accounts ADD COLUMN lastFeePaidYear INTEGER")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tx_templates` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `type` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `categoryId` TEXT, " +
                "`accountId` TEXT, `targetAccountId` TEXT, `note` TEXT, `feeCents` INTEGER NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`deleted` INTEGER NOT NULL, `deviceId` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tx_templates_bookId` ON `tx_templates` (`bookId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `template_tags` (`templateId` TEXT NOT NULL, `tagId` TEXT NOT NULL, " +
                "PRIMARY KEY(`templateId`, `tagId`))"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_rules` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `type` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `categoryId` TEXT, " +
                "`accountId` TEXT, `targetAccountId` TEXT, `note` TEXT, `feeCents` INTEGER NOT NULL, " +
                "`periodType` TEXT NOT NULL, `interval` INTEGER NOT NULL, `startEpochDay` INTEGER NOT NULL, " +
                "`nextEpochDay` INTEGER NOT NULL, `endEpochDay` INTEGER, `enabled` INTEGER NOT NULL, " +
                "`lastGeneratedAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`deleted` INTEGER NOT NULL, `deviceId` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_bookId` ON `recurring_rules` (`bookId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bill_filters` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `keyword` TEXT, `type` TEXT, `fromEpochDay` INTEGER, `toEpochDay` INTEGER, " +
                "`categoryIds` TEXT, `accountIds` TEXT, `tagIds` TEXT, `amountMinCents` INTEGER, " +
                "`amountMaxCents` INTEGER, `excludeFromStats` INTEGER, `excludeFromBudget` INTEGER, " +
                "`includeTransfer` INTEGER, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `deviceId` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_filters_bookId` ON `bill_filters` (`bookId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `asset_snapshots` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, " +
                "`dateEpochDay` INTEGER NOT NULL, `valueCents` INTEGER NOT NULL, `note` TEXT, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, " +
                "`deviceId` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_asset_snapshots_accountId` ON `asset_snapshots` (`accountId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `debts` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                "`direction` TEXT NOT NULL, `counterparty` TEXT NOT NULL, `principalCents` INTEGER NOT NULL, " +
                "`startEpochDay` INTEGER NOT NULL, `dueEpochDay` INTEGER, `note` TEXT, `closed` INTEGER NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`deleted` INTEGER NOT NULL, `deviceId` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_bookId` ON `debts` (`bookId`)")
    }
}
