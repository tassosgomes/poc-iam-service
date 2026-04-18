package com.platform.demo.sales.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.platform.demo.sales.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web slice tests for {@link OrdersController}.
 *
 * <p>The AuthZ SDK auto-configuration is disabled in the test profile, so this suite validates the
 * controller contract and the JWT resource-server security behavior with mock authentication.
 */
@WebMvcTest(controllers = OrdersController.class)
@Import(SecurityConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrdersControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("GET /orders")
    class ListOrders {

        @Test
        @DisplayName("returns 401 when no JWT is provided")
        void listOrders_NoJwt_Returns401() throws Exception {
            mockMvc.perform(get("/orders"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("returns 200 with empty list when authenticated")
        void listOrders_WithJwt_Returns200() throws Exception {
            mockMvc.perform(get("/orders").with(jwt()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("POST /orders")
    class CreateOrder {

        @Test
        @DisplayName("returns 401 when no JWT is provided")
        void createOrder_NoJwt_Returns401() throws Exception {
            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"customer":"Acme Corp","amount":100.50}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("returns 201 and created order when authenticated")
        void createOrder_WithJwt_Returns201() throws Exception {
            mockMvc.perform(post("/orders")
                            .with(jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"customer":"Acme Corp","amount":100.50}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.customer").value("Acme Corp"))
                    .andExpect(jsonPath("$.amount").value(100.50))
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        @DisplayName("returns 400 when payload is invalid")
        void createOrder_WithInvalidPayload_Returns400() throws Exception {
            mockMvc.perform(post("/orders")
                            .with(jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"customer":" ","amount":0}
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /orders/{id}")
    class CancelOrder {

        @Test
        @DisplayName("returns 401 when no JWT is provided")
        void cancelOrder_NoJwt_Returns401() throws Exception {
            mockMvc.perform(delete("/orders/00000000-0000-0000-0000-000000000001"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("returns 404 when order does not exist")
        void cancelOrder_NotFound_Returns404() throws Exception {
            mockMvc.perform(delete("/orders/00000000-0000-0000-0000-000000000001")
                            .with(jwt()))
                    .andExpect(status().isNotFound());
        }
    }
}
