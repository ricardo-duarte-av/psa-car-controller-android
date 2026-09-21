package pt.aguiarvieira.psacc.data.network.dto

import kotlinx.serialization.Serializable

/** `GET /vehicles/<vin>/pictures` — distinct car pictures as paths served by the daemon. */
@Serializable
data class PicturesDto(
    val vin: String? = null,
    val count: Int = 0,
    val pictures: List<String> = emptyList(),
)
