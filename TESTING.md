# Testing Guide - Spring Boot Order Service

## Quick Start

```bash
# Start the application
./run.sh

# Or with Docker
docker-compose up -d
```

---

## Manual Testing

### 1. Health Check

```bash
curl http://localhost:8080/api/orders/health | jq
```

**Expected Response**:
```json
{
  "status": "UP",
  "service": "Order Service",
  "endpoints": {
    "slow": "/api/orders/customer/{id}/slow",
    "fast": "/api/orders/customer/{id}/fast",
    "entitygraph": "/api/orders/customer/{id}/entitygraph",
    "dynamic": "/api/orders/customer/{id}?optimized=true/false"
  }
}
```

---

### 2. Test Slow Endpoint (N+1 Problem)

```bash
time curl http://localhost:8080/api/orders/customer/1/slow | jq '.method, .durationMs, .estimatedQueries'
```

**Expected**:
- Response time: 500-1000ms
- Estimated queries: 101

**What happens**:
1. 1 query to get all orders
2. 50 queries to get order items
3. 50 queries to get shipping info

**Watch the logs**:
```bash
docker-compose logs -f order-service | grep "SELECT"
```

You'll see 101 separate SQL queries!

---

### 3. Test Fast Endpoint (Optimized)

```bash
time curl http://localhost:8080/api/orders/customer/1/fast | jq '.method, .durationMs, .estimatedQueries'
```

**Expected**:
- Response time: 5-10ms
- Estimated queries: 1

**What happens**:
- 1 query with JOINs to get everything

**Watch the logs**:
```bash
docker-compose logs -f order-service | grep "SELECT"
```

You'll see only 1 SQL query with JOINs!

---

### 4. Side-by-Side Comparison

```bash
curl http://localhost:8080/api/orders/customer/1/compare | jq
```

**Example Output**:
```json
{
  "customerId": 1,
  "orderCount": 50,
  "slowMethod": {
    "durationMs": 856,
    "estimatedQueries": 101,
    "method": "N+1 queries"
  },
  "fastMethod": {
    "durationMs": 8,
    "estimatedQueries": 1,
    "method": "JOIN FETCH"
  },
  "improvement": {
    "timesFaster": 107.0,
    "timeSavedMs": 848,
    "queriesReduced": 100
  }
}
```

---

## Load Testing

### Basic Load Test

```bash
mvn exec:java -Dexec.mainClass="com.example.ecommerce.loadtest.OrderServiceLoadGenerator"
```

**Default Configuration**:
- Threads: 10
- Duration: 300 seconds (5 minutes)
- Slow requests: 70%
- Fast requests: 30%

---

### Custom Load Tests

#### Light Load (Baseline)
```bash
mvn exec:java -Dexec.mainClass="com.example.ecommerce.loadtest.OrderServiceLoadGenerator" \
  -Dexec.args="-t 5 -d 60 -s 70"
```

- 5 threads
- 60 seconds
- 70% slow

**Expected Results**:
- ~240 total requests
- ~168 slow (N+1)
- ~72 fast
- ~17,000 total database queries

---

#### Medium Load
```bash
mvn exec:java -Dexec.mainClass="com.example.ecommerce.loadtest.OrderServiceLoadGenerator" \
  -Dexec.args="-t 10 -d 300 -s 70"
```

- 10 threads
- 300 seconds
- 70% slow

**Expected Results**:
- ~14,000 total requests
- ~9,800 slow (N+1)
- ~4,200 fast
- ~990,000 total database queries

---

#### Stress Test
```bash
mvn exec:java -Dexec.mainClass="com.example.ecommerce.loadtest.OrderServiceLoadGenerator" \
  -Dexec.args="-t 50 -d 300 -s 80"
```

- 50 threads
- 300 seconds
- 80% slow

**Expected Results**:
- High response times
- Possible errors
- Database connection pool exhaustion

---

#### All Slow (Worst Case)
```bash
mvn exec:java -Dexec.mainClass="com.example.ecommerce.loadtest.OrderServiceLoadGenerator" \
  -Dexec.args="-t 20 -d 300 -s 100"
```

- 20 threads
- 300 seconds
- 100% slow (only N+1 queries)

**This will show maximum database load!**

---

#### All Fast (Best Case)
```bash
mvn exec:java -Dexec.mainClass="com.example.ecommerce.loadtest.OrderServiceLoadGenerator" \
  -Dexec.args="-t 20 -d 300 -s 0"
```

- 20 threads
- 300 seconds
- 100% fast (only optimized queries)

**This shows optimal performance!**

---

## Monitoring Database Queries

### Enable Query Logging

Already enabled in `application.properties`:
```properties
spring.jpa.show-sql=true
logging.level.org.hibernate.SQL=DEBUG
spring.jpa.properties.hibernate.generate_statistics=true
```

### Watch Queries in Real-Time

```bash
# Docker
docker-compose logs -f order-service | grep "SELECT"

# Or filter for statistics
docker-compose logs -f order-service | grep "queries executed"
```

### Check PostgreSQL Stats

```bash
# Connect to database
docker exec -it ecommerce-postgres psql -U admin -d ecommerce

# Check query count
SELECT 
    schemaname,
    relname,
    seq_scan,
    seq_tup_read,
    idx_scan,
    idx_tup_fetch
FROM pg_stat_user_tables
WHERE schemaname = 'public'
ORDER BY seq_tup_read DESC;
```

---

## Performance Testing Scenarios

### Scenario 1: Compare Response Times

```bash
# Slow endpoint (10 requests)
for i in {1..10}; do
  time curl -s http://localhost:8080/api/orders/customer/1/slow > /dev/null
done

# Fast endpoint (10 requests)
for i in {1..10}; do
  time curl -s http://localhost:8080/api/orders/customer/1/fast > /dev/null
done
```

### Scenario 2: Connection Pool Exhaustion

```bash
# Bombard slow endpoint
ab -n 1000 -c 50 http://localhost:8080/api/orders/customer/1/slow

# Watch for connection errors in logs
docker-compose logs -f order-service | grep "connection"
```

### Scenario 3: Database Load

```bash
# Generate heavy N+1 query load
mvn exec:java -Dexec.mainClass="com.example.ecommerce.loadtest.OrderServiceLoadGenerator" \
  -Dexec.args="-t 50 -d 60 -s 100"

# Monitor PostgreSQL
docker stats ecommerce-postgres
```

---

## Expected Metrics

### Slow Endpoint (N+1)
```
Response Time:        500-1000ms
Database Queries:     101 per request
Connection Usage:     High
CPU Usage:            High
Memory Usage:         Growing (leaks)
Scalability:          Poor
```

### Fast Endpoint (Optimized)
```
Response Time:        5-10ms
Database Queries:     1 per request
Connection Usage:     Low
CPU Usage:            Low
Memory Usage:         Stable
Scalability:          Excellent
```

---

## Dynatrace Monitoring

### Metrics to Watch

1. **Response Time**
   - Slow: 500-1000ms
   - Fast: 5-10ms

2. **Database Queries**
   - Slow: ~101 queries per request
   - Fast: 1 query per request

3. **Database Time**
   - Slow: High percentage of total time
   - Fast: Low percentage

4. **Connection Pool**
   - Slow: High usage, possible exhaustion
   - Fast: Low usage

5. **Throughput**
   - Slow: ~10-20 req/sec
   - Fast: ~1000+ req/sec

### Dynatrace Alerts to Configure

1. High number of database calls per request (> 50)
2. Slow response time (> 500ms)
3. Database connection pool usage (> 80%)
4. High database query time (> 200ms)

---

## Troubleshooting

### Application won't start

```bash
# Check logs
docker-compose logs order-service

# Check PostgreSQL
docker-compose logs postgres

# Restart
docker-compose restart
```

### No database connection

```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Test connection
docker exec -it ecommerce-postgres psql -U admin -d ecommerce -c "SELECT 1"
```

### Load generator fails

```bash
# Check application is running
curl http://localhost:8080/api/orders/health

# Check port is accessible
netstat -an | grep 8080
```

---

## Clean Up

```bash
# Stop services
docker-compose down

# Remove volumes (clears database)
docker-compose down -v

# Remove images
docker-compose down --rmi all

# Or use helper script
./stop.sh
```

---

## Next Steps

1. ✅ Start application: `./run.sh`
2. ✅ Test slow endpoint: `curl http://localhost:8080/api/orders/customer/1/slow`
3. ✅ Test fast endpoint: `curl http://localhost:8080/api/orders/customer/1/fast`
4. ✅ Compare: `curl http://localhost:8080/api/orders/customer/1/compare`
5. ✅ Run load test: `mvn exec:java ...`
6. ✅ Monitor in Dynatrace
7. ✅ Analyze results

---

## Performance Tuning Tips

### Fix N+1 Problem

**Bad (Slow)**:
```java
List<Order> orders = orderRepository.findByCustomerId(customerId);
// Triggers N+1 queries when accessing items and shipping
```

**Good (Fast)**:
```java
@Query("SELECT DISTINCT o FROM Order o " +
       "LEFT JOIN FETCH o.items " +
       "LEFT JOIN FETCH o.shippingInfo " +
       "WHERE o.customerId = :customerId")
List<Order> findByCustomerIdOptimized(@Param("customerId") Integer customerId);
```

### Enable Query Batching

```properties
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.order_inserts=true
```

### Configure Connection Pool

```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=10
```

---

## Summary

✅ **Slow Endpoint**: Demonstrates N+1 problem with 101 queries  
✅ **Fast Endpoint**: Shows optimized solution with 1 query  
✅ **Load Generator**: Randomly calls both for realistic testing  
✅ **Monitoring**: Perfect for Dynatrace demonstration  
✅ **Performance**: 100x improvement with optimization  

Happy testing! 🚀
