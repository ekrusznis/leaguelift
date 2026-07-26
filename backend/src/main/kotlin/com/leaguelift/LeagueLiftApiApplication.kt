package com.leaguelift

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@SpringBootApplication
@ConfigurationPropertiesScan
class LeagueLiftApiApplication

fun main(args: Array<String>) {
	runApplication<LeagueLiftApiApplication>(*args)
}
