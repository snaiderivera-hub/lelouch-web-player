package com.lelouch.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamAuthResponse(
    @SerialName("user_info")
    val userInfo: XtreamUserInfoDto? = null,
    @SerialName("server_info")
    val serverInfo: XtreamServerInfoDto? = null
)

@Serializable
data class XtreamUserInfoDto(
    val username: String = "",
    val status: String = "Active",
    @SerialName("exp_date")
    val expDate: String? = null,
    @SerialName("is_trial")
    val isTrial: String = "0",
    @SerialName("active_cons")
    val activeCons: String = "0",
    @SerialName("max_connections")
    val maxConnections: String = "1",
    @SerialName("allowed_output_formats")
    val allowedOutputFormats: List<String> = emptyList()
)

@Serializable
data class XtreamServerInfoDto(
    val url: String = "",
    val port: String = "",
    val https_port: String? = null,
    val server_protocol: String = "http",
    val timezone: String? = null,
    val timestamp_now: Long = 0
)
