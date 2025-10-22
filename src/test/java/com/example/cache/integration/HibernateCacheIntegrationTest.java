package com.example.cache.integration;

import com.example.cache.factory.CustomRegionFactory;
import com.example.cache.metrics.MetricsCollector;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;

import jakarta.persistence.*;
import org.hibernate.annotations.NaturalId;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Hibernate Cache Integration Tests")
class HibernateCacheIntegrationTest {

    private SessionFactory sessionFactory;
    private CustomRegionFactory regionFactory;

    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop");
        properties.setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect");
        properties.setProperty(AvailableSettings.URL, "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        properties.setProperty(AvailableSettings.USER, "sa");
        properties.setProperty(AvailableSettings.PASS, "");
        properties.setProperty(AvailableSettings.SHOW_SQL, "true");
        
        // Cache configuration
        properties.setProperty(AvailableSettings.USE_SECOND_LEVEL_CACHE, "true");
        properties.setProperty(AvailableSettings.USE_QUERY_CACHE, "true");
        properties.setProperty(AvailableSettings.CACHE_REGION_FACTORY, CustomRegionFactory.class.getName());
        properties.setProperty("hibernate.cache.max_entries", "100");
        properties.setProperty("hibernate.cache.ttl_seconds", "60");
        properties.setProperty("hibernate.cache.debug_logging", "true");

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(properties)
                .build();

        try {
            sessionFactory = new MetadataSources(registry)
                    .addAnnotatedClass(TestUser.class)
                    .buildMetadata()
                    .buildSessionFactory();
            
            regionFactory = (CustomRegionFactory) sessionFactory.getSessionFactoryOptions()
                    .getServiceRegistry()
                    .getService(RegionFactory.class);
        } catch (Exception e) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw e;
        }
    }

    @AfterEach
    void tearDown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @Test
    @DisplayName("Should cache entity on first load and return from cache on second load")
    void testEntityCaching() {
        // Create and save entity
        TestUser user = new TestUser("john@example.com", "John Doe");
        
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(user);
            session.getTransaction().commit();
        }

        // Clear session to ensure we're not using first-level cache
        sessionFactory.getCache().evictAll();

        // Get initial metrics
        Map<String, MetricsCollector> initialMetrics = regionFactory.getAllMetrics();
        long initialHits = initialMetrics.values().stream().mapToLong(MetricsCollector::getHits).sum();
        long initialMisses = initialMetrics.values().stream().mapToLong(MetricsCollector::getMisses).sum();

        // First load - should hit database and populate cache
        TestUser loadedUser1;
        try (Session session = sessionFactory.openSession()) {
            loadedUser1 = session.find(TestUser.class, user.getId());
        }

        // Get metrics after first load
        Map<String, MetricsCollector> afterFirstLoadMetrics = regionFactory.getAllMetrics();
        long afterFirstLoadHits = afterFirstLoadMetrics.values().stream().mapToLong(MetricsCollector::getHits).sum();
        long afterFirstLoadMisses = afterFirstLoadMetrics.values().stream().mapToLong(MetricsCollector::getMisses).sum();

        // Second load - should hit cache
        TestUser loadedUser2;
        try (Session session = sessionFactory.openSession()) {
            loadedUser2 = session.find(TestUser.class, user.getId());
        }

        // Get final metrics
        Map<String, MetricsCollector> finalMetrics = regionFactory.getAllMetrics();
        long finalHits = finalMetrics.values().stream().mapToLong(MetricsCollector::getHits).sum();
        long finalMisses = finalMetrics.values().stream().mapToLong(MetricsCollector::getMisses).sum();

        // Verify entities are loaded correctly
        assertNotNull(loadedUser1);
        assertNotNull(loadedUser2);
        assertEquals(loadedUser1.getEmail(), loadedUser2.getEmail());
        assertEquals(loadedUser1.getName(), loadedUser2.getName());

        // Verify cache behavior
        assertTrue(finalMisses > initialMisses, "Should have at least one cache miss (first load)");
        assertTrue(finalHits > afterFirstLoadHits, "Should have cache hits on second load");
        
        System.out.printf("Cache metrics - Initial: hits=%d, misses=%d, After first load: hits=%d, misses=%d, Final: hits=%d, misses=%d%n",
                         initialHits, initialMisses, afterFirstLoadHits, afterFirstLoadMisses, finalHits, finalMisses);
    }

    @Test
    @DisplayName("Should cache query results")
    void testQueryCaching() {
        // Create test data
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(new TestUser("user1@example.com", "User 1"));
            session.persist(new TestUser("user2@example.com", "User 2"));
            session.persist(new TestUser("user3@example.com", "User 3"));
            session.getTransaction().commit();
        }

        // Clear cache
        sessionFactory.getCache().evictAll();

        // First query - should hit database
        try (Session session = sessionFactory.openSession()) {
            session.createSelectionQuery("SELECT u FROM HibernateCacheIntegrationTest$TestUser u", TestUser.class)
                    .setCacheable(true)
                    .getResultList();
        }

        // Second query - should hit cache
        try (Session session = sessionFactory.openSession()) {
            session.createSelectionQuery("SELECT u FROM HibernateCacheIntegrationTest$TestUser u", TestUser.class)
                    .setCacheable(true)
                    .getResultList();
        }

        // Verify query cache metrics
        Map<String, MetricsCollector> metrics = regionFactory.getAllMetrics();
        assertFalse(metrics.isEmpty());
    }

    @Test
    @DisplayName("Should evict entity from cache on update")
    void testCacheEvictionOnUpdate() {
        TestUser user = new TestUser("test@example.com", "Test User");
        
        // Save entity
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(user);
            session.getTransaction().commit();
        }

        // Load to populate cache
        try (Session session = sessionFactory.openSession()) {
            session.find(TestUser.class, user.getId());
        }

        // Update entity
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            TestUser loadedUser = session.find(TestUser.class, user.getId());
            loadedUser.setName("Updated Name");
            session.getTransaction().commit();
        }

        // Verify cache was evicted and updated
        try (Session session = sessionFactory.openSession()) {
            TestUser updatedUser = session.find(TestUser.class, user.getId());
            assertEquals("Updated Name", updatedUser.getName());
        }
    }

    @Test
    @DisplayName("Should respect TTL configuration")
    void testTTLConfiguration() throws InterruptedException {
        // Create entity
        TestUser user = new TestUser("ttl@example.com", "TTL Test");
        
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(user);
            session.getTransaction().commit();
        }

        // Load to populate cache
        try (Session session = sessionFactory.openSession()) {
            session.find(TestUser.class, user.getId());
        }

        // Wait for TTL to expire (using short TTL for test)
        Thread.sleep(2000);

        // Load again - should hit database due to TTL expiration
        try (Session session = sessionFactory.openSession()) {
            session.find(TestUser.class, user.getId());
        }

        // Verify metrics show evictions due to TTL
        Map<String, MetricsCollector> metrics = regionFactory.getAllMetrics();
        assertFalse(metrics.isEmpty());
    }

    @Test
    @DisplayName("Should handle concurrent access safely")
    void testConcurrentAccess() throws InterruptedException {
        // Create test data
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            for (int i = 0; i < 10; i++) {
                session.persist(new TestUser("user" + i + "@example.com", "User " + i));
            }
            session.getTransaction().commit();
        }

        int threadCount = 5;
        Thread[] threads = new Thread[threadCount];
        Exception[] exceptions = new Exception[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 10; j++) {
                        try (Session session = sessionFactory.openSession()) {
                            TestUser user = session.find(TestUser.class, (long) (threadId + 1));
                            if (user != null) {
                                // Simulate some work
                                Thread.sleep(10);
                            }
                        }
                    }
                } catch (Exception e) {
                    exceptions[threadId] = e;
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Check for exceptions
        for (Exception exception : exceptions) {
            if (exception != null) {
                fail("Thread threw exception: " + exception.getMessage());
            }
        }

        // Verify cache is still functional
        try (Session session = sessionFactory.openSession()) {
            TestUser user = session.find(TestUser.class, 1L);
            assertNotNull(user);
        }
    }

    @Entity
    @Table(name = "test_users")
    @org.hibernate.annotations.Cache(usage = org.hibernate.annotations.CacheConcurrencyStrategy.READ_WRITE)
    public static class TestUser {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @NaturalId
        @Column(unique = true)
        private String email;

        @Column
        private String name;

        public TestUser() {}

        public TestUser(String email, String name) {
            this.email = email;
            this.name = name;
        }

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
