package io.github.stellhub.threading.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class LightweightTaskScheduler implements AutoCloseable {

  private final BlockingQueue<ScheduledTask> readyTasks = new LinkedBlockingQueue<>();
  private final DelayQueue<ParkedTask> parkedTasks = new DelayQueue<>();
  private final List<Thread> carrierThreads = new ArrayList<>();
  private final Thread timerThread;
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final AtomicLong submittedTasks = new AtomicLong();
  private final AtomicLong completedTasks = new AtomicLong();
  private final AtomicLong failedTasks = new AtomicLong();

  public LightweightTaskScheduler(int carrierThreadCount) {
    if (carrierThreadCount <= 0) {
      throw new IllegalArgumentException("carrierThreadCount must be positive");
    }

    for (int i = 0; i < carrierThreadCount; i++) {
      Thread carrierThread = Thread.ofPlatform()
          .name("toy-carrier-", i)
          .start(this::runCarrierLoop);
      carrierThreads.add(carrierThread);
    }

    timerThread = Thread.ofPlatform()
        .name("toy-scheduler-timer")
        .start(this::runTimerLoop);
  }

  /**
   * 提交一个轻量任务，返回的 Future 会在任务完成或失败时结束。
   */
  public CompletableFuture<Void> submit(LightweightTask task) {
    Objects.requireNonNull(task, "task must not be null");
    if (!running.get()) {
      throw new IllegalStateException("scheduler already closed");
    }

    CompletableFuture<Void> completion = new CompletableFuture<>();
    submittedTasks.incrementAndGet();
    readyTasks.offer(new ScheduledTask(task, completion));
    return completion;
  }

  /**
   * 返回已经提交的任务数量。
   */
  public long submittedTasks() {
    return submittedTasks.get();
  }

  /**
   * 返回已经完成的任务数量。
   */
  public long completedTasks() {
    return completedTasks.get();
  }

  /**
   * 返回执行失败的任务数量。
   */
  public long failedTasks() {
    return failedTasks.get();
  }

  /**
   * 关闭调度器，并中断所有载体线程。
   */
  @Override
  public void close() {
    if (running.compareAndSet(true, false)) {
      timerThread.interrupt();
      for (Thread carrierThread : carrierThreads) {
        carrierThread.interrupt();
      }
      joinQuietly(timerThread);
      for (Thread carrierThread : carrierThreads) {
        joinQuietly(carrierThread);
      }
    }
  }

  private void runCarrierLoop() {
    while (running.get() || !readyTasks.isEmpty()) {
      try {
        ScheduledTask scheduledTask = readyTasks.poll(100, TimeUnit.MILLISECONDS);
        if (scheduledTask != null) {
          runOneStep(scheduledTask);
        }
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  private void runOneStep(ScheduledTask scheduledTask) {
    try {
      TaskDecision decision = scheduledTask.task().run();
      if (decision == null) {
        throw new IllegalStateException("task decision must not be null");
      }

      switch (decision.type()) {
        case COMPLETE -> completeTask(scheduledTask);
        case YIELD -> readyTasks.offer(scheduledTask);
        case PARK -> parkedTasks.offer(ParkedTask.after(scheduledTask, decision.delayNanos()));
      }
    } catch (Exception exception) {
      failTask(scheduledTask, exception);
    }
  }

  private void runTimerLoop() {
    while (running.get() || !parkedTasks.isEmpty()) {
      try {
        ParkedTask parkedTask = parkedTasks.poll(100, TimeUnit.MILLISECONDS);
        if (parkedTask != null) {
          readyTasks.offer(parkedTask.scheduledTask());
        }
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  private void completeTask(ScheduledTask scheduledTask) {
    completedTasks.incrementAndGet();
    scheduledTask.completion().complete(null);
  }

  private void failTask(ScheduledTask scheduledTask, Exception exception) {
    failedTasks.incrementAndGet();
    scheduledTask.completion().completeExceptionally(exception);
  }

  private void joinQuietly(Thread thread) {
    try {
      thread.join(TimeUnit.SECONDS.toMillis(1));
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
    }
  }

  private record ScheduledTask(LightweightTask task, CompletableFuture<Void> completion) {
  }

  private record ParkedTask(ScheduledTask scheduledTask, long resumeAtNanos) implements Delayed {

    private static ParkedTask after(ScheduledTask scheduledTask, long delayNanos) {
      return new ParkedTask(scheduledTask, System.nanoTime() + delayNanos);
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return unit.convert(resumeAtNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
      ParkedTask otherTask = (ParkedTask) other;
      return Long.compare(resumeAtNanos, otherTask.resumeAtNanos);
    }
  }
}
