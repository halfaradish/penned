package dev.penned.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.penned.app.data.db.dao.AccountDao
import dev.penned.app.data.db.dao.BookDao
import dev.penned.app.data.db.dao.BudgetDao
import dev.penned.app.data.db.dao.CategoryDao
import dev.penned.app.data.db.dao.ImportBatchDao
import dev.penned.app.data.db.dao.TransactionDao
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.data.db.entity.AttachmentEntity
import dev.penned.app.data.db.entity.BookEntity
import dev.penned.app.data.db.entity.BudgetEntity
import dev.penned.app.data.db.entity.CategoryEntity
import dev.penned.app.data.db.entity.ImportBatchEntity
import dev.penned.app.data.db.entity.TagEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.data.db.entity.TransactionTagRefEntity

@Database(
    entities = [
        BookEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TagEntity::class,
        TransactionTagRefEntity::class,
        AttachmentEntity::class,
        BudgetEntity::class,
        ImportBatchEntity::class,
    ],
    version = 4,
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

    companion object {
        fun build(context: Context): PennedDatabase =
            Room.databaseBuilder(context, PennedDatabase::class.java, "penned.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
