package com.example.ecommerce.repository;

import com.example.ecommerce.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    
    /**
     * NON-OPTIMIZED: This will cause N+1 queries
     * Spring will:
     * 1. Execute: SELECT * FROM orders WHERE customer_id = ?
     * 2. For each order, execute: SELECT * FROM order_items WHERE order_id = ?
     * 3. For each order, execute: SELECT * FROM shipping WHERE order_id = ?
     * Total: 1 + (N items queries) + (N shipping queries) = 1 + 2N queries
     */
    List<Order> findByCustomerId(Integer customerId);
    
    /**
     * OPTIMIZED: Using JOIN FETCH to load everything in one query
     * This uses LEFT JOIN FETCH to eagerly load related entities
     * Total: 1 query with JOINs
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.items " +
           "LEFT JOIN FETCH o.shippingInfo " +
           "WHERE o.customerId = :customerId")
    List<Order> findByCustomerIdOptimized(@Param("customerId") Integer customerId);
    
    /**
     * ALTERNATIVE OPTIMIZED: Using EntityGraph
     * This is another way to solve N+1 problem using @EntityGraph annotation
     */
    @Query("SELECT o FROM Order o WHERE o.customerId = :customerId")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"items", "shippingInfo"})
    List<Order> findByCustomerIdWithEntityGraph(@Param("customerId") Integer customerId);
}
