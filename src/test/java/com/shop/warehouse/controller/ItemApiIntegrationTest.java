package com.shop.warehouse.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItemApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void createItemReturns201WithLocationAndBody() throws Exception {
        postJson("/api/items", Map.of("name", "T-Shirt", "description", "Basic cotton T-Shirt"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/items/\\d+$")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("T-Shirt"))
                .andExpect(jsonPath("$.description").value("Basic cotton T-Shirt"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void createItemWithBlankNameReturnsValidationError() throws Exception {
        postJson("/api/items", Map.of("name", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/items"))
                .andExpect(jsonPath("$.errors.name").value("Name must not be blank"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void createItemWithTooLongNameReturnsValidationError() throws Exception {
        postJson("/api/items", Map.of("name", "x".repeat(151)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Name must be at most 150 characters"));
    }

    @Test
    void systemControlledFieldsCannotBeSentByClients() throws Exception {
        postJson("/api/items", Map.of("id", 99, "name", "T-Shirt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Unknown field 'id'"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    @Test
    void getAllReturnsItemsOrderedById() throws Exception {
        createItem("T-Shirt");
        createItem("Sneakers");

        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("T-Shirt"))
                .andExpect(jsonPath("$[1].name").value("Sneakers"));
    }

    @Test
    void getByIdReturnsItem() throws Exception {
        long itemId = createItem("T-Shirt");

        mockMvc.perform(get("/api/items/{id}", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId))
                .andExpect(jsonPath("$.name").value("T-Shirt"));
    }

    @Test
    void getUnknownItemReturns404() throws Exception {
        mockMvc.perform(get("/api/items/{id}", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Item 999 not found"))
                .andExpect(jsonPath("$.path").value("/api/items/999"));
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unknownRouteReturns404InStandardFormat() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/does-not-exist"));
    }

    @Test
    void updateReplacesNameAndDescription() throws Exception {
        long itemId = createItem("T-Shirt");

        mockMvc.perform(put("/api/items/{id}", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("name", "T-Shirt Premium", "description", "Combed cotton"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("T-Shirt Premium"))
                .andExpect(jsonPath("$.description").value("Combed cotton"));

        mockMvc.perform(get("/api/items/{id}", itemId))
                .andExpect(jsonPath("$.name").value("T-Shirt Premium"));
    }

    @Test
    void updateUnknownItemReturns404() throws Exception {
        mockMvc.perform(put("/api/items/{id}", 999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteItemWithoutVariantsReturns204() throws Exception {
        long itemId = createItem("T-Shirt");

        mockMvc.perform(delete("/api/items/{id}", itemId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/items/{id}", itemId)).andExpect(status().isNotFound());
    }

    @Test
    void deleteItemWithVariantsIsRejectedAndKeepsData() throws Exception {
        long itemId = createItem("T-Shirt");
        createVariant(itemId, "TS-BLK-M", 5);

        mockMvc.perform(delete("/api/items/{id}", itemId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ITEM_HAS_VARIANTS"));

        mockMvc.perform(get(variantsUrl(itemId)))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void wrongHttpMethodReturns405() throws Exception {
        mockMvc.perform(delete("/api/items"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }
}
