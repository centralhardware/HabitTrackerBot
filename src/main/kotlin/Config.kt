object Config {

    val DATABASE_URL: String = System.getenv("DATABASE_URL")
        ?: error("DATABASE_URL env var is required (e.g. jdbc:postgresql://localhost:5432/habits)")

    val DATABASE_USER: String? = System.getenv("DATABASE_USER")
    val DATABASE_PASSWORD: String? = System.getenv("DATABASE_PASSWORD")
}
