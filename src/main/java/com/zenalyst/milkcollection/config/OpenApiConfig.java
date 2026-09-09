package com.zenalyst.milkcollection.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI milkCollectionOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Milk Collection API")
                .version("v1")
                .description("""
                        Planning and twice-a-day operations backend for a dairy milk collection network.

                        Two sides of the system:
                        * **Planning** - master data, routes and immutable route versions, greedy route optimisation.
                        * **Operations** - collection runs, stop arrivals, milk collection, tanker tracking, farmer ETA.

                        Business errors return a consistent payload with a stable machine-readable `code`
                        (for example `TANKER_CAPACITY_EXCEEDED`, `MILK_HOLDING_TIME_EXCEEDED`, `INVALID_RUN_STATE`).
                        """)
                .license(new License().name("Take-home assignment")));
    }
}
