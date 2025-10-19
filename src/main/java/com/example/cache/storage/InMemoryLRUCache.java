package com.example.cache.storage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import com.example.cache.metrics.MetricsCollector;

public class InMemoryLRUCache<K, V> {
  private final ConcurrentHashMap<K, Node> map = new ConcurrentHashMap<>();
  private final int maxEntries;
  private final long ttlMillis;
  private final Node head = new Node(null, null);
  private final Node tail = new Node(null, null);
  private final ReentrantLock lock = new ReentrantLock();
  private final AtomicInteger size = new AtomicInteger(0);
  private final MetricsCollector metrics;

  private class Node {
    final K key;
    V value;
    long lastAccess;
    Node prev, next;

    Node(K k, V v) {
      this.key = k;
      this.value = v;
      this.lastAccess = System.currentTimeMillis();
    }
  }

  public InMemoryLRUCache(int maxEntries, long ttlMillis, MetricsCollector metrics) {
    if (maxEntries <= 0)
      throw new IllegalArgumentException("maxEntries must be > 0");
    if (ttlMillis < 0)
      throw new IllegalArgumentException("ttlMillis must be >= 0");
    this.maxEntries = maxEntries;
    this.metrics = metrics;
    this.ttlMillis = ttlMillis;
    head.next = tail;
    tail.prev = head;
  }


  public V get(K key) {
    lock.lock();
    try {
      Node n = map.get(key);
      if (n == null) {
        metrics.miss();
        return null;
      }
      
      long now = System.currentTimeMillis();
      if (ttlMillis > 0 && (now - n.lastAccess) > ttlMillis) {
        map.remove(key);
        unlink(n);
        size.decrementAndGet();
        metrics.miss();
        metrics.evict(); 
        return null;
      }
      
      metrics.hit();
      n.lastAccess = now;
      unlink(n);
      addFront(n);
      return n.value;
    } finally {
      lock.unlock();
    }
  }


  public void put(K key, V value) {
    lock.lock();
    try {
      Node existing = map.get(key);
      
      if (existing != null) {
        existing.value = value;
        existing.lastAccess = System.currentTimeMillis();
        unlink(existing);
        addFront(existing);
        metrics.put();
        return;
      }
      
      Node newNode = new Node(key, value);
      addFront(newNode);
      map.put(key, newNode);
      metrics.put();
      
      if (size.incrementAndGet() > maxEntries) {
        evictLRU();
      }
    } finally {
      lock.unlock();
    }
  }
  

  public void remove(K key) {
    lock.lock();
    try {
      Node n = map.remove(key);
      if (n != null) {
        unlink(n);
        size.decrementAndGet();
      }
    } finally {
      lock.unlock();
    }
  }


  private void unlink(Node n) {
    if (n.prev != null && n.next != null) {
      n.prev.next = n.next;
      n.next.prev = n.prev;
    }
  }
  

  private void addFront(Node n) {
    n.next = head.next;
    n.prev = head;
    head.next.prev = n;
    head.next = n;
  }
  

  private void evictLRU() {
    Node lru = tail.prev;
    if (lru == head) {
      return; 
    }
    
    map.remove(lru.key);
    unlink(lru);
    size.decrementAndGet();
    metrics.evict();
  }


  public int size() {
    return size.get();
  }
  

  public void clear() {
    lock.lock();
    try {
      map.clear();
      head.next = tail;
      tail.prev = head;
      size.set(0);
    } finally {
      lock.unlock();
    }
  }

}