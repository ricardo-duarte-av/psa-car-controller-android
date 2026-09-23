package pt.aguiarvieira.psacc.data.network.dto

import kotlinx.serialization.Serializable

/** `GET /version` of the forked daemon (0.1.24 on): the release it runs. */
@Serializable
data class VersionDto(
    val version: String? = null,
)
