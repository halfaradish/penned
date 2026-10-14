package dev.penned.app.ui.assets

import dev.penned.app.data.db.dao.AccountWithBalance
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.domain.AccountType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 资产页与选择器的纯口径（M3-9）：在用/已停用分段、选择器不列停用（但保留本笔已选中的）、
 * 分组标题 groupName 优先。
 */
class AccountOpsTest {

    private fun balance(id: String, name: String, archived: Boolean = false, groupName: String? = null) =
        AccountWithBalance(id, name, AccountType.DEBIT, groupName, 100L, archived = archived)

    private fun account(id: String, name: String, archived: Boolean = false) = AccountEntity(
        id = id, bookId = "b1", name = name, type = AccountType.DEBIT,
        archived = archived, createdAt = 0, updatedAt = 0, deviceId = "d",
    )

    @Test
    fun `在用与已停用分段：净资产两边都算（这里只验分段）`() {
        val accounts = listOf(
            balance("a1", "微信"),
            balance("a2", "旧卡", archived = true),
            balance("a3", "现金"),
            balance("a4", "旧钱包", archived = true),
        )
        val (active, archived) = partitionArchived(accounts)
        assertEquals(listOf("a1", "a3"), active.map { it.id })
        assertEquals(listOf("a2", "a4"), archived.map { it.id })
        assertEquals("两边合起来还是全部（净资产不会漏）", accounts.size, active.size + archived.size)
    }

    @Test
    fun `选择器不列停用账户，但本笔已选中的要留着`() {
        val accounts = listOf(
            account("a1", "微信"),
            account("a2", "旧卡", archived = true),
            account("a3", "旧钱包", archived = true),
        )
        assertEquals(listOf("a1"), selectableAccounts(accounts, emptyList()).map { it.id })
        // 编辑一条用停用账户记的流水：那个账户必须在（否则看不到自己）
        assertEquals(listOf("a1", "a2"), selectableAccounts(accounts, listOf("a2")).map { it.id })
        // 转账两端都可能是停用账户
        assertEquals(listOf("a1", "a2", "a3"), selectableAccounts(accounts, listOf("a2", "a3")).map { it.id })
        // null 不误伤
        assertEquals(listOf("a1"), selectableAccounts(accounts, listOf(null)).map { it.id })
    }

    @Test
    fun `分组标题：groupName 优先于账户大类`() {
        assertEquals("资金账户", accountGroupTitle(null, AccountType.DEBIT))
        assertEquals("旅行基金", accountGroupTitle("旅行基金", AccountType.DEBIT))
        assertEquals("已停用段标题不参与大类推导", ARCHIVED_SECTION_TITLE, "已停用")
    }

    @Test
    fun `自定义分组排在已知大类之后，按名排`() {
        val accounts = listOf(
            balance("a1", "微信"),
            balance("a2", "旅行基金", groupName = "旅行基金"),
            balance("a3", "现金"),
            balance("a4", "装修款", groupName = "装修款"),
        )
        assertEquals(
            listOf("资金账户", "旅行基金", "装修款"),
            groupAccounts(accounts).map { it.first },
        )
    }
}
