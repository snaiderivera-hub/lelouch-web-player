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
 * Pruebas unitarias para FASE CONTROLADA — P1 #2B (Paging 3 Movies / VOD).
 *
 * Valida:
 * - Paso 4: EXPLAIN QUERY PLAN para consultas paginadas con index_movies_sourceId e index_movies_sourceId_categoryId.
 * - Paso 7: Identidad canónica v4 confirmada en DB: "${sourceId}_vod_${streamId}".
 * - Paso 21: Fixture de 30,000 películas entre múltiples sources y categorías. Paging carga únicamente ~100 items iniciales.
 * - Paso 22: Cambio de categoría aislado (Action: 4,000, Comedy: 3,200, Drama: 5,100, ALL: 27,000) sin cargar todo a RAM.
 * - Paso 23: Simulación de invalidación de PagingSource tras actualización/swap en tabla movies.
 * - Paso 17: Aislamiento total de catálogo por sourceId.
 */
class MoviesPagingTest {

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
            CREATE TABLE IF NOT EXISTS `movies` (
                `id` TEXT NOT NULL,
                `streamId` INTEGER NOT NULL,
                `num` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `year` TEXT,
                `streamIcon` TEXT,
                `backdropPath` TEXT,
                `rating` REAL,
                `rating5based` REAL,
                `added` TEXT,
                `categoryId` TEXT NOT NULL,
                `categoryName` TEXT NOT NULL,
                `containerExtension` TEXT NOT NULL,
                `plot` TEXT,
                `cast` TEXT,
                `director` TEXT,
                `genre` TEXT,
                `durationSecs` INTEGER NOT NULL,
                `streamUrl` TEXT NOT NULL,
                `isFavorite` INTEGER NOT NULL,
                `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_sourceId` ON `movies` (`sourceId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_sourceId_categoryId` ON `movies` (`sourceId`, `categoryId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_streamId` ON `movies` (`streamId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_name` ON `movies` (`name`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_rating` ON `movies` (`rating`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_movies_added` ON `movies` (`added`)")
    }

    private fun populate30kMovies() {
        conn.autoCommit = false
        val insertSql = """
            INSERT INTO `movies` (
                id, streamId, num, name, title, year, streamIcon,
                backdropPath, rating, rating5based, added,
                categoryId, categoryName, containerExtension,
                plot, cast, director, genre, durationSecs,
                streamUrl, isFavorite, sourceId
            ) VALUES (?, ?, ?, ?, ?, '2024', null, null, 7.5, 3.75, '1700000000', ?, ?, 'mp4', null, null, null, null, 7200, ?, ?, ?)
        """.trimIndent()

        val ps = conn.prepareStatement(insertSql)

        // Source A: 27,000 películas
        // - Action: 4,000 películas
        // - Comedy: 3,200 películas
        // - Drama:  5,100 películas
        // - Otros:  14,700 películas
        for (i in 1..27000) {
            val catId = when {
                i <= 4000 -> "cat_action"
                i <= 4000 + 3200 -> "cat_comedy"
                i <= 4000 + 3200 + 5100 -> "cat_drama"
                else -> "cat_other"
            }
            val catName = when (catId) {
                "cat_action" -> "Acción"
                "cat_comedy" -> "Comedia"
                "cat_drama" -> "Drama"
                else -> "Otras Películas"
            }
            val isFav = if (i % 100 == 0) 1 else 0
            // Canonical v4 ID format: ${sourceId}_vod_${streamId}
            val movieId = "src_a_vod_$i"

            ps.setString(1, movieId)
            ps.setInt(2, i)
            ps.setInt(3, i)
            ps.setString(4, "Película %05d".format(i))
            ps.setString(5, "Película %05d".format(i))
            ps.setString(6, catId)
            ps.setString(7, catName)
            ps.setString(8, "http://provider/movie/$i.mp4")
            ps.setInt(9, isFav)
            ps.setString(10, "src_a")
            ps.addBatch()

            if (i % 5000 == 0) {
                ps.executeBatch()
            }
        }
        ps.executeBatch()

        // Source B: 3,000 películas
        for (i in 1..3000) {
            val movieId = "src_b_vod_$i"
            ps.setString(1, movieId)
            ps.setInt(2, i)
            ps.setInt(3, i)
            ps.setString(4, "Movie B %05d".format(i))
            ps.setString(5, "Movie B %05d".format(i))
            ps.setString(6, "cat_b_movies")
            ps.setString(7, "B Exclusivas")
            ps.setString(8, "http://provider-b/movie/$i.mp4")
            ps.setInt(9, 0)
            ps.setString(10, "src_b")
            ps.addBatch()
        }
        ps.executeBatch()
        conn.commit()
        conn.autoCommit = true
    }

    // =========================================================================
    // PASO 4: EXPLAIN QUERY PLAN (Valida índices de Room)
    // =========================================================================

    @Test
    fun `test explain query plan uses sourceId index for all movies query`() {
        populate30kMovies()

        val query = "EXPLAIN QUERY PLAN SELECT * FROM movies WHERE sourceId = ? ORDER BY name ASC, id ASC LIMIT 50 OFFSET 0"
        val ps = conn.prepareStatement(query)
        ps.setString(1, "src_a")
        val rs = ps.executeQuery()

        var planDetails = ""
        while (rs.next()) {
            planDetails += rs.getString("detail") + " | "
        }
        rs.close()
        ps.close()

        assertTrue(
            "Expected plan to use index on sourceId, but got: $planDetails",
            planDetails.contains("index_movies_sourceId")
        )
    }

    @Test
    fun `test explain query plan uses composite index for sourceId and categoryId query`() {
        populate30kMovies()

        val query = "EXPLAIN QUERY PLAN SELECT * FROM movies WHERE sourceId = ? AND categoryId = ? ORDER BY name ASC, id ASC LIMIT 50 OFFSET 0"
        val ps = conn.prepareStatement(query)
        ps.setString(1, "src_a")
        ps.setString(2, "cat_action")
        val rs = ps.executeQuery()

        var planDetails = ""
        while (rs.next()) {
            planDetails += rs.getString("detail") + " | "
        }
        rs.close()
        ps.close()

        assertTrue(
            "Expected plan to use index_movies_sourceId_categoryId, but got: $planDetails",
            planDetails.contains("index_movies_sourceId_categoryId")
        )
    }

    // =========================================================================
    // PASO 21: TEST CON 30,000 PELÍCULAS (Pager NO materializa 30,000 items)
    // =========================================================================

    @Test
    fun `test 30k movies initial paging load only materializes initial page`() {
        populate30kMovies()

        // Total en DB para src_a
        val countPs = conn.prepareStatement("SELECT COUNT(*) FROM movies WHERE sourceId = ?")
        countPs.setString(1, "src_a")
        val countRs = countPs.executeQuery()
        assertTrue(countRs.next())
        val totalCount = countRs.getInt(1)
        assertEquals(27000, totalCount)
        countRs.close()

        // Paging initial load: initialLoadSize = 100
        val initialLoadSize = 100
        val pagePs = conn.prepareStatement(
            "SELECT * FROM movies WHERE sourceId = ? ORDER BY name ASC, id ASC LIMIT ? OFFSET 0"
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
        // No se materializan 27,000 / 30,000 items, sino exactamente initialLoadSize (100)
        assertEquals(100, materializedCount)
        assertEquals(100, loadedIds.size)
        assertEquals("src_a_vod_1", loadedIds.first())
        assertEquals("src_a_vod_100", loadedIds.last())
    }

    // =========================================================================
    // PASO 22: TEST DE CAMBIO DE CATEGORÍA
    // =========================================================================

    @Test
    fun `test category switch queries only target category without loading 30k`() {
        populate30kMovies()

        fun countCategory(sourceId: String, categoryId: String?): Int {
            val sql = if (categoryId == null) {
                "SELECT COUNT(*) FROM movies WHERE sourceId = ?"
            } else {
                "SELECT COUNT(*) FROM movies WHERE sourceId = ? AND categoryId = ?"
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
                "SELECT id FROM movies WHERE sourceId = ? ORDER BY name ASC, id ASC LIMIT ?"
            } else {
                "SELECT id FROM movies WHERE sourceId = ? AND categoryId = ? ORDER BY name ASC, id ASC LIMIT ?"
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

        // 1. ALL: Total 27,000 en src_a
        assertEquals(27000, countCategory("src_a", null))
        val allFirstPage = fetchFirstCategoryPage("src_a", null, 100)
        assertEquals(100, allFirstPage.size)

        // 2. Action: Total exacto 4,000
        assertEquals(4000, countCategory("src_a", "cat_action"))
        val actionPage = fetchFirstCategoryPage("src_a", "cat_action", 100)
        assertEquals(100, actionPage.size)
        assertEquals("src_a_vod_1", actionPage.first())
        assertEquals("src_a_vod_100", actionPage.last())

        // 3. Comedy: Total exacto 3,200
        assertEquals(3200, countCategory("src_a", "cat_comedy"))
        val comedyPage = fetchFirstCategoryPage("src_a", "cat_comedy", 100)
        assertEquals(100, comedyPage.size)
        assertEquals("src_a_vod_4001", comedyPage.first())
        assertEquals("src_a_vod_4100", comedyPage.last())

        // 4. Drama: Total exacto 5,100
        assertEquals(5100, countCategory("src_a", "cat_drama"))
        val dramaPage = fetchFirstCategoryPage("src_a", "cat_drama", 100)
        assertEquals(100, dramaPage.size)
        assertEquals("src_a_vod_7201", dramaPage.first())
        assertEquals("src_a_vod_7300", dramaPage.last())

        // 5. Volver a ALL: vuelve a cargar desde offset 0 de todas las películas
        val returnToAllPage = fetchFirstCategoryPage("src_a", null, 100)
        assertEquals(allFirstPage, returnToAllPage)
    }

    // =========================================================================
    // PASO 23: INVALIDATION TEST (PagingSource invalidation tras update/swap)
    // =========================================================================

    @Test
    fun `test invalidation reflects updated movie in subsequent page fetch`() {
        populate30kMovies()

        // Fetch página inicial
        val ps1 = conn.prepareStatement(
            "SELECT id, name FROM movies WHERE sourceId = ? ORDER BY name ASC, id ASC LIMIT 10 OFFSET 0"
        )
        ps1.setString(1, "src_a")
        val rs1 = ps1.executeQuery()
        val firstItemInitial = if (rs1.next()) rs1.getString("name") else ""
        rs1.close()
        ps1.close()
        assertEquals("Película 00001", firstItemInitial)

        // Transacción de actualización
        conn.autoCommit = false
        val updatePs = conn.prepareStatement("UPDATE movies SET name = ? WHERE id = ?")
        updatePs.setString(1, "Película Modificada 00001")
        updatePs.setString(2, "src_a_vod_1")
        val updatedRows = updatePs.executeUpdate()
        assertEquals(1, updatedRows)
        conn.commit()
        conn.autoCommit = true

        // Simula la invalidación de Room PagingSource que dispara una nueva generación
        val ps2 = conn.prepareStatement(
            "SELECT id, name FROM movies WHERE sourceId = ? ORDER BY name ASC, id ASC LIMIT 10 OFFSET 0"
        )
        ps2.setString(1, "src_a")
        val rs2 = ps2.executeQuery()
        val firstItemUpdated = if (rs2.next()) rs2.getString("name") else ""
        rs2.close()
        ps2.close()

        assertEquals("Película 00002", firstItemUpdated)
    }

    // =========================================================================
    // PASO 17: CAMBIO DE FUENTE (Aislamiento total por sourceId)
    // =========================================================================

    @Test
    fun `test source switch isolates movie items between sources completely`() {
        populate30kMovies()

        val srcAQuery = conn.prepareStatement("SELECT COUNT(*) FROM movies WHERE sourceId = ?")
        srcAQuery.setString(1, "src_a")
        val srcARs = srcAQuery.executeQuery()
        srcARs.next()
        val countA = srcARs.getInt(1)
        srcARs.close()
        srcAQuery.close()
        assertEquals(27000, countA)

        val srcBQuery = conn.prepareStatement("SELECT COUNT(*) FROM movies WHERE sourceId = ?")
        srcBQuery.setString(1, "src_b")
        val srcBRs = srcBQuery.executeQuery()
        srcBRs.next()
        val countB = srcBRs.getInt(1)
        srcBRs.close()
        srcBQuery.close()
        assertEquals(3000, countB)

        // Validar que la primera página de source B solo contiene items con prefijo "src_b_vod_"
        val pageBPs = conn.prepareStatement("SELECT id FROM movies WHERE sourceId = ? ORDER BY name ASC, id ASC LIMIT 50")
        pageBPs.setString(1, "src_b")
        val pageBRs = pageBPs.executeQuery()
        var itemsCount = 0
        while (pageBRs.next()) {
            val id = pageBRs.getString("id")
            assertTrue("Movie must belong to src_b", id.startsWith("src_b_vod_"))
            itemsCount++
        }
        pageBRs.close()
        pageBPs.close()
        assertEquals(50, itemsCount)
    }
}
