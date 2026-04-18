package com.platform.demo.sales.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record representing a sales order.
 */
public record Order(UUID id, String customer, double amount, Instant createdAt) {
}
