package io.github.stellhub.threading;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ThreadingBenchmark {

  @Param({"100", "1000", "5000"})
  private int taskCount;

  @Param({"10"})
  private long blockingMillis;

  @Param({"200"})
  private int platformPoolSize;

  private ExecutorService platformExecutor;
  private ExecutorService virtualExecutor;

  /**
   * 初始化可复用的线程执行器。
   */
  @Setup
  public void setUp() {
    platformExecutor = Executors.newFixedThreadPool(platformPoolSize);
    virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
  }

  /**
   * 关闭每轮基准使用的线程执行器。
   */
  @TearDown
  public void tearDown() {
    platformExecutor.shutdownNow();
    virtualExecutor.shutdownNow();
  }

  /**
   * 使用固定大小的平台线程池执行阻塞任务。
   */
  @Benchmark
  public int platformThreads() throws Exception {
    return runBlockingTasks(platformExecutor);
  }

  /**
   * 使用每任务一个平台线程执行阻塞任务。
   */
  @Benchmark
  public int platformThreadPerTask() throws Exception {
    ExecutorService executor = Executors.newThreadPerTaskExecutor(
        Thread.ofPlatform().name("jmh-platform-task-", 0).factory());
    try {
      return runBlockingTasks(executor);
    } finally {
      executor.shutdownNow();
    }
  }

  /**
   * 使用每任务一个虚拟线程执行阻塞任务。
   */
  @Benchmark
  public int virtualThreads() throws Exception {
    return runBlockingTasks(virtualExecutor);
  }

  /**
   * 提交一批阻塞任务并等待所有任务完成。
   */
  private int runBlockingTasks(ExecutorService executor) throws Exception {
    List<Future<Integer>> futures = new ArrayList<>(taskCount);

    for (int i = 0; i < taskCount; i++) {
      futures.add(executor.submit(() -> {
        Thread.sleep(blockingMillis);
        return 1;
      }));
    }

    int completedTasks = 0;
    for (Future<Integer> future : futures) {
      completedTasks += future.get();
    }
    return completedTasks;
  }
}
