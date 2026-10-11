package com.metroboard.ime.prediction

/**
 * Small offline language packs used by the v0.2 input-quality refactor.
 *
 * The goal here is architectural separation: suggestions and Word Flow must query
 * the active language instead of a shared multilingual word bucket. These lists
 * are intentionally modest and can later be replaced by licensed frequency
 * dictionaries without changing the callers.
 */
data class LanguagePack(
    val code: String,
    val words: List<String>,
    val corrections: Map<String, String>,
    val bigrams: Map<String, List<String>>,
)

object LanguagePacks {
    private val spanish = LanguagePack(
        code = "es",
        words = listOf(
            "ahora", "amigo", "amiga", "amor", "antes", "aquí", "bien", "buenas", "bueno",
            "casa", "casi", "comer", "como", "contigo", "cuando", "día", "días", "donde",
            "entonces", "estamos", "estar", "gracias", "hacer", "hasta", "hola", "hombre",
            "hora", "hoy", "juntos", "luego", "mañana", "mejor", "mensaje", "muchas",
            "mundo", "noche", "nosotros", "nuevo", "para", "pero", "podemos", "porque",
            "pronto", "puedo", "quiero", "saber", "saludos", "siempre", "también", "tarde",
            "teclado", "tenemos", "tiempo", "tienes", "todos", "vamos", "verdad", "vida",
            "algo", "año", "años", "aquel", "así", "aunque", "buena", "cada", "claro",
            "cosa", "cosas", "creo", "cual", "cuál", "dar", "decir", "después", "dios",
            "dónde", "ella", "ellos", "esto", "estoy", "favor", "gente", "gran", "haber",
            "hablar", "hace", "hacia", "hijo", "igual", "lado", "llegar", "lugar", "mano",
            "más", "mayor", "medio", "mientras", "mismo", "momento", "mujer", "nada",
            "nadie", "niño", "niña", "nunca", "otro", "otra", "padre", "parece", "parte",
            "pasar", "poco", "poder", "poner", "primer", "primero", "pueblo", "quien",
            "quién", "razón", "salir", "seguir", "segundo", "seguro", "señor", "sentir",
            "ser", "si", "sí", "sido", "solo", "somos", "soy", "su", "sus", "tanto",
            "tener", "tipo", "toda", "tomar", "trabajo", "tres", "uno", "unos", "va",
            "veces", "ver", "vez", "visto", "volver", "ya", "deber", "ir", "quedar",
            "creer", "llevar", "dejar", "encontrar", "llamar", "venir", "pensar", "vivir"
        ),
        corrections = mapOf(
            "tambien" to "también", "mas" to "más", "dia" to "día", "dias" to "días",
            "esta" to "está", "estas" to "estás", "estan" to "están", "aqui" to "aquí",
            "alli" to "allí", "facil" to "fácil", "dificil" to "difícil",
            "quiza" to "quizá", "quizas" to "quizás", "adios" to "adiós",
            "corazon" to "corazón", "cancion" to "canción", "accion" to "acción",
            "entonses" to "entonces"
        ),
        bigrams = mapOf(
            "gracias" to listOf("por", "a", "todos"),
            "muchas" to listOf("gracias"),
            "buenos" to listOf("días"),
            "buenas" to listOf("tardes", "noches"),
            "cómo" to listOf("estás", "está", "te va"),
            "como" to listOf("estás", "está", "siempre"),
            "por" to listOf("favor", "ejemplo", "eso"),
            "de" to listOf("la", "el", "los"),
            "en" to listOf("el", "la", "casa"),
            "te" to listOf("quiero", "veo"),
            "vamos" to listOf("a", "bien"),
            "hasta" to listOf("luego", "mañana", "pronto"),
            "hola" to listOf("cómo", "amigo", "buenos")
        )
    )

    private val english = LanguagePack(
        code = "en",
        words = listOf(
            "about", "after", "again", "all", "also", "and", "any", "are", "back", "because",
            "been", "before", "can", "come", "could", "day", "even", "first", "for", "from",
            "get", "give", "good", "have", "here", "how", "into", "just", "know", "like",
            "look", "make", "most", "new", "not", "now", "one", "only", "other", "our",
            "out", "over", "people", "say", "see", "she", "some", "take", "than", "that",
            "the", "their", "them", "then", "there", "these", "they", "think", "this",
            "time", "two", "use", "want", "way", "well", "what", "when", "which", "who",
            "will", "with", "work", "would", "year", "you", "your", "hello", "keyboard",
            "message", "morning", "night", "friend", "thanks", "thank", "please"
        ),
        corrections = mapOf(
            "dont" to "don't", "cant" to "can't", "wont" to "won't", "im" to "I'm",
            "ive" to "I've", "id" to "I'd", "ill" to "I'll", "youre" to "you're",
            "theyre" to "they're", "weve" to "we've", "youve" to "you've",
            "didnt" to "didn't", "isnt" to "isn't", "arent" to "aren't",
            "wasnt" to "wasn't", "werent" to "weren't", "couldnt" to "couldn't",
            "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "hasnt" to "hasn't",
            "havent" to "haven't", "teh" to "the", "adn" to "and", "taht" to "that",
            "waht" to "what", "wiht" to "with", "tou" to "you", "thsi" to "this",
            "peopel" to "people", "becuase" to "because"
        ),
        bigrams = mapOf(
            "how" to listOf("are", "is", "about"),
            "are" to listOf("you", "we", "they"),
            "thank" to listOf("you"),
            "thanks" to listOf("for", "again"),
            "good" to listOf("morning", "night", "afternoon"),
            "see" to listOf("you"),
            "let" to listOf("me", "us"),
            "what" to listOf("is", "are", "about"),
            "where" to listOf("are", "is"),
            "take" to listOf("care"),
            "you" to listOf("are", "can", "know")
        )
    )

    private val packs = mapOf(spanish.code to spanish, english.code to english)

    fun forCode(code: String): LanguagePack = packs[code.lowercase()] ?: spanish
}
