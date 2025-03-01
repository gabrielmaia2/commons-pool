/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.pool2.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.pool2.TestException;
import org.apache.commons.pool2.impl.TestGenericObjectPool.SimpleFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 */
public class TestBaseGenericObjectPool {

    BaseGenericObjectPool<String, TestException> pool;
    SimpleFactory factory;

    @BeforeEach
    public void setUp() {
        factory = new SimpleFactory();
        pool = new GenericObjectPool<>(factory);
    }

    @AfterEach
    public void tearDown() {
        pool.close();
        pool = null;
        factory = null;
    }

    @Test
    public void testActiveTimeStatistics() {
        for (int i = 0; i < 99; i++) { // must be < MEAN_TIMING_STATS_CACHE_SIZE
            pool.updateStatsReturn(Duration.ofMillis(i));
        }
        assertEquals(49, pool.getMeanActiveTimeMillis(), Double.MIN_VALUE);
    }

    @Test
    public void testBorrowWaitStatistics() {
        final DefaultPooledObject<String> p = (DefaultPooledObject<String>) factory.makeObject();
        pool.updateStatsBorrow(p, Duration.ofMillis(10));
        pool.updateStatsBorrow(p, Duration.ofMillis(20));
        pool.updateStatsBorrow(p, Duration.ofMillis(20));
        pool.updateStatsBorrow(p, Duration.ofMillis(30));
        assertEquals(20, pool.getMeanBorrowWaitTimeMillis(), Double.MIN_VALUE);
        assertEquals(30, pool.getMaxBorrowWaitTimeMillis(), 0);
    }

    public void testBorrowWaitStatisticsMax() {
        final DefaultPooledObject<String> p = (DefaultPooledObject<String>) factory.makeObject();
        assertEquals(0, pool.getMaxBorrowWaitTimeMillis(), Double.MIN_VALUE);
        pool.updateStatsBorrow(p, Duration.ZERO);
        assertEquals(0, pool.getMaxBorrowWaitTimeMillis(), Double.MIN_VALUE);
        pool.updateStatsBorrow(p, Duration.ofMillis(20));
        assertEquals(20, pool.getMaxBorrowWaitTimeMillis(), Double.MIN_VALUE);
        pool.updateStatsBorrow(p, Duration.ofMillis(20));
        assertEquals(20, pool.getMaxBorrowWaitTimeMillis(), Double.MIN_VALUE);
        pool.updateStatsBorrow(p, Duration.ofMillis(10));
        assertEquals(20, pool.getMaxBorrowWaitTimeMillis(), Double.MIN_VALUE);
    }

    @Test
    public void testEvictionTimerMultiplePools() throws InterruptedException {
        final AtomicIntegerFactory factory = new AtomicIntegerFactory();
        factory.setValidateLatency(50);
        try (final GenericObjectPool<AtomicInteger, RuntimeException> evictingPool = new GenericObjectPool<>(factory)) {
            evictingPool.setTimeBetweenEvictionRuns(Duration.ofMillis(100));
            evictingPool.setNumTestsPerEvictionRun(5);
            evictingPool.setTestWhileIdle(true);
            evictingPool.setMinEvictableIdleTime(Duration.ofMillis(50));
            for (int i = 0; i < 10; i++) {
                try {
                    evictingPool.addObject();
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }

            for (int i = 0; i < 1000; i++) {
                try (final GenericObjectPool<AtomicInteger, RuntimeException> nonEvictingPool = new GenericObjectPool<>(factory)) {
                    // empty
                }
            }

            Thread.sleep(1000);
            assertEquals(0, evictingPool.getNumIdle());
        }
    }
}
