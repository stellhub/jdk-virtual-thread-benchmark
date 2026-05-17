package io.github.stellhub.threading.scheduler;

@FunctionalInterface
public interface LightweightTask {

  /**
   * 执行任务的一小步，并告诉调度器下一步应该如何处理该任务。
   */
  TaskDecision run() throws Exception;
}
