import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

object DatabaseService {

    val dataSource: DataSource = initialize()

    private fun initialize(): DataSource {
        val hikari = HikariConfig().apply {
            jdbcUrl = Config.DATABASE_URL
            Config.DATABASE_USER?.let { username = it }
            Config.DATABASE_PASSWORD?.let { password = it }
            maximumPoolSize = 5
            driverClassName = "org.postgresql.Driver"
        }
        val ds = HikariDataSource(hikari)

        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()
            .migrate()

        return ds
    }
}
