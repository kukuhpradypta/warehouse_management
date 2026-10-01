package com.shop.warehouse.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Shop Warehouse Management API",
        version = "1.0.0",
        description = "Items, variants (SKU, price in IDR, stock) and stock operations"))
public class OpenApiConfig {
}
