package aap.ettersendelse

import no.nav.aap.kafka.streams.v2.config.StreamsConfig
import java.net.URL

data class Config(
    val kafka: StreamsConfig,
    val tokenx: TokenXConfig,
)

data class TokenXConfig(
    val clientId: String,
    val privateKey: String,
    val tokenEndpoint: String,
    val jwksUrl: URL,
    val issuer: String,
)
