package com.shop.warehouse.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full application context over H2 with the real Flyway schema. Tests are deliberately not wrapped in
 * a rolled-back transaction, so commits, constraints and concurrency behave as they do in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM variants");
        jdbcTemplate.update("DELETE FROM items");
    }

    protected ResultActions postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected long createItem(String name) throws Exception {
        String json = postJson("/api/items", Map.of("name", name))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readId(json);
    }

    protected long createVariant(long itemId, String sku, int stock) throws Exception {
        Map<String, Object> body = Map.of("sku", sku, "name", sku, "price", 149000, "stockQuantity", stock);
        String json = postJson(variantsUrl(itemId), body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readId(json);
    }

    protected static String variantsUrl(long itemId) {
        return "/api/items/" + itemId + "/variants";
    }

    protected static String variantUrl(long itemId, long variantId) {
        return variantsUrl(itemId) + "/" + variantId;
    }

    private long readId(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("id").asLong();
    }
}
