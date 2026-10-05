package com.lelouch.core.database

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Pruebas unitarias para FASE CONTROLADA — P1 #2A (Paging 3 Live TV).
 *
 * Valida:
 * - Paso 3: EXPLAIN QUERY PLAN para consultas paginadas con index_channels_sourceId e index_channels_sourceId_categoryId.
 * - Paso 20: Fixture de 50,000 canales entre múltiples sources y categorías. Paging carga únicamente ~100 items iniciales.
 * - Paso 21: Scroll (offset 0, 100, 1000, 5000, back) sin duplicate keys y con orden determinista (num ASC, name ASC, id ASC).
 * - Paso 22: Simulación de invalidación de PagingSource tras actualización/swap en channels.
 * - Paso 23: Cambio de categoría aislado (Cat A: 1,250, Cat B: 2,800, ALL: 48,000) sin cargar todo a RAM.
 */
class LiveChannelsPagingTest {

    private lateinit var conn: Connection

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        initSchema()
    }

    @After
    fun tearDown() {
        if (!conn.isClosed) conn.close()
    }

    private fun initSchema() {
        val stmt = conn.createStatement()
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS `channels` (
                `id` TEXT NOT NULL,
                `streamId` INTEGER NOT NULL,
                `num` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `streamType` TEXT NOT NULL,
                `streamIcon` TEXT,
                `categoryId` TEXT NOT NULL,
                `categoryName` TEXT NOT NULL,
                `epgChannelId` TEXT,
                `isAdult` INTEGER NOT NULL,
                `isFavorite` INTEGER NOT NULL,
                `streamUrl` TEXT NOT NULL,
                `containerExtension` TEXT NOT NULL,
                `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        stmt.execute("CREATE INDEX IF NOT EXISTS `index_channels_sourceId` ON `channels` (`sourceId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_channels_sourceId_categoryId` ON `channels` (`sourceId`, `categoryId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_channels_streamId` ON `channels` (`streamId`)")
    }

    private fun populate50kChannels() {
        conn.autoCommit = false
        val insertSql = """
            INSERT INTO `channels` (
                id, streamId, num, name, streamType, streamIcon,
                categoryId, categoryName, epgChannelId, isAdult, isFavorite,
                streamUrl, containerExtension, sourceId
            ) VALUES (?, ?, ?, ?, 'live', null, ?, ?, null, 0, ?, ?, 'm3u8', ?)
        """.trimIndent()

        val ps = conn.prepareStatement(insertSql)

        // Source A: 48,000 canales
        // - Cat A ("cat_a"): 1,250 canales
        // - Cat B ("cat_b"): 2,800 canales
        // - Cat C ("cat_c"): 43,950 canales
        for (i in 1..48000) {
            val catId = when {
                i <= 1250 -> "cat_a"
                i <= 1250 + 2800 -> "cat_b"
                else -> "cat_c"
            }
            val catName = when (catId) {
                "cat_a" -> "Noticias"
                "cat_b" -> "Deportes"
                else -> "Variedades"
            }
            val isFav = if (i % 50 == 0) 1 else 0
            val channelId = "src_a:$i"

            ps.setString(1, channelId)
            ps.setInt(2, i)
            ps.setInt(3, i)
            ps.setString(4, "Canal %05d".format(i))
            ps.setString(5, catId)
            ps.setString(6, catName)
            ps.setInt(7, isFav)
            ps.setString(8, "http://iptv.provider/live/user/pass/$i.m3u8")
            ps.setString(9, "src_a")
            ps.addBatch()

            if (i % 5000 == 0) {
                ps.executeBatch()
            }
        }
        ps.executeBatch()

        // Source B: 2,000 canales
        for (i in 1..2000) {
            val channelId = "src_b:$i"
            ps.setString(1, channelId)
            ps.setInt(2, i)
            ps.setInt(3, i)
            ps.setString(4, "Canal B %05d".format(i))
            ps.setString(5, "cat_b_only")
            ps.setString(6, "Internacional")
            ps.setInt(7, 0)
            ps.setString(8, "http://provider-b/live/$i.m3u8")
            ps.setString(9, "src_b")
            ps.addBatch()
        }
        ps.executeBatch()
        conn.commit()
        conn.autoCommit = true
    }

    // =========================================================================
    // PASO 3: EXPLAIN QUERY PLAN (Valida índices de Room)
    // =========================================================================

    @Test
    fun `test explain query plan uses sourceId index for all channels query`() {
        populate50kChannels()

        val query = "EXPLAIN QUERY PLAN SELECT * FROM channels WHERE sourceId = ? ORDER BY num ASC, name ASC, id ASC LIMIT 50 OFFSET 0"
        val ps = conn.prepareStatement(query)
        ps.setString(1, "src_a")
        val rs = ps.executeQuery()

        var planDetails = ""
        while (rs.next()) {
            planDetails += rs.getString("detail") + " | "
        }
        rs.close()
        ps.close()

        // Debe usar index_channels_sourceId (o index_channels_sourceId_categoryId con sourceId prefijo)
        assertTrue(
            "Expected plan to use index on sourceId, but got: $planDetails",
            planDetails.contains("index_channels_sourceId")
        )
    }

    @Test
    fun `test explain query plan uses composite index for sourceId and categoryId query`() {
        populate50kChannels()

        val query = "EXPLAIN QUERY PLAN SELECT * FROM channels WHERE sourceId = ? AND categoryId = ? ORDER BY num ASC, name ASC, id ASC LIMIT 50 OFFSET 0"
        val ps = conn.prepareStatement(query)
        ps.setString(1, "src_a")
        ps.setString(2, "cat_b")
        val rs = ps.executeQuery()

        var planDetails = ""
        while (rs.next()) {
            planDetails += rs.getString("detail") + " | "
        }
        rs.close()
        ps.close()

        assertTrue(
            "Expected plan to use index_channels_sourceId_categoryId, but got: $planDetails",
            planDetails.contains("index_channels_sourceId_categoryId")
        )
    }

    // =========================================================================
    // PASO 20: TEST CON 50,000 CANALES (Pager NO materializa 50,000 items)
    // =========================================================================

    @Test
    fun `test 50k channels initial paging load only materializes initial page`() {
        populate50kChannels()

        // Total en DB
        val countPs = conn.prepareStatement("SELECT COUNT(*) FROM channels WHERE sourceId = ?")
        countPs.setString(1, "src_a")
        val countRs = countPs.executeQuery()
        assertTrue(countRs.next())
        val totalCount = countRs.getInt(1)
        assertEquals(48000, totalCount)
        countRs.close()

        // Paging initial load: initialLoadSize = 100
        val initialLoadSize = 100
        val pagePs = conn.prepareStatement(
            "SELECT * FROM channels WHERE sourceId = ? ORDER BY num ASC, name ASC, id ASC LIMIT ? OFFSET 0"
        )
        pagePs.setString(1, "src_a")
        pagePs.setInt(2, initialLoadSize)
        val rs = pagePs.executeQuery()

        var materializedCount = 0
        val loadedIds = mutableListOf<String>()
        while (rs.next()) {
            materializedCount++
            loadedIds.add(rs.getString("id"))
        }
        rs.close()
        pagePs.close()

        // VERIFICACIÓN CLAVE:
        // No se materializan 48,000 / 50,000 items, sino exactamente initialLoadSize (100)
        assertEquals(100, materializedCount)
        assertEquals(100, loadedIds.size)
        assertEquals("src_a:1", loadedIds.first())
        assertEquals("src_a:100", loadedIds.last())
    }

    // =========================================================================
    // PASO 21: TEST DE SCROLL Y ORDEN DETERMINISTA
    // =========================================================================

    @Test
    fun `test paging scroll offsets maintain stable ordering and no duplicates`() {
        populate50kChannels()

        val query = "SELECT id, num, name FROM channels WHERE sourceId = ? ORDER BY num ASC, name ASC, id ASC LIMIT ? OFFSET ?"

        fun fetchPage(limit: Int, offset: Int): List<String> {
            val ps = conn.prepareStatement(query)
            ps.setString(1, "src_a")
            ps.setInt(2, limit)
            ps.setInt(3, offset)
            val rs = ps.executeQuery()
            val list = mutableListOf<String>()
            while (rs.next()) {
                list.add(rs.getString("id"))
            }
            rs.close()
            ps.close()
            return list
        }

        // 1. Initial Page (0..99)
        val initialPage = fetchPage(limit = 100, offset = 0)
        assertEquals(100, initialPage.size)
        assertEquals("src_a:1", initialPage.first())

        // 2. Scroll 100 (100..149)
        val pageAt100 = fetchPage(limit = 50, offset = 100)
        assertEquals(50, pageAt100.size)
        assertEquals("src_a:101", pageAt100.first())

        // Sin intersección (sin duplicados entre páginas consecutivas)
        val intersection = initialPage.toSet().intersect(pageAt100.toSet())
        assertTrue("No duplicate keys between adjacent pages", intersection.isEmpty())

        // 3. Scroll 1,000 (1000..1049)
        val pageAt1000 = fetchPage(limit = 50, offset = 1000)
        assertEquals(50, pageAt1000.size)
        assertEquals("src_a:1001", pageAt1000.first())

        // 4. Scroll 5,000 (5000..5049)
        val pageAt5000 = fetchPage(limit = 50, offset = 5000)
        assertEquals(50, pageAt5000.size)
        assertEquals("src_a:5001", pageAt5000.first())

        // 5. Scroll de regreso al inicio (offset 0)
        val backToTop = fetchPage(limit = 50, offset = 0)
        assertEquals(50, backToTop.size)
        assertEquals("src_a:1", backToTop.first())
        assertEquals("src_a:50", backToTop.last())
    }

    // =========================================================================
    // PASO 22: INVALIDATION TEST (PagingSource invalidation tras swap/update)
    // =========================================================================

    @Test
    fun `test invalidation reflects updated data in subsequent page fetches`() {
        populate50kChannels()

        // Fetch página inicial
        val ps1 = conn.prepareStatement(
            "SELECT id, name FROM channels WHERE sourceId = ? ORDER BY num ASC, name ASC, id ASC LIMIT 10 OFFSET 0"
        )
        ps1.setString(1, "src_a")
        val rs1 = ps1.executeQuery()
        val firstItemInitial = if (rs1.next()) rs1.getString("name") else ""
        rs1.close()
        ps1.close()
        assertEquals("Canal 00001", firstItemInitial)

        // Transacción de actualización (simula atomic swap / rename de canal)
        conn.autoCommit = false
        val updatePs = conn.prepareStatement("UPDATE channels SET name = ? WHERE id = ?")
        updatePs.setString(1, "Canal Modificado 00001")
        updatePs.setString(2, "src_a:1")
        val updatedRows = updatePs.executeUpdate()
        assertEquals(1, updatedRows)
        conn.commit()
        conn.autoCommit = true

        // Simula la invalidación de Room PagingSource que dispara una nueva generación de fetch
        val ps2 = conn.prepareStatement(
            "SELECT id, name FROM channels WHERE sourceId = ? ORDER BY num ASC, name ASC, id ASC LIMIT 10 OFFSET 0"
        )
        ps2.setString(1, "src_a")
        val rs2 = ps2.executeQuery()
        val firstItemUpdated = if (rs2.next()) rs2.getString("name") else ""
        rs2.close()
        ps2.close()

        assertEquals("Canal Modificado 00001", firstItemUpdated)
    }

    // =========================================================================
    // PASO 23: PRUEBAS DE CAMBIO DE CATEGORÍA
    // =========================================================================

    @Test
    fun `test category switch queries only target category without loading 50k`() {
        populate50kChannels()

        fun countCategory(sourceId: String, categoryId: String?): Int {
            val sql = if (categoryId == null) {
                "SELECT COUNT(*) FROM channels WHERE sourceId = ?"
            } else {
                "SELECT COUNT(*) FROM channels WHERE sourceId = ? AND categoryId = ?"
            }
            val ps = conn.prepareStatement(sql)
            ps.setString(1, sourceId)
            if (categoryId != null) ps.setString(2, categoryId)
            val rs = ps.executeQuery()
            rs.next()
            val c = rs.getInt(1)
            rs.close()
            ps.close()
            return c
        }

        fun fetchFirstCategoryPage(sourceId: String, categoryId: String?, limit: Int = 100): List<String> {
            val sql = if (categoryId == null) {
                "SELECT id FROM channels WHERE sourceId = ? ORDER BY num ASC, name ASC, id ASC LIMIT ?"
            } else {
                "SELECT id FROM channels WHERE sourceId = ? AND categoryId = ? ORDER BY num ASC, name ASC, id ASC LIMIT ?"
            }
            val ps = conn.prepareStatement(sql)
            ps.setString(1, sourceId)
            if (categoryId != null) {
                ps.setString(2, categoryId)
                ps.setInt(3, limit)
            } else {
                ps.setInt(2, limit)
            }
            val rs = ps.executeQuery()
            val list = mutableListOf<String>()
            while (rs.next()) {
                list.add(rs.getString("id"))
            }
            rs.close()
            ps.close()
            return list
        }

        // 1. ALL: Total 48,000 en src_a
        assertEquals(48000, countCategory("src_a", null))
        val allFirstPage = fetchFirstCategoryPage("src_a", null, 100)
        assertEquals(100, allFirstPage.size)

        // 2. Categoría A ("cat_a"): Total exacto 1,250
        assertEquals(1250, countCategory("src_a", "cat_a"))
        val catAPage = fetchFirstCategoryPage("src_a", "cat_a", 100)
        assertEquals(100, catAPage.size)
        assertEquals("src_a:1", catAPage.first())
        assertEquals("src_a:100", catAPage.last())

        // 3. Categoría B ("cat_b"): Total exacto 2,800
        assertEquals(2800, countCategory("src_a", "cat_b"))
        val catBPage = fetchFirstCategoryPage("src_a", "cat_b", 100)
        assertEquals(100, catBPage.size)
        assertEquals("src_a:1251", catBPage.first())
        assertEquals("src_a:1350", catBPage.last())

        // 4. Volver a ALL: vuelve a cargar desde offset 0 de todos los canales
        val returnToAllPage = fetchFirstCategoryPage("src_a", null, 100)
        assertEquals(allFirstPage, returnToAllPage)
    }

    // =========================================================================
    // PASO 11: CAMBIO DE FUENTE (Aislamiento total por sourceId)
    // =========================================================================

    @Test
    fun `test source switch isolates items between sources completely`() {
        populate50kChannels()

        val srcAQuery = conn.prepareStatement("SELECT COUNT(*) FROM channels WHERE sourceId = ?")
        srcAQuery.setString(1, "src_a")
        val srcARs = srcAQuery.executeQuery()
        srcARs.next()
        val countA = srcARs.getInt(1)
        srcARs.close()
        srcAQuery.close()
        assertEquals(48000, countA)

        val srcBQuery = conn.prepareStatement("SELECT COUNT(*) FROM channels WHERE sourceId = ?")
        srcBQuery.setString(1, "src_b")
        val srcBRs = srcBQuery.executeQuery()
        srcBRs.next()
        val countB = srcBRs.getInt(1)
        srcBRs.close()
        srcBQuery.close()
        assertEquals(2000, countB)

        // Validar que la primera página de source B solo contiene items con prefijo "src_b:"
        val pageBPs = conn.prepareStatement("SELECT id FROM channels WHERE sourceId = ? ORDER BY num ASC, name ASC, id ASC LIMIT 50")
        pageBPs.setString(1, "src_b")
        val pageBRs = pageBPs.executeQuery()
        var itemsCount = 0
        while (pageBRs.next()) {
            val id = pageBRs.getString("id")
            assertTrue("Item must belong to src_b", id.startsWith("src_b:"))
            itemsCount++
        }
        pageBRs.close()
        pageBPs.close()
        assertEquals(50, itemsCount)
    }
}
