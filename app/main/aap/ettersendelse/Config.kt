package aap.ettersendelse

import no.nav.aap.kafka.streams.v2.config.StreamsConfig

data class Config(
    val kafka: StreamsConfig,
)


