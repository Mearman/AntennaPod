package de.danoeh.antennapod.net.sync.service;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
        private final AtomicBoolean releasing = new AtomicBoolean(false);
        private final Thread thread = new Thread(() -> {
            LockingAsyncExecutor.lock();
            try {
                acquired.countDown();
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                releasing.set(true);
                LockingAsyncExecutor.unlock();
            }
        });

        boolean isReleasing() {
            return releasing.get();
        }

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
        AtomicBoolean holderWasReleasing = new AtomicBoolean(false);

        LockingAsyncExecutor.executeLockedAsync(() -> {
            runningThread.set(Thread.currentThread());
            holderWasReleasing.set(holder.isReleasing());
            ran.countDown();
        });

        holder.release();
        assertTrue(ran.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(holderWasReleasing.get());
        assertNotEquals(Thread.currentThread(), runningThread.get());
    }

    @Test
    public void queuedRunnablesHoldTheLockWhileRunning() throws InterruptedException {
        LockHolder holder = new LockHolder();
        holder.acquire();
        CountDownLatch insideRunnable = new CountDownLatch(1);
        CountDownLatch finishRunnable = new CountDownLatch(1);
        AtomicBoolean firstFinished = new AtomicBoolean(false);
        LockingAsyncExecutor.executeLockedAsync(() -> {
            insideRunnable.countDown();
            try {
                finishRunnable.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            firstFinished.set(true);
        });
        holder.release();
        assertTrue(insideRunnable.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        AtomicBoolean firstHadFinishedWhenSecondRan = new AtomicBoolean(false);
        CountDownLatch secondDone = new CountDownLatch(1);

        LockingAsyncExecutor.executeLockedAsync(() -> {
            firstHadFinishedWhenSecondRan.set(firstFinished.get());
            secondDone.countDown();
        });

        finishRunnable.countDown();
        assertTrue(secondDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(firstHadFinishedWhenSecondRan.get());
    }
}
