package com.lelouch.feature.mobile

import android.content.Context
import com.lelouch.core.model.PortalFeedData
import com.lelouch.core.model.PortalHomeCacheManager

/**
 * Delegado ligero hacia [PortalHomeCacheManager] compartido (prefijo "mobile").
 */
object MobileHomeCacheManager {
    suspend fun load(context: Context, sourceId: String): PortalFeedData? =
        PortalHomeCacheManager.load(context, "mobile", sourceId)

    suspend fun save(context: Context, sourceId: String, feed: PortalFeedData) =
        PortalHomeCacheManager.save(context, "mobile", sourceId, feed)
}
