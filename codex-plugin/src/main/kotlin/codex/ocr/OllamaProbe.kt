package codex.ocr

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * US-CDX-13-2 — Sonde de disponibilité Ollama pour tests d'intégration.
 *
 * Vérifie qu'un serveur Ollama répond sur le port donné en appelant
 * `/api/tags` (endpoint léger, pas d'inférence). Utilisé par les tests
 * d'intégration via `assumeTrue(isOllamaReady(port))` pour se skipper
 * proprement hors CI/local sans Ollama.
 *
 * La rotation se fait sur la plage 11437-11465 (AGENTS.adoc contrainte
 * globale). Les ports 11434-11436 sont INTERDITS.
 */
object OllamaProbe {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    /**
     * Vérifie qu'Ollama répond sur `host:port`.
     *
     * @return true si l'endpoint `/api/tags` répond 200, false sinon
     */
    fun isReady(host: String = "localhost", port: Int = 11437): Boolean {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://$host:$port/api/tags"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() in 200..299
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Vérifie qu'un modèle spécifique est disponible sur le serveur.
     *
     * @param modelName nom du modèle (ex: "gpt-oss:120b-cloud")
     * @return true si le modèle est listé dans `/api/tags`
     */
    fun isModelAvailable(host: String = "localhost", port: Int = 11437, modelName: String): Boolean {
        if (!isReady(host, port)) return false
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://$host:$port/api/tags"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.body().contains("\"$modelName\"")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Trouve le premier port disponible dans la plage de rotation.
     *
     * @return un port prêt (11437-11465), ou -1 si aucun serveur ne répond
     */
    fun findReadyPort(host: String = "localhost", range: IntRange = 11437..11465): Int {
        for (port in range) {
            if (isReady(host, port)) return port
        }
        return -1
    }
}