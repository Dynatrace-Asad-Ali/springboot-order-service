package com.example.ecommerce.loadtest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced Load Generator for Order Service
 * 
 * Features:
 * - Randomly calls fast and slow endpoints
 * - Configurable thread count and duration
 * - Real-time statistics
 * - Percentile calculations
 * - Detailed reporting
 */
public class OrderServiceLoadGenerator {
    
    private static final String BASE_URL = "http://localhost:8080";
    private static final String SLOW_ENDPOINT = BASE_URL + "/api/orders/customer/{customerId}/slow";
    private static final String FAST_ENDPOINT = BASE_URL + "/api/orders/customer/{customerId}/fast";
    
    // Statistics
    private static final AtomicInteger totalRequests = new AtomicInteger(0);
    private static final AtomicInteger slowRequests = new AtomicInteger(0);
    private static final AtomicInteger fastRequests = new AtomicInteger(0);
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger errorCount = new AtomicInteger(0);
    private static final AtomicLong totalResponseTime = new AtomicLong(0);
    private static final List<Long> responseTimes = new CopyOnWriteArrayList<>();
    
    // Configuration
    private static int threads = 10;
    private static int durationSeconds = 300;
    private static boolean runForever = false;
    private static double slowEndpointProbability = 0.7;  // 70% slow, 30% fast
    private static int customerId = 1;  // Customer with 50 orders
    private static int requestsPerMinute = 5;  // Target requests per minute (total across all threads)
    
    public static void main(String[] args) {
        // Parse command line arguments
        parseArguments(args);
        
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║     Order Service Load Generator - N+1 Problem Demo          ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Base URL:              " + BASE_URL);
        System.out.println("  Concurrent Threads:    " + threads);
        System.out.println("  Requests Per Minute:   " + requestsPerMinute + " (" + String.format("%.2f", requestsPerMinute / 60.0) + " req/sec)");
        System.out.println("  Test Duration:         " + (runForever ? "∞ (forever)" : durationSeconds + " seconds"));
        System.out.println("  Slow Endpoint %:       " + (slowEndpointProbability * 100) + "%");
        System.out.println("  Fast Endpoint %:       " + ((1 - slowEndpointProbability) * 100) + "%");
        System.out.println("  Customer ID:           " + customerId);
        System.out.println();
        System.out.println("Endpoints:");
        System.out.println("  🐌 SLOW: " + SLOW_ENDPOINT.replace("{customerId}", String.valueOf(customerId)));
        System.out.println("  🚀 FAST: " + FAST_ENDPOINT.replace("{customerId}", String.valueOf(customerId)));
        System.out.println();
        System.out.println("Starting load test in 3 seconds...");
        
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("LOAD TEST STARTED");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();
        
        runLoadTest();
    }
    
    private static void parseArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-t", "--threads" -> {
                    if (i + 1 < args.length) threads = Integer.parseInt(args[++i]);
                }
                case "-d", "--duration" -> {
                    if (i + 1 < args.length) durationSeconds = Integer.parseInt(args[++i]);
                }
                case "-f", "--forever" -> runForever = true;
                case "-s", "--slow-percentage" -> {
                    if (i + 1 < args.length) slowEndpointProbability = Double.parseDouble(args[++i]) / 100.0;
                }
                case "-c", "--customer" -> {
                    if (i + 1 < args.length) customerId = Integer.parseInt(args[++i]);
                }
                case "-r", "--rate" -> {
                    if (i + 1 < args.length) requestsPerMinute = Integer.parseInt(args[++i]);
                }
                case "-h", "--help" -> {
                    printUsage();
                    System.exit(0);
                }
            }
        }
    }
    
    private static void printUsage() {
        System.out.println("Usage: java OrderServiceLoadGenerator [OPTIONS]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -t, --threads <n>            Number of concurrent threads (default: 10)");
        System.out.println("  -d, --duration <seconds>     Test duration in seconds (default: 300)");
        System.out.println("  -f, --forever                Run continuously until manually stopped (Ctrl+C)");
        System.out.println("  -r, --rate <n>               Target requests per minute across all threads (default: 5)");
        System.out.println("  -s, --slow-percentage <n>    Percentage of slow requests (default: 70)");
        System.out.println("  -c, --customer <id>          Customer ID to query (default: 1)");
        System.out.println("  -h, --help                   Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java OrderServiceLoadGenerator -t 1 -r 5 -d 600");
        System.out.println("  java OrderServiceLoadGenerator -t 10 -r 60 -d 300 -s 80");
        System.out.println("  java OrderServiceLoadGenerator --threads 1 --rate 10 --duration 60");
    }
    
    private static void runLoadTest() {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (durationSeconds * 1000L);
        
        // Graceful shutdown support
        AtomicBoolean shutdownRequested = new AtomicBoolean(false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n\n🛑 Shutdown signal received. Stopping gracefully...");
            shutdownRequested.set(true);
        }));
        
        // Statistics monitor thread
        ScheduledExecutorService monitor = Executors.newScheduledThreadPool(1);
        monitor.scheduleAtFixedRate(() -> printStatistics(startTime), 10, 10, TimeUnit.SECONDS);
        
        // Calculate delay between requests to achieve target rate
        // Total requests per minute = requestsPerMinute
        // Delay per thread = (60 seconds * 1000 ms * threads) / requestsPerMinute
        int delayBetweenRequests = (60 * 1000 * threads) / requestsPerMinute;
        
        // Worker threads
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Future<?> future = executor.submit(() -> {
                Random random = new Random();
                
                while (!shutdownRequested.get() && (runForever || System.currentTimeMillis() < endTime)) {
                    try {
                        // Randomly choose endpoint
                        boolean useSlow = random.nextDouble() < slowEndpointProbability;
                        makeRequest(httpClient, useSlow);
                        
                        // Delay to achieve target rate
                        Thread.sleep(delayBetweenRequests);
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            futures.add(future);
        }
        
        // Wait for all threads to complete
        try {
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (Exception e) {
            System.err.println("Error waiting for threads: " + e.getMessage());
        }
        
        // Shutdown
        executor.shutdown();
        monitor.shutdown();
        
        // Final report
        printFinalReport(startTime);
    }
    
    private static void makeRequest(HttpClient httpClient, boolean useSlow) {
        String endpoint = useSlow ? SLOW_ENDPOINT : FAST_ENDPOINT;
        String url = endpoint.replace("{customerId}", String.valueOf(customerId));
        
        long requestStart = System.currentTimeMillis();
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            long responseTime = System.currentTimeMillis() - requestStart;
            
            totalRequests.incrementAndGet();
            if (useSlow) {
                slowRequests.incrementAndGet();
            } else {
                fastRequests.incrementAndGet();
            }
            
            if (response.statusCode() == 200) {
                successCount.incrementAndGet();
                totalResponseTime.addAndGet(responseTime);
                responseTimes.add(responseTime);
            } else {
                errorCount.incrementAndGet();
            }
            
        } catch (IOException | InterruptedException e) {
            errorCount.incrementAndGet();
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private static void printStatistics(long startTime) {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        int total = totalRequests.get();
        int success = successCount.get();
        int errors = errorCount.get();
        int slow = slowRequests.get();
        int fast = fastRequests.get();
        
        double throughput = total / (double) elapsed;
        double avgResponseTime = total > 0 ? totalResponseTime.get() / (double) total : 0;
        double errorRate = total > 0 ? (errors * 100.0) / total : 0;
        
        System.out.printf("⏱️  Time: %3ds | Total: %5d | Slow: %5d | Fast: %5d | Success: %5d | Errors: %3d | " +
                          "Throughput: %.2f req/s | Avg: %.0f ms | Error Rate: %.1f%%%n",
                elapsed, total, slow, fast, success, errors, throughput, avgResponseTime, errorRate);
    }
    
    private static void printFinalReport(long startTime) {
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("LOAD TEST COMPLETED");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();
        
        long totalDuration = (System.currentTimeMillis() - startTime) / 1000;
        int total = totalRequests.get();
        int success = successCount.get();
        int errors = errorCount.get();
        int slow = slowRequests.get();
        int fast = fastRequests.get();
        
        System.out.println("📊 SUMMARY:");
        System.out.println("  Total Duration:        " + totalDuration + " seconds");
        System.out.println("  Total Requests:        " + total);
        System.out.println("  Slow Requests (N+1):   " + slow + " (" + String.format("%.1f%%", (slow * 100.0 / total)) + ")");
        System.out.println("  Fast Requests (JOIN):  " + fast + " (" + String.format("%.1f%%", (fast * 100.0 / total)) + ")");
        System.out.println("  Successful:            " + success);
        System.out.println("  Errors:                " + errors);
        System.out.println("  Success Rate:          " + String.format("%.2f%%", (success * 100.0 / total)));
        System.out.println("  Average Throughput:    " + String.format("%.2f", total / (double) totalDuration) + " requests/sec");
        System.out.println();
        
        if (!responseTimes.isEmpty()) {
            List<Long> sorted = new ArrayList<>(responseTimes);
            Collections.sort(sorted);
            
            System.out.println("📈 RESPONSE TIMES:");
            System.out.println("  Min:                   " + sorted.get(0) + " ms");
            System.out.println("  Max:                   " + sorted.get(sorted.size() - 1) + " ms");
            System.out.println("  Average:               " + String.format("%.2f", totalResponseTime.get() / (double) total) + " ms");
            System.out.println("  Median (p50):          " + getPercentile(sorted, 50) + " ms");
            System.out.println("  p95:                   " + getPercentile(sorted, 95) + " ms");
            System.out.println("  p99:                   " + getPercentile(sorted, 99) + " ms");
            System.out.println();
        }
        
        System.out.println("🗄️  DATABASE IMPACT ESTIMATE:");
        System.out.println("  Slow endpoint queries: ~101 per request (1 + 50 items + 50 shipping)");
        System.out.println("  Fast endpoint queries: 1 per request (JOIN FETCH)");
        System.out.println("  Total slow queries:    ~" + (slow * 101));
        System.out.println("  Total fast queries:    ~" + fast);
        System.out.println("  Total queries:         ~" + ((slow * 101) + fast));
        System.out.println("  Queries saved by opt:  ~" + (slow * 100) + " (optimization = " + String.format("%.0f%%", (slow * 100.0 / ((slow * 101) + fast) * 100)) + " reduction)");
        System.out.println();
        
        System.out.println("✅ Load test completed successfully!");
        System.out.println();
    }
    
    private static long getPercentile(List<Long> sorted, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }
}
