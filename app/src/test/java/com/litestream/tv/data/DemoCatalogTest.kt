package com.litestream.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DemoCatalogTest {
    @Test fun demoCatalogHasUniqueIds() {
        assertEquals(DemoCatalog.items.size, DemoCatalog.items.map { it.id }.toSet().size)
    }
    @Test fun moviesAndSeriesAreSeparated() {
        assertEquals(4, DemoCatalog.items.filter { it.type == MediaType.MOVIE }.size)
        assertEquals(2, DemoCatalog.items.filter { it.type == MediaType.SERIES }.size)
    }
}
