package com.platform.demo.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request payload for creating a new order.
 */
public record CreateOrderRequest(
        @NotBlank(message = "customer is required")
        String customer,
        @Positive(message = "amount must be greater than zero")
        double amount
) {
}
