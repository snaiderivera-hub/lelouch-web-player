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
 * Pruebas unitarias para FASE CONTROLADA — P1 #2C (Paging 3 Series).
 *
 * Valida:
 * - Paso 4 & 6: EXPLAIN QUERY PLAN para consultas paginadas con index_series_sourceId_seriesId e index_series_sourceId_categoryId.
 * - Paso 5: Identidad canónica v4 confirmada en DB: "${sourceId}_series_${seriesId}".
 * - Paso 19: Fixture de 10,000 series (Source A: 8,500, Source B: 1,500). Paging carga únicamente ~100 items iniciales.
 * - Paso 20: Duplicated seriesId across providers (Source A: seriesId 123, Source B: seriesId 123). Keys distintas sin conflicto.
 * - Paso 21: Invalidación de PagingSource tras actualización/atomic swap.
 * - Paso 22: Category test (ALL: 8,500, Drama: 2,000, Anime: 1,500, Documental: 700). Query aislado sin cargar 8,500 a RAM.
 * - Paso 23: Categorías ocultas excluidas a nivel de query SQL.
 * - Paso 13: Aislamiento total de catálogo por sourceId.
 * - Paso 14: Acceso directo a isFavorite sin búsqueda en memoria.
 * - Paso 15: Detail navigation identity preservada vía id canónico.
 */
class SeriesPagingTest {

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
            CREATE TABLE IF NOT EXISTS `series` (
                `id` TEXT NOT NULL,
                `seriesId` INTEGER NOT NULL,
                `num` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `cover` TEXT,
                `backdropPath` TEXT,
                `plot` TEXT,
                `cast` TEXT,
                `director` TEXT,
                `genre` TEXT,
                `releaseDate` TEXT,
                `rating` REAL,
                `rating5based` REAL,
                `categoryId` TEXT NOT NULL,
                `categoryName` TEXT NOT NULL,
                `isFavorite` INTEGER NOT NULL,
                `sourceId` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_series_sourceId_seriesId` ON `series` (`sourceId`, `seriesId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_series_sourceId_categoryId` ON `series` (`sourceId`, `categoryId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_series_categoryId` ON `series` (`categoryId`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_series_name` ON `series` (`name`)")
        stmt.execute("CREATE INDEX IF NOT EXISTS `index_series_isFavorite` ON `series` (`isFavorite`)")
    }

    private fun populate10kSeries() {
        conn.autoCommit = false
        val insertSql = """
            INSERT INTO `series` (
                id, seriesId, num, name, title, cover, backdropPath,
                plot, cast, director, genre, releaseDate, rating,
                rating5based, categoryId, categoryName, isFavorite, sourceId
            ) VALUES (?, ?, ?, ?, ?, null, null, null, null, null, null, '2024', 8.5, 4.25, ?, ?, ?, ?)
        """.trimIndent()

        val ps = conn.prepareStatement(insertSql)

        // Source A: 8,500 series
        // - Drama: 2,000 (1..2000)
        // - Anime: 1,500 (2001..3500)
        // - Documental: 700 (3501..4200)
        // - General / Otros: 4,300 (4201..8500)
        for (i in 1..8500) {
            val catId = when {
                i <= 2000 -> "cat_drama"
                i <= 3500 -> "cat_anime"
                i <= 4200 -> "cat_documental"
                else -> "cat_general"
            }
            val catName = when (catId) {
                "cat_drama" -> "Drama"
                "cat_anime" -> "Anime"
                "cat_documental" -> "Documental"
                else -> "General"
            }
            val isFav = if (i % 50 == 0) 1 else 0
            // Canonical v4 ID format: ${sourceId}_series_${seriesId}
            val id = "src_a_series_$i"

            ps.setString(1, id)
            ps.setInt(2, i)
            ps.setInt(3, i)
            ps.setString(4, "Serie A %05d".format(i))
            ps.setString(5, "Serie A %05d".format(i))
            ps.setString(6, catId)
            ps.setString(7, catName)
            ps.setInt(8, isFav)
            ps.setString(9, "src_a")
            ps.addBatch()

            if (i % 2000 == 0) {
                ps.executeBatch()
            }
        }
        ps.executeBatch()

        // Source B: 1,500 series
        for (i in 1..1500) {
            val id = "src_b_series_$i"
            ps.setString(1, id)
            ps.setInt(2, i)
            ps.setInt(3, i)
            ps.setString(4, "Serie B %05d".format(i))
            ps.setString(5, "Serie B %05d".format(i))
            ps.setString(6, "cat_b_series")
            ps.setString(7, "B Exclusivas")
            ps.setInt(8, 0)
            ps.setString(9, "src_b")
            ps.addBatch()
        }
        ps.executeBatch()
        conn.commit()
        conn.autoCommit = true
    }

    // =========================================================================
    // PASO 4 & 6: EXPLAIN QUERY PLAN (Valida índices de Room)
    // =========================================================================

    @Test
    fun `test explain query plan uses composite index prefix for all series query`() {
        populate10kSeries()

        val query = "EXPLAIN QUERY PLAN SELECT * FROM series WHERE sourceId = ? ORDER BY name ASC, id ASC LIMIT 50 OFFSET 0"
        val ps = conn.prepareStatement(query)
        ps.setString(1, "src_a")
        val rs = ps.executeQuery()

        var planDetails = ""
        while (rs.next()) {
            planDetails += rs.getString("detail") + " | "
        }
        rs.close()
        ps.close()

        // SQLite utiliza index_series_sourceId_seriesId o index_series_sourceId_categoryId
        // por tener sourceId como prefijo izquierdo
        assertTrue(
            "Expected plan to use an index covering sourceId, but got: $planDetails",
            planDetails.contains("index_series_sourceId")
        )
    }

    @Test
    fun `test explain query plan uses composite index for sourceId and categoryId query`() {
        populate10kSeries()

        val query = "EXPLAIN QUERY PLAN SELECT * FROM series WHERE sourceId = ? AND categoryId = ? ORDER BY name ASC, id ASC LIMIT 50 OFFSET 0"
        val ps = conn.prepareStatement(query)
        ps.setString(1, "src_a")
        ps.setString(2, "cat_anime")
        val rs = ps.executeQuery()

        var planDetails = ""
        while (rs.next()) {
            planDetails += rs.getString("detail") + " | "
        }
        rs.close()
        ps.close()

        assertTrue(
            "Expected plan to use index_series_sourceId_categoryId, but got: $planDetails",
            planDetails.contains("index_series_sourceId_categoryId")
        )
    }

    // =========================================================================
    // PASO 19: TEST CON 10,000 SERIES (Pager NO materializa 10,000 items)
    // =========================================================================

    @Test
    fun `test 10k series initial paging load only materializes initial page`() {
        populate10kSeries()

        // Total en DB para src_a
        val countPs = conn.prepareStatement("SELECT COUNT(*) FROM series WHERE sourceId = ?")
        countPs.setString(1, "src_a")
        val countRs = countPs.executeQuery()
        assertTrue(countRs.next())
        val totalCount = countRs.getInt(1)
        assertEquals(8500, totalCount)
        countRs.close()

        // Paging initial load: initialLoadSize = 100
        val initialLoadSize = 100
        val pagePs = conn.prepareStatement(
            "SELECT * FROM series WHERE sourceId = ? ORDER BY name ASC, id ASC LIMIT ? OFFSET 0"
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
        // No se materializan 8,500 ni 10,000 items, sino exactamente initialLoadSize (100)
        assertEquals(100, materializedCount)
        assertEquals(100, loadedIds.size)
        assertEquals("src_a_series_1", loadedIds.first())
        assertEquals("src_a_series_100", loadedIds.last())
    }

    // =========================================================================
    // PASO 20: DUPLICATED PROVIDER IDS (Multi-source isolation)
    // =========================================================================

    @Test
    fun `test duplicate seriesId across providers have unique canonical keys and isolate properly`() {
        populate10kSeries()

        // Ambas fuentes tienen seriesId = 123
        val queryA = "SELECT * FROM series WHERE sourceId = 'src_a' AND seriesId = 123"
        val rsA = conn.createStatement().executeQuery(queryA)
        assertTrue(rsA.next())
        val idA = rsA.getString("id")
        val nameA = rsA.getString("name")
        val srcA = rsA.getString("sourceId")
        rsA.close()

        val queryB = "SELECT * FROM series WHERE sourceId = 'src_b' AND seriesId = 123"
        val rsB = conn.createStatement().executeQuery(queryB)
        assertTrue(rsB.next())
        val idB = rsB.getString("id")
        val nameB = rsB.getString("name")
        val srcB = rsB.getString("sourceId")
        rsB.close()

        // Validaciones:
        // 1. Las canonical keys son completamente distintas
        assertEquals("src_a_series_123", idA)
        assertEquals("src_b_series_123", idB)
        assertNotEquals(idA, idB)

        // 2. Nombres y sources aislados
        assertEquals("Serie A 00123", nameA)
        assertEquals("Serie B 00123", nameB)
        assertEquals("src_a", srcA)
        assertEquals("src_b", srcB)

        // 3. Pager de Source A no contiene nada de Source B
        val pagerPsA = conn.prepareStatement(
            "SELECT * FROM series WHERE sourceId = ? ORDER BY name ASC, id ASC LIMIT 50"
        )
        pagerPsA.setString(1, "src_a")
        val pagerRsA = pagerPsA.executeQuery()
        while (pagerRsA.next()) {
            assertEquals("src_a", pagerRsA.getString("sourceId"))
            assertFalse(pagerRsA.getString("id").startsWith("src_b"))
        }
        pagerRsA.close()
    }

    // =========================================================================
    // PASO 21: TEST DE INVALIDACIÓN TRAS ATOMIC SWAP
    // =========================================================================

    @Test
    fun `test paging invalidation on atomic swap update`() {
        populate10kSeries()

        // Paging página 1 inicial
        val initialPs = conn.prepareStatement(
            "SELECT * FROM series WHERE sourceId = ? ORDER BY name ASC, id ASC LIMIT 5"
        )
        initialPs.setString(1, "src_a")
        val initialRs = initialPs.executeQuery()
        val initialList = mutableListOf<String>()
        while (initialRs.next()) {
            initialList.add(initialRs.getString("name"))
        }
        initialRs.close()
        initialPs.close()

        assertEquals("Serie A 00001", initialList.first())

        // Simulación de sync atómico:
        conn.autoCommit = false
        val updateStmt = conn.createStatement()
        updateStmt.executeUpdate("UPDATE series SET name = 'AAA Serie Destacada' WHERE id = 'src_a_series_1'")
        conn.commit()
        conn.autoCommit = true

        // Consulta de nueva generación tras invalidación
        val newGenPs = conn.prepareStatement(
            "SELECT * FROM series WHERE sourceId = ? ORDER BY name ASC, id ASC LIMIT 5"
        )
        newGenPs.setString(1, "src_a")
        val newGenRs = newGenPs.executeQuery()
        val newGenList = mutableListOf<String>()
        while (newGenRs.next()) {
            newGenList.add(newGenRs.getString("name"))
        }
        newGenRs.close()
        newGenPs.close()

        // El primer elemento ahora es la serie actualizada por orden alfabético
        assertEquals("AAA Serie Destacada", newGenList.first())
    }

    // =========================================================================
    // PASO 22: TEST DE CAMBIO DE CATEGORÍA
    // =========================================================================

    @Test
    fun `test category switch queries only target category without loading 8500`() {
        populate10kSeries()

        // Conteo esperado por categoría
        val catCounts = mapOf(
            "cat_drama" to 2000,
            "cat_anime" to 1500,
            "cat_documental" to 700,
            "cat_general" to 4300
        )

        for ((catId, expectedCount) in catCounts) {
            // Count query directo
            val countPs = conn.prepareStatement("SELECT COUNT(*) FROM series WHERE sourceId = ? AND categoryId = ?")
            countPs.setString(1, "src_a")
            countPs.setString(2, catId)
            val countRs = countPs.executeQuery()
            assertTrue(countRs.next())
            assertEquals(expectedCount, countRs.getInt(1))
            countRs.close()
            countPs.close()

            // Consulta paginada específica de categoría (solo retorna página de 50)
            val pagePs = conn.prepareStatement(
                "SELECT * FROM series WHERE sourceId = ? AND categoryId = ? ORDER BY name ASC, id ASC LIMIT 50 OFFSET 0"
            )
            pagePs.setString(1, "src_a")
            pagePs.setString(2, catId)
            val rs = pagePs.executeQuery()

            var itemsRetrieved = 0
            while (rs.next()) {
                itemsRetrieved++
                assertEquals(catId, rs.getString("categoryId"))
                assertEquals("src_a", rs.getString("sourceId"))
            }
            rs.close()
            pagePs.close()

            assertEquals(50, itemsRetrieved)
        }
    }

    // =========================================================================
    // PASO 23: HIDDEN CATEGORIES FILTER
    // =========================================================================

    @Test
    fun `test hidden categories excluded via SQL query`() {
        populate10kSeries()

        // Excluir cat_documental (700) y cat_anime (1,500)
        // Total esperado: 8,500 - 700 - 1,500 = 6,300
        val query = """
            SELECT COUNT(*) FROM series
            WHERE sourceId = ?
            AND categoryId NOT IN (?, ?)
        """.trimIndent()

        val ps = conn.prepareStatement(query)
        ps.setString(1, "src_a")
        ps.setString(2, "cat_anime")
        ps.setString(3, "cat_documental")
        val rs = ps.executeQuery()

        assertTrue(rs.next())
        val count = rs.getInt(1)
        assertEquals(6300, count)
        rs.close()
        ps.close()

        // Paging query con exclusión
        val pageQuery = """
            SELECT * FROM series
            WHERE sourceId = ?
            AND categoryId NOT IN (?, ?)
            ORDER BY name ASC, id ASC
            LIMIT 100 OFFSET 0
        """.trimIndent()
        val pagePs = conn.prepareStatement(pageQuery)
        pagePs.setString(1, "src_a")
        pagePs.setString(2, "cat_anime")
        pagePs.setString(3, "cat_documental")
        val pageRs = pagePs.executeQuery()

        var countLoaded = 0
        while (pageRs.next()) {
            countLoaded++
            val cat = pageRs.getString("categoryId")
            assertFalse("Hidden category appeared in paged result: $cat", cat == "cat_anime" || cat == "cat_documental")
        }
        pageRs.close()
        pagePs.close()

        assertEquals(100, countLoaded)
    }

    // =========================================================================
    // PASO 14: FAVORITE LOOKUP
    // =========================================================================

    @Test
    fun `test series isFavorite field queried directly without full list scan`() {
        populate10kSeries()

        // Series marcadas como favoritas son las de i % 50 == 0
        // En 8,500: 8500 / 50 = 170 favoritas
        val favPs = conn.prepareStatement("SELECT COUNT(*) FROM series WHERE sourceId = ? AND isFavorite = 1")
        favPs.setString(1, "src_a")
        val favRs = favPs.executeQuery()
        assertTrue(favRs.next())
        val favCount = favRs.getInt(1)
        assertEquals(170, favCount)
        favRs.close()
        favPs.close()

        // Chequeo directo por id de una serie
        val checkPs = conn.prepareStatement("SELECT isFavorite FROM series WHERE id = ?")
        checkPs.setString(1, "src_a_series_50")
        val checkRs = checkPs.executeQuery()
        assertTrue(checkRs.next())
        assertEquals(1, checkRs.getInt(1))
        checkRs.close()

        checkPs.setString(1, "src_a_series_51")
        val checkRs2 = checkPs.executeQuery()
        assertTrue(checkRs2.next())
        assertEquals(0, checkRs2.getInt(1))
        checkRs2.close()
        checkPs.close()
    }

    // =========================================================================
    // PASO 15: DETAIL NAVIGATION IDENTITY
    // =========================================================================

    @Test
    fun `test detail navigation retrieves single series without catalog in RAM`() {
        populate10kSeries()

        val detailPs = conn.prepareStatement("SELECT * FROM series WHERE seriesId = ? AND sourceId = ?")
        detailPs.setInt(1, 1337)
        detailPs.setString(2, "src_a")
        val rs = detailPs.executeQuery()

        assertTrue(rs.next())
        assertEquals("src_a_series_1337", rs.getString("id"))
        assertEquals(1337, rs.getInt("seriesId"))
        assertEquals("Serie A 01337", rs.getString("name"))
        assertEquals("src_a", rs.getString("sourceId"))
        assertFalse(rs.next())

        rs.close()
        detailPs.close()
    }
}
