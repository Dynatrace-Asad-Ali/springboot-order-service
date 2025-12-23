package com.example.ecommerce.controller;

import com.example.ecommerce.entity.Order;
import com.example.ecommerce.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller exposing both optimized and non-optimized endpoints
 */
@RestController
@RequestMapping("/api/orders")
@Slf4j
public class OrderController {
    
    @Autowired
    private OrderService orderService;
    
    /**
     * SLOW ENDPOINT - Demonstrates N+1 Query Problem
     * 
     * URL: GET /api/orders/customer/{customerId}/slow
     * Example: http://localhost:8080/api/orders/customer/1/slow
     * 
     * This endpoint will cause ~101 database queries for customer with 50 orders
     */
    @GetMapping("/customer/{customerId}/slow")
    public ResponseEntity<Map<String, Object>> getCustomerOrdersSlow(@PathVariable Integer customerId) {
        log.info("📥 SLOW ENDPOINT called for customer {}", customerId);
        
        long startTime = System.currentTimeMillis();
        List<Order> orders = orderService.getCustomerOrdersSlow(customerId);
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("customerId", customerId);
        response.put("orderCount", orders.size());
        response.put("orders", orders);
        response.put("method", "SLOW (N+1 queries)");
        response.put("estimatedQueries", 1 + (orders.size() * 2));
        response.put("durationMs", duration);
        
        log.info("📤 SLOW ENDPOINT completed in {} ms", duration);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * FAST ENDPOINT - Optimized with JOIN FETCH
     * 
     * URL: GET /api/orders/customer/{customerId}/fast
     * Example: http://localhost:8080/api/orders/customer/1/fast
     * 
     * This endpoint will execute only 1 database query regardless of number of orders
     */
    @GetMapping("/customer/{customerId}/fast")
    public ResponseEntity<Map<String, Object>> getCustomerOrdersFast(@PathVariable Integer customerId) {
        log.info("📥 FAST ENDPOINT called for customer {}", customerId);
        
        long startTime = System.currentTimeMillis();
        List<Order> orders = orderService.getCustomerOrdersFast(customerId);
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("customerId", customerId);
        response.put("orderCount", orders.size());
        response.put("orders", orders);
        response.put("method", "FAST (JOIN FETCH)");
        response.put("estimatedQueries", 1);
        response.put("durationMs", duration);
        
        log.info("📤 FAST ENDPOINT completed in {} ms", duration);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * ENTITY GRAPH ENDPOINT - Alternative optimization
     * 
     * URL: GET /api/orders/customer/{customerId}/entitygraph
     * Example: http://localhost:8080/api/orders/customer/1/entitygraph
     */
    @GetMapping("/customer/{customerId}/entitygraph")
    public ResponseEntity<Map<String, Object>> getCustomerOrdersEntityGraph(@PathVariable Integer customerId) {
        log.info("📥 ENTITY GRAPH ENDPOINT called for customer {}", customerId);
        
        long startTime = System.currentTimeMillis();
        List<Order> orders = orderService.getCustomerOrdersWithEntityGraph(customerId);
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("customerId", customerId);
        response.put("orderCount", orders.size());
        response.put("orders", orders);
        response.put("method", "ENTITY GRAPH");
        response.put("estimatedQueries", 1);
        response.put("durationMs", duration);
        
        log.info("📤 ENTITY GRAPH ENDPOINT completed in {} ms", duration);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * DYNAMIC ENDPOINT - Choose optimization with query parameter
     * 
     * URL: GET /api/orders/customer/{customerId}?optimized=true
     * Examples:
     *   Slow: http://localhost:8080/api/orders/customer/1?optimized=false
     *   Fast: http://localhost:8080/api/orders/customer/1?optimized=true
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<Map<String, Object>> getCustomerOrders(
            @PathVariable Integer customerId,
            @RequestParam(defaultValue = "false") boolean optimized) {
        
        log.info("📥 DYNAMIC ENDPOINT called for customer {} (optimized={})", customerId, optimized);
        
        long startTime = System.currentTimeMillis();
        List<Order> orders = orderService.getCustomerOrders(customerId, optimized);
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("customerId", customerId);
        response.put("orderCount", orders.size());
        response.put("orders", orders);
        response.put("method", optimized ? "FAST" : "SLOW");
        response.put("estimatedQueries", optimized ? 1 : (1 + orders.size() * 2));
        response.put("durationMs", duration);
        
        log.info("📤 DYNAMIC ENDPOINT completed in {} ms", duration);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * HEALTH CHECK ENDPOINT
     * 
     * URL: GET /api/orders/health
     * Returns application health and performance information
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "Order Service");
        health.put("endpoints", Map.of(
            "slow", "/api/orders/customer/{id}/slow",
            "fast", "/api/orders/customer/{id}/fast",
            "entitygraph", "/api/orders/customer/{id}/entitygraph",
            "dynamic", "/api/orders/customer/{id}?optimized=true/false"
        ));
        health.put("performanceInfo", orderService.getPerformanceInfo());
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * COMPARISON ENDPOINT
     * Runs both slow and fast methods and returns comparison
     * 
     * URL: GET /api/orders/customer/{customerId}/compare
     */
    @GetMapping("/customer/{customerId}/compare")
    public ResponseEntity<Map<String, Object>> comparePerformance(@PathVariable Integer customerId) {
        log.info("📊 COMPARISON ENDPOINT called for customer {}", customerId);
        
        // Run slow method
        long slowStart = System.currentTimeMillis();
        List<Order> slowOrders = orderService.getCustomerOrdersSlow(customerId);
        long slowDuration = System.currentTimeMillis() - slowStart;
        
        // Run fast method
        long fastStart = System.currentTimeMillis();
        List<Order> fastOrders = orderService.getCustomerOrdersFast(customerId);
        long fastDuration = System.currentTimeMillis() - fastStart;
        
        Map<String, Object> comparison = new HashMap<>();
        comparison.put("customerId", customerId);
        comparison.put("orderCount", slowOrders.size());
        comparison.put("slowMethod", Map.of(
            "durationMs", slowDuration,
            "estimatedQueries", 1 + (slowOrders.size() * 2),
            "method", "N+1 queries"
        ));
        comparison.put("fastMethod", Map.of(
            "durationMs", fastDuration,
            "estimatedQueries", 1,
            "method", "JOIN FETCH"
        ));
        comparison.put("improvement", Map.of(
            "timesF aster", slowDuration > 0 ? (double) slowDuration / fastDuration : 0,
            "timeSavedMs", slowDuration - fastDuration,
            "queriesReduced", (slowOrders.size() * 2)
        ));
        
        log.info("📊 COMPARISON: Slow={}ms, Fast={}ms, Improvement={}x", 
                 slowDuration, fastDuration, 
                 slowDuration > 0 ? (double) slowDuration / fastDuration : 0);
        
        return ResponseEntity.ok(comparison);
    }
}
