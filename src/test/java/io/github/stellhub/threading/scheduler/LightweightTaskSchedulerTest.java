package io.github.stellhub.threading.scheduler;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightweightTaskSchedulerTest {

  @Test
  void shouldCompleteAllSubmittedTasks() {
    try (LightweightTaskScheduler scheduler = new LightweightTaskScheduler(2)) {
      List<CompletableFuture<Void>> futures = new ArrayList<>();
      AtomicInteger completedSteps = new AtomicInteger();

      for (int i = 0; i < 100; i++) {
        futures.add(scheduler.submit(() -> {
          completedSteps.incrementAndGet();
          return TaskDecision.complete();
        }));
      }

      CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

      assertEquals(100, completedSteps.get());
      assertEquals(100, scheduler.submittedTasks());
      assertEquals(100, scheduler.completedTasks());
      assertEquals(0, scheduler.failedTasks());
    }
  }

  @Test
  void shouldResumeTaskAfterYield() {
    try (LightweightTaskScheduler scheduler = new LightweightTaskScheduler(1)) {
      AtomicInteger steps = new AtomicInteger();

      CompletableFuture<Void> future = scheduler.submit(() -> {
        if (steps.incrementAndGet() < 3) {
          return TaskDecision.yieldNow();
        }
        return TaskDecision.complete();
      });

      future.join();

      assertEquals(3, steps.get());
      assertEquals(1, scheduler.completedTasks());
    }
  }

  @Test
  void shouldResumeTaskAfterParkDelay() throws Exception {
    try (LightweightTaskScheduler scheduler = new LightweightTaskScheduler(1)) {
      AtomicInteger steps = new AtomicInteger();
      long startedAt = System.nanoTime();

      CompletableFuture<Void> future = scheduler.submit(() -> {
        if (steps.incrementAndGet() == 1) {
          return TaskDecision.park(Duration.ofMillis(30));
        }
        return TaskDecision.complete();
      });

      future.get(1, TimeUnit.SECONDS);
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

      assertEquals(2, steps.get());
      assertTrue(elapsedMillis >= 20);
      assertEquals(1, scheduler.completedTasks());
    }
  }

  @Test
  void shouldRejectTaskAfterClose() {
    LightweightTaskScheduler scheduler = new LightweightTaskScheduler(1);
    scheduler.close();

    assertThrows(IllegalStateException.class, () -> scheduler.submit(TaskDecision::complete));
  }
}
