package dev.penned.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {

    @Test
    fun `blank input is not searchable`() {
        assertFalse(SearchQuery.isSearchable(""))
        assertFalse(SearchQuery.isSearchable("   "))
        assertTrue(SearchQuery.isSearchable("遮光布"))
    }

    @Test
    fun `like pattern wraps and escapes wildcards`() {
        assertEquals("%晚餐%", SearchQuery.likePattern("晚餐"))
        // 用户输入 % 必须按字面匹配（否则 LIKE '%' 会命中全部）
        assertEquals("%100\\%%", SearchQuery.likePattern("100%"))
        assertEquals("%a\\_b%", SearchQuery.likePattern("a_b"))
        assertEquals("%c\\\\d%", SearchQuery.likePattern("c\\d"))
        // 首尾空白忽略
        assertEquals("%工资%", SearchQuery.likePattern("  工资  "))
    }

    @Test
    fun `numeric keyword also matches amount in cents`() {
        assertEquals(1600L, SearchQuery.amountCentsOrNull("16"))
        assertEquals(1650L, SearchQuery.amountCentsOrNull("16.5"))
        assertEquals(1650L, SearchQuery.amountCentsOrNull("16.50"))
        assertNull(SearchQuery.amountCentsOrNull("晚餐"))
        assertNull(SearchQuery.amountCentsOrNull("0"))
        assertNull(SearchQuery.amountCentsOrNull(""))
    }
}
