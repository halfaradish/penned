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

/**
 * v6→v7：分类加 `hidden`（M3-1，D074）。
 *
 * 规格原本以为这是 v1 就留的字段——实际**从没有过**（1~6.json 都没有），所以由本片真加一列。
 * 默认 0 = 现有分类全部照旧可见（隐藏只是"不再出现在记账宫格与筛选"，历史流水照常显示）。
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v7→v8：建愿望清单表（M3-15，D072 = 攒钱目标）——**M3 唯一一次迁移**。
 *
 * 表结构与 `WishlistItemEntity` 逐列对齐（Room 的 schema 校验会逐列比对，少一列就报错）；
 * 索引照 `@Entity(indices = [Index("bookId"), Index("accountId")])` 建，名字必须是 `index_<表>_<列>`。
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `wishlist_items` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `targetCents` INTEGER NOT NULL, `savedCents` INTEGER NOT NULL, " +
                "`startEpochDay` INTEGER, `dueEpochDay` INTEGER, `accountId` TEXT, `note` TEXT, " +
                "`status` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `deviceId` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_wishlist_items_bookId` ON `wishlist_items` (`bookId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_wishlist_items_accountId` ON `wishlist_items` (`accountId`)")
    }
}

/**
 * v8→v9：M4-0 一次迁移到位（规格 M4 §二·1 / §三 M4-0）——建 `keyword_rules`（关键词规则）与
 * `pending_entries`（机器识别候选池），给 `transactions` 补 `externalId`（账单去重）与
 * `(bookId, externalId)` **非唯一**索引，给 `import_batches` 补来源与跳过/重复计数三列。
 *
 * 本迁移之后 M4 不再升版本（`TxSource.NOTIFICATION` 那类枚举加值不改 schema）。
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `keyword_rules` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                "`keyword` TEXT NOT NULL, `targetType` TEXT NOT NULL, `targetId` TEXT NOT NULL, " +
                "`priority` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `deviceId` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_keyword_rules_bookId` ON `keyword_rules` (`bookId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_entries` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`sourceApp` TEXT, `rawText` TEXT, `type` TEXT NOT NULL, `amountCents` INTEGER, " +
                "`occurredAtEpochMinute` INTEGER, `merchant` TEXT, `note` TEXT, `categoryId` TEXT, " +
                "`status` TEXT NOT NULL, `txId` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`deleted` INTEGER NOT NULL, `deviceId` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_entries_status` ON `pending_entries` (`status`)")

        db.execSQL("ALTER TABLE transactions ADD COLUMN externalId TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_transactions_bookId_externalId` " +
                "ON `transactions` (`bookId`, `externalId`)"
        )

        // 旧批次都是"一木 xls 导入"，默认值 = YIMU，语义不变（规格 §三 M4-0）
        db.execSQL("ALTER TABLE import_batches ADD COLUMN sourceKind TEXT NOT NULL DEFAULT 'YIMU'")
        db.execSQL("ALTER TABLE import_batches ADD COLUMN skippedCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE import_batches ADD COLUMN duplicateCount INTEGER NOT NULL DEFAULT 0")
    }
}
