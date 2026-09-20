package de.danoeh.antennapod.net.sync.service;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class LockingAsyncExecutorTest {
    private static final long TIMEOUT_SECONDS = 10;

    private static class LockHolder {
        private final CountDownLatch acquired = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final Thread thread = new Thread(() -> {
            LockingAsyncExecutor.lock();
            try {
                acquired.countDown();
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                LockingAsyncExecutor.unlock();
            }
        });

        void acquire() throws InterruptedException {
            thread.start();
            assertTrue(acquired.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }

        void release() throws InterruptedException {
            release.countDown();
            thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            assertFalse(thread.isAlive());
        }
    }

    @Test
    public void runnableRunsSynchronouslyOnCallingThreadWhenLockIsFree() {
        AtomicReference<Thread> runningThread = new AtomicReference<>();

        LockingAsyncExecutor.executeLockedAsync(() -> runningThread.set(Thread.currentThread()));

        assertSame(Thread.currentThread(), runningThread.get());
    }

    @Test
    public void lockIsReleasedAfterSynchronousRun() throws InterruptedException {
        LockingAsyncExecutor.executeLockedAsync(() -> { });

        LockHolder holder = new LockHolder();
        holder.acquire();
        holder.release();
    }

    @Test
    public void lockIsReleasedWhenRunnableThrows() throws InterruptedException {
        assertThrows(IllegalStateException.class, () -> LockingAsyncExecutor.executeLockedAsync(() -> {
            throw new IllegalStateException("failure");
        }));

        LockHolder holder = new LockHolder();
        holder.acquire();
        holder.release();
    }

    @Test
    public void runnableWaitsForLockHeldByAnotherThreadAndRunsOnDifferentThread() throws InterruptedException {
        LockHolder holder = new LockHolder();
        holder.acquire();
        CountDownLatch ran = new CountDownLatch(1);
        AtomicReference<Thread> runningThread = new AtomicReference<>();

        LockingAsyncExecutor.executeLockedAsync(() -> {
            runningThread.set(Thread.currentThread());
            ran.countDown();
        });

        assertEquals(1, ran.getCount());
        holder.release();
        assertTrue(ran.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertNotEquals(Thread.currentThread(), runningThread.get());
    }

    @Test
    public void queuedRunnablesHoldTheLockWhileRunning() throws InterruptedException {
        LockHolder holder = new LockHolder();
        holder.acquire();
        CountDownLatch insideRunnable = new CountDownLatch(1);
        CountDownLatch finishRunnable = new CountDownLatch(1);
        LockingAsyncExecutor.executeLockedAsync(() -> {
            insideRunnable.countDown();
            try {
                finishRunnable.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        holder.release();
        assertTrue(insideRunnable.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        AtomicBoolean secondRan = new AtomicBoolean(false);
        CountDownLatch secondDone = new CountDownLatch(1);

        LockingAsyncExecutor.executeLockedAsync(() -> {
            secondRan.set(true);
            secondDone.countDown();
        });

        assertFalse(secondRan.get());
        finishRunnable.countDown();
        assertTrue(secondDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(secondRan.get());
    }
}
