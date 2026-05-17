package io.github.stellhub.threading.scheduler;

import java.time.Duration;
import java.util.Objects;

public final class TaskDecision {

  private final TaskDecisionType type;
  private final long delayNanos;

  private TaskDecision(TaskDecisionType type, long delayNanos) {
    this.type = type;
    this.delayNanos = delayNanos;
  }

  /**
   * 当前任务已经完成，不需要再次调度。
   */
  public static TaskDecision complete() {
    return new TaskDecision(TaskDecisionType.COMPLETE, 0);
  }

  /**
   * 当前任务主动让出执行权，稍后继续进入就绪队列。
   */
  public static TaskDecision yieldNow() {
    return new TaskDecision(TaskDecisionType.YIELD, 0);
  }

  /**
   * 当前任务模拟阻塞等待，到期后重新进入就绪队列。
   */
  public static TaskDecision park(Duration delay) {
    Objects.requireNonNull(delay, "delay must not be null");
    if (delay.isNegative()) {
      throw new IllegalArgumentException("delay must not be negative");
    }
    return new TaskDecision(TaskDecisionType.PARK, delay.toNanos());
  }

  TaskDecisionType type() {
    return type;
  }

  long delayNanos() {
    return delayNanos;
  }

  enum TaskDecisionType {
    YIELD,
    PARK,
    COMPLETE
  }
}
