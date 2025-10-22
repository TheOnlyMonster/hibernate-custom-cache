# Hibernate Custom Cache

A custom cache implementation for Hibernate ORM built after learning Hibernate. This project implements different concurrency strategies and provides a thread-safe caching solution.

## What it does

This cache supports multiple concurrency strategies:

- **Read-Only**: For immutable data
- **Read-Write**: Full CRUD with soft locking
- **Non-Strict Read-Write**: Eventual consistency
- **Transactional**: ACID compliance (planned)

## Key Features

- Thread-safe operations using ConcurrentHashMap
- LRU eviction policy with configurable TTL
- Soft locking mechanism to prevent cache corruption
- Performance metrics and monitoring
- Integration with Hibernate's second-level cache

## Tech Stack

- Java 17
- Hibernate 6.4.4
- Maven
- JUnit 5 for testing
- H2 database for integration tests

## Project Structure

```
src/main/java/com/example/cache/
├── access/           # Different concurrency strategies
├── factory/          # CustomRegionFactory for Hibernate
├── region/           # Cache region implementations
├── storage/          # InMemoryLRUCache
├── metrics/          # Performance tracking
└── config/           # Configuration management
```

## Usage

Add to your Hibernate configuration:

```properties
hibernate.cache.use_second_level_cache=true
hibernate.cache.region.factory_class=com.example.cache.factory.CustomRegionFactory
hibernate.cache.max_entries=10000
hibernate.cache.ttl_seconds=3600
```

## Testing

The project includes comprehensive tests with real performance results:

**Performance Benchmarks:**

- **High throughput reads**: 434,782 ops/sec (10,000 operations in 23ms)
- **High throughput writes**: 156,250 ops/sec (2,500 operations in 16ms)
- **Mixed workload**: 571,428 ops/sec (4,000 operations in 7ms)
- **Sustained load**: 2.4M ops/sec over 10 seconds with 0% error rate
- **Cache warming**: 277,777 ops/sec (5,000 operations in 18ms)
- **Lock contention**: 100% success rate (1,000 locks in 2.6s)
- **Memory pressure**: Handles 1,500 operations in 9ms with proper eviction

**Test Coverage:**

- Unit tests for individual components
- Integration tests with Hibernate
- Performance tests with concurrent access
- Concurrency tests for thread safety

Run tests with:

```bash
mvn test
```

## What I learned

- Hibernate's internal caching mechanisms
- Java concurrency and thread safety
- Soft locking patterns
- Performance optimization techniques
- Testing concurrent systems

This was a great way to understand how Hibernate's second-level cache works under the hood and practice advanced Java concepts.
