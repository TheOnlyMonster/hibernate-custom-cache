package com.example.cache.storage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class InMemoryLRUCache<K, V> {
  private final ConcurrentHashMap<K, Node> map = new ConcurrentHashMap<>();
  private final int maxEntries;
  private final long ttlMillis;
  private final Node head = new Node(null, null), tail = new Node(null, null);
  private final ReentrantLock lock = new ReentrantLock();
  private final AtomicInteger size = new AtomicInteger(0);

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

  public InMemoryLRUCache(int maxEntries, long ttlMillis) {
    if (maxEntries <= 0)
      throw new IllegalArgumentException("maxEntries must be > 0");
    if (ttlMillis < 0)
      throw new IllegalArgumentException("ttlMillis must be >= 0");
    this.maxEntries = maxEntries;
    this.ttlMillis = ttlMillis;
    head.next = tail;
    tail.prev = head;
  }

  public V get(K key) {
    Node n = map.get(key);
    if (n == null)
      return null;
    long now = System.currentTimeMillis();
    if (ttlMillis > 0 && (now - n.lastAccess) > ttlMillis) {
      remove(key);
      return null;
    }
    n.lastAccess = now;
    moveToFront(n);
    return n.value;
  }

  public void put(K key, V value) {
      Node existing = map.get(key);
      // Fast path for existing key
      if (existing != null) {
        existing.value = value;
        existing.lastAccess = System.currentTimeMillis();
        moveToFront(existing);
        return;
      }
      lock.lock();
      try {
        Node n = map.get(key);
        // Double-check after acquiring lock
        if (n != null) {
            n.value = value;
            n.lastAccess = System.currentTimeMillis();
            moveToFront(n);
            return;
        }
        Node newNode = new Node(key, value);
        addFront(newNode);
        map.put(key, newNode);
        if (size.incrementAndGet() > maxEntries) {
            evictTail();
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
    if (n.prev != null) {
      n.prev.next = n.next;
      n.next.prev = n.prev;
      n.prev = null;
      n.next = null;
    }
  }

  private void moveToFront(Node n) {
    lock.lock();
    try {
      unlink(n);
      addFront(n);
      n.lastAccess = System.currentTimeMillis();
    } finally {
      lock.unlock();
    }
  }
  
  private void addFront(Node n) {
    n.next = head.next;
    n.prev = head;
    head.next.prev = n;
    head.next = n;
  }
  
  private void evictTail() {
      Node t = tail.prev;
      if (t == head) return;
      remove(t.key);
  }

  public int size() {
    return size.get();
  }
  
  public void clear() {
        lock.lock();
        try {
            map.clear();
            head.next = tail; tail.prev = head;
            size.set(0);
        } finally {
            lock.unlock();
        }
    }
}

