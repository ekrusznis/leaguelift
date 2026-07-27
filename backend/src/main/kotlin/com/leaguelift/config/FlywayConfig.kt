package com.leaguelift.config

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
) {

    @Bean
    fun flyway(): Flyway {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(*locations.split(",").map { it.trim() }.toTypedArray())
            .baselineOnMigrate(baselineOnMigrate)
            .cleanDisabled(cleanDisabled)
            .load()
        flyway.migrate()
        return flyway
    }
}
