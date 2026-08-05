package com.rally26.config

import org.flywaydb.core.Flyway
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class FlywayConfig(
    private val dataSource: DataSource,
    @Value("\${spring.flyway.locations:classpath:db/migration}") private val locations: String,
    @Value("\${spring.flyway.baseline-on-migrate:false}") private val baselineOnMigrate: Boolean,
    @Value("\${spring.flyway.clean-disabled:true}") private val cleanDisabled: Boolean,
    // Only ever true for the `local` profile, which additionally loads
    // classpath:db/seed (see application-local.yml). Those dev-only fixture
    // migrations are numbered from V9000 specifically to stay clear of the real
    // migration sequence, but that means a fresh real migration (e.g. V7) can
    // land "behind" an already-applied V9000 on a developer's existing local
    // database — Flyway's default validation rejects that as out-of-order.
    // staging/prod never load db/seed, so they never hit this gap and always
    // apply strictly in order regardless of this flag.
    @Value("\${spring.flyway.out-of-order:false}") private val outOfOrder: Boolean,
) {
    @Bean
    fun flyway(): Flyway {
        val flyway =
            Flyway
                .configure()
                .dataSource(dataSource)
                .locations(*locations.split(",").map { it.trim() }.toTypedArray())
                .baselineOnMigrate(baselineOnMigrate)
                .cleanDisabled(cleanDisabled)
                .outOfOrder(outOfOrder)
                .load()
        flyway.migrate()
        return flyway
    }
}
