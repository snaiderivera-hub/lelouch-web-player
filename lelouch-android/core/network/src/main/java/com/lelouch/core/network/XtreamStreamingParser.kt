package com.lelouch.core.network

import android.util.JsonReader
import android.util.JsonToken
import com.lelouch.core.database.entity.ChannelEntity
import com.lelouch.core.database.entity.MovieEntity
import com.lelouch.core.database.entity.SeriesEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Parser Incremental de JSON para catálogos masivos Xtream (IPTV-by-Killua architecture).
 * Lee el stream HTTP en trozos (chunks) y emite bloques hacia la base de datos Room, 
 * evitando agotar la memoria RAM con String/JsonTrees inmensos.
 */
object XtreamStreamingParser {

    suspend fun parseLiveStreams(
        inputStream: InputStream,
        sourceId: String,
        batchSize: Int = 500,
        onBatchParsed: suspend (List<ChannelEntity>) -> Unit
    ) = withContext(Dispatchers.IO) {
        val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
        val batch = mutableListOf<ChannelEntity>()

        try {
            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                reader.beginArray()
                while (reader.hasNext()) {
                    val entity = parseSingleChannel(reader, sourceId)
                    if (entity != null) {
                        batch.add(entity)
                        if (batch.size >= batchSize) {
                            onBatchParsed(batch.toList())
                            batch.clear()
                        }
                    }
                }
                reader.endArray()
            }
            if (batch.isNotEmpty()) {
                onBatchParsed(batch.toList())
                batch.clear()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            reader.close()
            inputStream.close()
        }
    }

    suspend fun parseVodStreams(
        inputStream: InputStream,
        sourceId: String,
        batchSize: Int = 500,
        onBatchParsed: suspend (List<MovieEntity>) -> Unit
    ) = withContext(Dispatchers.IO) {
        val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
        val batch = mutableListOf<MovieEntity>()

        try {
            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                reader.beginArray()
                while (reader.hasNext()) {
                    val entity = parseSingleMovie(reader, sourceId)
                    if (entity != null) {
                        batch.add(entity)
                        if (batch.size >= batchSize) {
                            onBatchParsed(batch.toList())
                            batch.clear()
                        }
                    }
                }
                reader.endArray()
            }
            if (batch.isNotEmpty()) {
                onBatchParsed(batch.toList())
                batch.clear()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            reader.close()
            inputStream.close()
        }
    }

    suspend fun parseSeriesStreams(
        inputStream: InputStream,
        sourceId: String,
        batchSize: Int = 500,
        onBatchParsed: suspend (List<SeriesEntity>) -> Unit
    ) = withContext(Dispatchers.IO) {
        val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
        val batch = mutableListOf<SeriesEntity>()

        try {
            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                reader.beginArray()
                while (reader.hasNext()) {
                    val entity = parseSingleSeries(reader, sourceId)
                    if (entity != null) {
                        batch.add(entity)
                        if (batch.size >= batchSize) {
                            onBatchParsed(batch.toList())
                            batch.clear()
                        }
                    }
                }
                reader.endArray()
            }
            if (batch.isNotEmpty()) {
                onBatchParsed(batch.toList())
                batch.clear()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            reader.close()
            inputStream.close()
        }
    }

    private fun parseSingleChannel(reader: JsonReader, sourceId: String): ChannelEntity? {
        var streamId = 0
        var name = ""
        var num = 0
        var streamType = "live"
        var streamIcon = ""
        var categoryId = ""
        var epgChannelId = ""
        
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "stream_id" -> streamId = if (reader.peek() == JsonToken.NUMBER) reader.nextInt() else { reader.skipValue(); 0 }
                "name" -> name = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); "" }
                "num" -> num = if (reader.peek() == JsonToken.NUMBER) reader.nextInt() else if (reader.peek() == JsonToken.STRING) reader.nextString().toIntOrNull() ?: 0 else { reader.skipValue(); 0 }
                "stream_type" -> streamType = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); "" }
                "stream_icon" -> streamIcon = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); "" }
                "category_id" -> categoryId = if (reader.peek() == JsonToken.STRING) reader.nextString().trim() else if (reader.peek() == JsonToken.NUMBER) reader.nextInt().toString() else { reader.skipValue(); "" }
                "epg_channel_id" -> epgChannelId = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); "" }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        
        if (streamId == 0 || name.isBlank()) return null
        
        val isAdultContent = name.contains("+18", ignoreCase = true) || name.contains("XXX", ignoreCase = true)

        return ChannelEntity(
            id = "${sourceId}_live_$streamId",
            streamId = streamId,
            num = num,
            name = name,
            streamType = streamType,
            streamIcon = streamIcon.takeIf { it.isNotBlank() },
            categoryId = categoryId,
            epgChannelId = epgChannelId.takeIf { it.isNotBlank() },
            isAdult = isAdultContent,
            isFavorite = false,
            sourceId = sourceId,
            containerExtension = "ts"
        )
    }

    private fun parseSingleMovie(reader: JsonReader, sourceId: String): MovieEntity? {
        var streamId = 0
        var name = ""
        var num = 0
        var streamIcon = ""
        var categoryId = ""
        var containerExtension = "mp4"
        var rating = 0.0
        var added = ""
        
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "stream_id" -> streamId = if (reader.peek() == JsonToken.NUMBER) reader.nextInt() else { reader.skipValue(); 0 }
                "name" -> name = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); "" }
                "num" -> num = if (reader.peek() == JsonToken.NUMBER) reader.nextInt() else if (reader.peek() == JsonToken.STRING) reader.nextString().toIntOrNull() ?: 0 else { reader.skipValue(); 0 }
                "stream_icon" -> streamIcon = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); "" }
                "category_id" -> categoryId = if (reader.peek() == JsonToken.STRING) reader.nextString().trim() else if (reader.peek() == JsonToken.NUMBER) reader.nextInt().toString() else { reader.skipValue(); "" }
                "container_extension" -> containerExtension = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); "mp4" }
                "rating" -> rating = if (reader.peek() == JsonToken.NUMBER) reader.nextDouble() else if (reader.peek() == JsonToken.STRING) reader.nextString().toDoubleOrNull() ?: 0.0 else { reader.skipValue(); 0.0 }
                "added" -> added = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); "" }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        
        if (streamId == 0 || name.isBlank()) return null
        
        return MovieEntity(
            id = "${sourceId}_vod_$streamId",
            streamId = streamId,
            num = num,
            name = name,
            title = name,
            streamIcon = streamIcon.takeIf { it.isNotBlank() },
            categoryId = categoryId,
            containerExtension = containerExtension,
            rating = rating,
            rating5based = if (rating > 5) rating / 2 else rating,
            added = added.takeIf { it.isNotBlank() },
            sourceId = sourceId,
            isFavorite = false
        )
    }

    private fun parseSingleSeries(reader: JsonReader, sourceId: String): SeriesEntity? {
        var seriesId = 0
        var name = ""
        var num = 0
        var cover = ""
        var categoryId = ""
        var rating = 0.0
        
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "series_id" -> seriesId = if (reader.peek() == JsonToken.NUMBER) reader.nextInt() else { reader.skipValue(); 0 }
                "name" -> name = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); "" }
                "num" -> num = if (reader.peek() == JsonToken.NUMBER) reader.nextInt() else if (reader.peek() == JsonToken.STRING) reader.nextString().toIntOrNull() ?: 0 else { reader.skipValue(); 0 }
                "cover" -> cover = if (reader.peek() == JsonToken.STRING) reader.nextString() else { reader.skipValue(); "" }
                "category_id" -> categoryId = if (reader.peek() == JsonToken.STRING) reader.nextString().trim() else if (reader.peek() == JsonToken.NUMBER) reader.nextInt().toString() else { reader.skipValue(); "" }
                "rating" -> rating = if (reader.peek() == JsonToken.NUMBER) reader.nextDouble() else if (reader.peek() == JsonToken.STRING) reader.nextString().toDoubleOrNull() ?: 0.0 else { reader.skipValue(); 0.0 }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        
        if (seriesId == 0 || name.isBlank()) return null
        
        return SeriesEntity(
            id = "${sourceId}_series_$seriesId",
            seriesId = seriesId,
            num = num,
            name = name,
            title = name,
            cover = cover.takeIf { it.isNotBlank() },
            categoryId = categoryId,
            rating = rating,
            rating5based = if (rating > 5) rating / 2 else rating,
            sourceId = sourceId,
            isFavorite = false
        )
    }
}
