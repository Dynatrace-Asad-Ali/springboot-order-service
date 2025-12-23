#!/bin/bash

################################################################################
# Spring Boot Order Service - Quick Start Script
################################################################################

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

print_header() {
    echo -e "${BLUE}"
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║     Spring Boot Order Service - N+1 Problem Demo            ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

print_success() {
    echo -e "${GREEN}[✓]${NC} $1"
}

print_error() {
    echo -e "${RED}[✗]${NC} $1"
}

print_info() {
    echo -e "${CYAN}[i]${NC} $1"
}

print_header

# Check if Docker is available
if command -v docker &> /dev/null && command -v docker-compose &> /dev/null; then
    echo -e "${GREEN}Using Docker Compose${NC}"
    echo ""
    
    print_info "Starting services..."
    docker-compose up -d
    
    echo ""
    print_info "Waiting for services to start..."
    sleep 10
    
    echo ""
    print_info "Checking service health..."
    
    # Wait for application to be ready
    for i in {1..30}; do
        if curl -s http://localhost:8080/api/orders/health > /dev/null 2>&1; then
            print_success "Application is ready!"
            break
        fi
        echo -n "."
        sleep 2
    done
    
    echo ""
    echo ""
    print_success "Services started successfully!"
    
else
    echo -e "${YELLOW}Docker not found, using local setup${NC}"
    echo ""
    
    # Check for Java
    if ! command -v java &> /dev/null; then
        print_error "Java not found. Please install Java 17 or higher."
        exit 1
    fi
    
    # Check for Maven
    if ! command -v mvn &> /dev/null; then
        print_error "Maven not found. Please install Maven 3.6+."
        exit 1
    fi
    
    print_info "Building application..."
    mvn clean package -DskipTests
    
    echo ""
    print_info "Starting application..."
    java -jar target/ecommerce-order-service-1.0.0.jar &
    APP_PID=$!
    
    echo ""
    print_info "Waiting for application to start..."
    
    # Wait for application
    for i in {1..30}; do
        if curl -s http://localhost:8080/api/orders/health > /dev/null 2>&1; then
            print_success "Application is ready!"
            break
        fi
        echo -n "."
        sleep 2
    done
    
    echo ""
    print_info "Application PID: $APP_PID"
    echo "$APP_PID" > .app.pid
fi

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║                    Application Ready                         ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "📡 API Endpoints:"
echo "  Health:        http://localhost:8080/api/orders/health"
echo "  Slow (N+1):    http://localhost:8080/api/orders/customer/1/slow"
echo "  Fast (JOIN):   http://localhost:8080/api/orders/customer/1/fast"
echo "  Compare:       http://localhost:8080/api/orders/customer/1/compare"
echo ""
echo "🧪 Test it:"
echo "  curl http://localhost:8080/api/orders/customer/1/slow"
echo "  curl http://localhost:8080/api/orders/customer/1/fast"
echo "  curl http://localhost:8080/api/orders/customer/1/compare"
echo ""
echo "🚀 Run load test:"
echo "  mvn exec:java -Dexec.mainClass=\"com.example.ecommerce.loadtest.OrderServiceLoadGenerator\""
echo ""
echo "📊 View logs:"
if command -v docker-compose &> /dev/null; then
    echo "  docker-compose logs -f order-service"
else
    echo "  tail -f nohup.out"
fi
echo ""
echo "🛑 Stop:"
if command -v docker-compose &> /dev/null; then
    echo "  docker-compose down"
else
    echo "  kill \$(cat .app.pid)"
fi
echo ""
