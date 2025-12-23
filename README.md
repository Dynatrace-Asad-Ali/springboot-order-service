# Spring Boot Order Service - N+1 Query Problem Demonstration

## Overview

This Spring Boot application demonstrates the **N+1 query problem** and its solution using optimized database queries. Perfect for demonstrating performance issues with Dynatrace monitoring.

### Key Features

1. **Two API Endpoints**:
   - 🐌 **Slow endpoint**: Uses N+1 queries (101 database queries for 50 orders)
   - 🚀 **Fast endpoint**: Uses JOIN FETCH (1 database query for 50 orders)

2. **Performance Comparison**:
   - Slow: ~500-1000ms response time
   - Fast: ~5-10ms response time
   - **100x faster!**

3. **Load Generator**:
   - Randomly calls both endpoints
   - Configurable slow/fast ratio
   - Real-time statistics
   - Perfect for Dynatrace monitoring

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Load Generator                           │
│  Randomly calls:                                            │
│  - 70% → /api/orders/customer/1/slow (N+1 problem)        │
│  - 30% → /api/orders/customer/1/fast (optimized)          │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│              Spring Boot Order Service                      │
│                                                             │
│  🐌 Slow Endpoint:                                          │
│  1. SELECT * FROM orders WHERE customer_id = 1             │
│  2. SELECT * FROM order_items WHERE order_id = 1 (×50)    │
│  3. SELECT * FROM shipping WHERE order_id = 1 (×50)       │
│  Total: 101 queries                                        │
│                                                             │
│  🚀 Fast Endpoint:                                          │
│  1. SELECT o.*, i.*, s.* FROM orders o                     │
│     LEFT JOIN order_items i ON o.order_id = i.order_id    │
│     LEFT JOIN shipping s ON o.order_id = s.order_id       │
│     WHERE o.customer_id = 1                                │
│  Total: 1 query                                            │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                  PostgreSQL Database                        │
│  - 100 orders for customer_id=1 (50 orders each)          │
│  - 2,500 order items                                       │
│  - 100 shipping records                                    │
└─────────────────────────────────────────────────────────────┘
```

---

## Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- PostgreSQL 12+ (or use Docker)
- (Optional) Docker and Docker Compose

### Option 1: Run with Docker (Recommended)

```bash
# 1. Clone or navigate to project directory
cd springboot-order-service

# 2. Start everything with Docker Compose
docker-compose up -d

# Wait for services to start (~30 seconds)

# 3. Verify it's running
curl http://localhost:8080/api/orders/health
```

### Option 2: Run Locally

#### 1. Setup PostgreSQL Database

```bash
# Create database
createdb -U postgres ecommerce

# Run the initialization script
psql -U postgres -d ecommerce -f init-db.sql
```

#### 2. Build and Run Application

```bash
# Build
mvn clean package

# Run
java -jar target/ecommerce-order-service-1.0.0.jar
```

#### 3. Verify

```bash
curl http://localhost:8080/api/orders/health
```

---

## API Endpoints

### 1. Slow Endpoint (N+1 Problem)

**URL**: `GET /api/orders/customer/{customerId}/slow`

**Example**:
```bash
curl http://localhost:8080/api/orders/customer/1/slow
```

**Performance**:
- Queries: ~101 (1 + 50 + 50)
- Response time: ~500-1000ms
- Database load: HIGH

### 2. Fast Endpoint (Optimized)

**URL**: `GET /api/orders/customer/{customerId}/fast`

**Example**:
```bash
curl http://localhost:8080/api/orders/customer/1/fast
```

**Performance**:
- Queries: 1 (with JOINs)
- Response time: ~5-10ms
- Database load: LOW

### 3. Dynamic Endpoint

**URL**: `GET /api/orders/customer/{customerId}?optimized=true/false`

**Examples**:
```bash
# Slow
curl http://localhost:8080/api/orders/customer/1?optimized=false

# Fast
curl http://localhost:8080/api/orders/customer/1?optimized=true
```

### 4. Comparison Endpoint

**URL**: `GET /api/orders/customer/{customerId}/compare`

Runs both methods and returns performance comparison:

```bash
curl http://localhost:8080/api/orders/customer/1/compare
```

**Example Response**:
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

### 5. Health Check

**URL**: `GET /api/orders/health`

```bash
curl http://localhost:8080/api/orders/health
```

---

## Load Testing

### Running the Load Generator

```bash
# Compile
mvn clean compile

# Run with defaults (10 threads, 300 seconds, 70% slow)
mvn exec:java -Dexec.mainClass="com.example.ecommerce.loadtest.OrderServiceLoadGenerator"

# Run with custom configuration
mvn exec:java -Dexec.mainClass="com.example.ecommerce.loadtest.OrderServiceLoadGenerator" \
  -Dexec.args="-t 20 -d 600 -s 80"
```

### Load Generator Options

```
-t, --threads <n>            Number of concurrent threads (default: 10)
-d, --duration <seconds>     Test duration in seconds (default: 300)
-s, --slow-percentage <n>    Percentage of slow requests (default: 70)
-c, --customer <id>          Customer ID to query (default: 1)
-h, --help                   Show help message
```

### Example Load Tests

#### Baseline Test (Light Load)
```bash
mvn exec:java -Dexec.mainClass="com.example.ecommerce.loadtest.OrderServiceLoadGenerator" \
  -Dexec.args="-t 5 -d 60 -s 70"
```

#### Normal Load
```bash
mvn exec:java -Dexec.mainClass="com.example.ecommerce.loadtest.OrderServiceLoadGenerator" \
  -Dexec.args="-t 10 -d 300 -s 70"
```

#### Stress Test
```bash
mvn exec:java -Dexec.mainClass="com.example.ecommerce.loadtest.OrderServiceLoadGenerator" \
  -Dexec.args="-t 50 -d 300 -s 80"
```

#### Worst Case (All Slow)
```bash
mvn exec:java -Dexec.mainClass="com.example.ecommerce.loadtest.OrderServiceLoadGenerator" \
  -Dexec.args="-t 20 -d 300 -s 100"
```

---

## Expected Results

### During Load Test

```
⏱️  Time:  10s | Total:   485 | Slow:   340 | Fast:   145 | Success:   485 | Errors:   0 | Throughput: 48.50 req/s | Avg: 203 ms | Error Rate: 0.0%
⏱️  Time:  20s | Total:   978 | Slow:   685 | Fast:   293 | Success:   978 | Errors:   0 | Throughput: 48.90 req/s | Avg: 204 ms | Error Rate: 0.0%
⏱️  Time:  30s | Total:  1467 | Slow:  1027 | Fast:   440 | Success:  1467 | Errors:   0 | Throughput: 48.90 req/s | Avg: 204 ms | Error Rate: 0.0%
```

### Final Report

```
═══════════════════════════════════════════════════════════════
LOAD TEST COMPLETED
═══════════════════════════════════════════════════════════════

📊 SUMMARY:
  Total Duration:        300 seconds
  Total Requests:        14670
  Slow Requests (N+1):   10269 (70.0%)
  Fast Requests (JOIN):  4401 (30.0%)
  Successful:            14670
  Errors:                0
  Success Rate:          100.00%
  Average Throughput:    48.90 requests/sec

📈 RESPONSE TIMES:
  Min:                   3 ms
  Max:                   1245 ms
  Average:               204.35 ms
  Median (p50):          195 ms
  p95:                   856 ms
  p99:                   1023 ms

🗄️  DATABASE IMPACT ESTIMATE:
  Slow endpoint queries: ~101 per request (1 + 50 items + 50 shipping)
  Fast endpoint queries: 1 per request (JOIN FETCH)
  Total slow queries:    ~1037169
  Total fast queries:    ~4401
  Total queries:         ~1041570
  Queries saved by opt:  ~1026900 (optimization = 98% reduction)

✅ Load test completed successfully!
```

---

## Monitoring with Dynatrace

### What Dynatrace Will Detect

1. **N+1 Query Problem** on slow endpoint:
   - High number of database calls
   - Inefficient query patterns
   - Database connection pool exhaustion

2. **Performance Difference**:
   - Slow endpoint: High response time
   - Fast endpoint: Low response time
   - 100x performance gap

3. **Database Load**:
   - Slow endpoint causes ~101 queries per request
   - Fast endpoint causes 1 query per request

4. **Response Time Distribution**:
   - Bimodal distribution (fast and slow)
   - Clear performance patterns

### Dynatrace Configuration

Enable these settings:
- Database monitoring
- SQL statement monitoring
- Response time monitoring
- Transaction flow analysis

---

## Performance Comparison

| Metric | Slow (N+1) | Fast (JOIN FETCH) | Improvement |
|--------|-----------|-------------------|-------------|
| Response Time | ~500-1000ms | ~5-10ms | 100x faster |
| Database Queries | 101 | 1 | 100x fewer |
| Database Load | Very High | Low | 99% reduction |
| Scalability | Poor | Excellent | ∞ |
| Connection Pool | Exhausted | Minimal | - |

---

## Project Structure

```
springboot-order-service/
├── src/main/
│   ├── java/com/example/ecommerce/
│   │   ├── OrderServiceApplication.java    # Main application
│   │   ├── controller/
│   │   │   └── OrderController.java        # REST endpoints
│   │   ├── service/
│   │   │   └── OrderService.java           # Business logic
│   │   ├── repository/
│   │   │   └── OrderRepository.java        # Data access
│   │   ├── entity/
│   │   │   ├── Order.java                  # JPA entity
│   │   │   ├── OrderItem.java              # JPA entity
│   │   │   └── ShippingInfo.java           # JPA entity
│   │   └── loadtest/
│   │       └── OrderServiceLoadGenerator.java # Load tester
│   └── resources/
│       └── application.properties           # Configuration
├── pom.xml                                  # Maven dependencies
├── Dockerfile                               # Docker image
├── docker-compose.yml                       # Docker Compose
├── init-db.sql                              # Database setup
└── README.md                                # This file
```

---

## Troubleshooting

### Application won't start

**Check PostgreSQL is running**:
```bash
docker ps | grep postgres
# or
psql -U admin -d ecommerce -c "SELECT 1"
```

**Check logs**:
```bash
docker-compose logs order-service
```

### No data in database

**Re-run initialization**:
```bash
psql -U admin -d ecommerce -f init-db.sql
```

### Load generator errors

**Check application is running**:
```bash
curl http://localhost:8080/api/orders/health
```

### Slow performance

This is expected for the slow endpoint! That's the point of the demo. 😊

---

## Key Learnings

### N+1 Query Problem

**What it is**:
- 1 query to fetch parent records
- N additional queries to fetch related records
- Total: 1 + N queries

**Why it's bad**:
- Excessive database round trips
- Connection pool exhaustion
- Poor scalability
- High latency

**How to fix**:
- Use JOIN FETCH
- Use @EntityGraph
- Use batch fetching
- Use DTOs with custom queries

---

## Next Steps

1. **Run load test** and observe Dynatrace metrics
2. **Compare slow vs fast** endpoints
3. **Analyze database queries** in logs
4. **Experiment with different loads**
5. **Monitor connection pool** usage
6. **Try different optimization** techniques

---

## Additional Resources

- [Spring Data JPA Documentation](https://spring.io/projects/spring-data-jpa)
- [Hibernate N+1 Problem](https://www.baeldung.com/hibernate-common-performance-problems-in-logs)
- [JOIN FETCH Best Practices](https://vladmihalcea.com/jpql-join-fetch-clause/)

---

## License

MIT License - Feel free to use for learning and demonstrations.

---

## Author

Created for demonstrating N+1 query problems with Dynatrace monitoring.
