package com.example.ecommerce.service;

import com.example.ecommerce.entity.Order;
import com.example.ecommerce.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Order Service with both optimized and non-optimized methods
 * to demonstrate N+1 query problem and its solution
 */
@Service
@Slf4j
public class OrderService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    /**
     * SLOW METHOD - Causes N+1 Query Problem
     * 
     * This method uses the basic findByCustomerId which doesn't use JOIN FETCH.
     * When we access order.getItems() and order.getShippingInfo() in the controller,
     * it triggers separate queries for each order.
     * 
     * Performance: For 50 orders, this causes approximately 101 database queries:
     * - 1 query to get all orders
     * - 50 queries to get items for each order
     * - 50 queries to get shipping info for each order
     * 
     * @param customerId The customer ID
     * @return List of orders with N+1 query problem
     */
    @Transactional(readOnly = true)
    public List<Order> getCustomerOrdersSlow(Integer customerId) {
        long startTime = System.currentTimeMillis();
        log.info("🐌 SLOW METHOD: Fetching orders for customer {} (N+1 queries)", customerId);
        
        // This will execute: SELECT * FROM orders WHERE customer_id = ?
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        
        // Force lazy loading to happen inside transaction
        // This causes N+1 queries when accessed
        orders.forEach(order -> {
            order.getItems().size();  // Triggers query for items
            if (order.getShippingInfo() != null) {
                order.getShippingInfo().getAddress();  // Triggers query for shipping
            }
        });
        
        long endTime = System.currentTimeMillis();
        log.info("🐌 SLOW METHOD: Completed in {} ms - Executed ~{} queries", 
                 (endTime - startTime), (1 + orders.size() * 2));
        
        return orders;
    }
    
    /**
     * FAST METHOD - Optimized with JOIN FETCH
     * 
     * This method uses JOIN FETCH to load all related entities in a single query.
     * All data is fetched upfront, preventing lazy loading issues.
     * 
     * Performance: For 50 orders, this causes only 1 database query:
     * - 1 query with JOINs to get orders, items, and shipping info
     * 
     * This is 100x faster than the slow method!
     * 
     * @param customerId The customer ID
     * @return List of orders loaded with single query
     */
    @Transactional(readOnly = true)
    public List<Order> getCustomerOrdersFast(Integer customerId) {
        long startTime = System.currentTimeMillis();
        log.info("🚀 FAST METHOD: Fetching orders for customer {} (JOIN FETCH)", customerId);
        
        // This executes a single query with JOINs
        List<Order> orders = orderRepository.findByCustomerIdOptimized(customerId);
        
        long endTime = System.currentTimeMillis();
        log.info("🚀 FAST METHOD: Completed in {} ms - Executed 1 query", 
                 (endTime - startTime));
        
        return orders;
    }
    
    /**
     * ALTERNATIVE FAST METHOD - Using EntityGraph
     * 
     * This demonstrates another way to optimize using @EntityGraph.
     * Similar performance to JOIN FETCH approach.
     * 
     * @param customerId The customer ID
     * @return List of orders loaded with EntityGraph
     */
    @Transactional(readOnly = true)
    public List<Order> getCustomerOrdersWithEntityGraph(Integer customerId) {
        long startTime = System.currentTimeMillis();
        log.info("⚡ ENTITY GRAPH METHOD: Fetching orders for customer {}", customerId);
        
        List<Order> orders = orderRepository.findByCustomerIdWithEntityGraph(customerId);
        
        long endTime = System.currentTimeMillis();
        log.info("⚡ ENTITY GRAPH METHOD: Completed in {} ms", (endTime - startTime));
        
        return orders;
    }
    
    /**
     * Method overloading demonstration
     * Same method name but different parameters
     */
    
    // Slow version - uses customer ID
    @Transactional(readOnly = true)
    public List<Order> getCustomerOrders(Integer customerId) {
        return getCustomerOrdersSlow(customerId);
    }
    
    // Fast version - uses customer ID and optimization flag
    @Transactional(readOnly = true)
    public List<Order> getCustomerOrders(Integer customerId, boolean optimized) {
        if (optimized) {
            return getCustomerOrdersFast(customerId);
        } else {
            return getCustomerOrdersSlow(customerId);
        }
    }
    
    /**
     * Get statistics about queries
     */
    public String getPerformanceInfo() {
        return """
            Performance Comparison:
            
            SLOW (N+1 Problem):
            - Queries: 1 + (N × 2) where N = number of orders
            - For 50 orders: ~101 queries
            - Response time: ~500-1000ms
            
            FAST (JOIN FETCH):
            - Queries: 1 (with JOINs)
            - For 50 orders: 1 query
            - Response time: ~5-10ms
            
            Performance Improvement: ~100x faster!
            """;
    }
}
