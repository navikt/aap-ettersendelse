package aap.ettersendelse

import aap.ettersendelse.data.Ettersendelse
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.aap.kafka.streams.v2.KafkaStreams
import no.nav.aap.kafka.streams.v2.Streams
import no.nav.aap.ktor.config.loadConfig
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

private val sikkerLogg = LoggerFactory.getLogger("secureLog")

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::server).start(wait = true)
}

private fun Application.server(kafka: Streams = KafkaStreams()) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val config = loadConfig<Config>()

    install(MicrometerMetrics) { registry = prometheus }
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    Thread.currentThread().setUncaughtExceptionHandler { _, e -> sikkerLogg.error("Uhåndtert feil", e) }
    environment.monitor.subscribe(ApplicationStopping) { kafka.close() }

    val tokenxJwkProvider: JwkProvider = JwkProviderBuilder(config.tokenx.jwksUrl)
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()


    install(Authentication){
        jwt("tokenx") {
            realm = "Hent dokumenter"
            verifier(tokenxJwkProvider, config.tokenx.issuer)
            challenge { _, _ -> call.respondText("Ikke tilgang", status = HttpStatusCode.Unauthorized) }
            validate { cred ->
                if (cred.audience.contains(config.tokenx.clientId) && cred.getClaim("pid", String::class) != null) {
                    JWTPrincipal(cred.payload)
                } else {
                    null
                }
            }
        }
    }

    routing {
        actuators(prometheus, kafka)
        authenticate {
            route("/v1/ettersendelse") {
                post {
                    val ettersendelse = call.receive<Ettersendelse>()

                    //kafka.send(ettersendelse) 🐶
                    call.respond(ettersendelse)
                }
                get { call.respondText("Hello, world!") }
            }
        }
    }
}
