object Config {

    val DATABASE_URL: String = System.getenv("DATABASE_URL")
        ?: error("DATABASE_URL env var is required (e.g. jdbc:postgresql://localhost:5432/habits?user=foo&password=bar)")
}
