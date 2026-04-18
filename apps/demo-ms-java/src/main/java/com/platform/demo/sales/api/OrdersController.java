package com.platform.demo.sales.api;

import com.platform.authz.sdk.annotation.HasPermission;
import com.platform.demo.sales.dto.CreateOrderRequest;
import com.platform.demo.sales.dto.Order;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Demo orders controller protected by AuthZ SDK permissions.
 */
@RestController
@RequestMapping("/orders")
public class OrdersController {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrdersController.class);

    private final List<Order> orders = new CopyOnWriteArrayList<>();

    @GetMapping
    @HasPermission("vendas.orders.view")
    public List<Order> list() {
        return List.copyOf(orders);
    }

    @PostMapping
    @HasPermission("vendas.orders.create")
    @ResponseStatus(HttpStatus.CREATED)
    public Order create(@Valid @RequestBody CreateOrderRequest request) {
        var order = new Order(UUID.randomUUID(), request.customer(), request.amount(), Instant.now());
        orders.add(order);
        LOGGER.info("order.created orderId={}", order.id());
        return order;
    }

    @DeleteMapping("/{id}")
    @HasPermission("vendas.orders.cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable("id") UUID id) {
        boolean removed = orders.removeIf(order -> order.id().equals(id));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        LOGGER.info("order.cancelled orderId={}", id);
    }
}
