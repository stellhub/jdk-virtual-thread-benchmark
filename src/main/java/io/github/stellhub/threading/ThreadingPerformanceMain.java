package io.github.stellhub.threading;

import io.github.stellhub.threading.scheduler.LightweightTaskScheduler;
import io.github.stellhub.threading.scheduler.TaskDecision;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ThreadingPerformanceMain {

  public static void main(String[] args) throws Exception {
    BenchmarkConfig config = BenchmarkConfig.from(args);
    List<BenchmarkResult> results = List.of(
        runLightweightScheduler(config),
        runFixedPlatformThreadPool(config),
        runPlatformThreadPerTask(config),
        runVirtualThreadPerTask(config)
    );

    printConfig(config);
    printResults(results);
  }

  /**
   * 使用教学版轻量调度器运行模拟阻塞任务。
   */
  private static BenchmarkResult runLightweightScheduler(BenchmarkConfig config) {
    long startedAt = System.nanoTime();
    try (LightweightTaskScheduler scheduler = new LightweightTaskScheduler(config.schedulerCarrierThreads())) {
      List<CompletableFuture<Void>> futures = new ArrayList<>(config.taskCount());
      for (int i = 0; i < config.taskCount(); i++) {
        futures.add(scheduler.submit(new OneParkLightweightTask(config.blockingMillis())));
      }
      CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
      return BenchmarkResult.from("教学轻量调度器", scheduler.completedTasks(), startedAt);
    }
  }

  /**
   * 使用固定大小的平台线程池运行真实阻塞任务。
   */
  private static BenchmarkResult runFixedPlatformThreadPool(BenchmarkConfig config) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(config.platformPoolSize());
    try {
      return runExecutorBenchmark("传统Thread固定线程池", executor, config);
    } finally {
      shutdownExecutor(executor);
    }
  }

  /**
   * 使用每任务一个平台线程的方式运行真实阻塞任务。
   */
  private static BenchmarkResult runPlatformThreadPerTask(BenchmarkConfig config) throws Exception {
    ExecutorService executor = Executors.newThreadPerTaskExecutor(
        Thread.ofPlatform().name("platform-task-", 0).factory());
    try {
      return runExecutorBenchmark("传统Thread每任务一个线程", executor, config);
    } finally {
      shutdownExecutor(executor);
    }
  }

  /**
   * 使用每任务一个虚拟线程的方式运行真实阻塞任务。
   */
  private static BenchmarkResult runVirtualThreadPerTask(BenchmarkConfig config) throws Exception {
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      return runExecutorBenchmark("VirtualThread每任务一个线程", executor, config);
    } finally {
      shutdownExecutor(executor);
    }
  }

  private static BenchmarkResult runExecutorBenchmark(
      String scenario,
      ExecutorService executor,
      BenchmarkConfig config
  ) throws Exception {
    long startedAt = System.nanoTime();
    List<Future<Integer>> futures = new ArrayList<>(config.taskCount());
    for (int i = 0; i < config.taskCount(); i++) {
      futures.add(executor.submit(() -> {
        Thread.sleep(config.blockingMillis());
        return 1;
      }));
    }

    long completedTasks = 0;
    for (Future<Integer> future : futures) {
      completedTasks += future.get();
    }
    return BenchmarkResult.from(scenario, completedTasks, startedAt);
  }

  private static void shutdownExecutor(ExecutorService executor) throws InterruptedException {
    executor.shutdown();
    if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
      executor.shutdownNow();
    }
  }

  private static void printConfig(BenchmarkConfig config) {
    System.out.println("运行参数");
    System.out.printf("任务数量: %,d%n", config.taskCount());
    System.out.printf("模拟阻塞: %,d ms%n", config.blockingMillis());
    System.out.printf("固定平台线程池大小: %,d%n", config.platformPoolSize());
    System.out.printf("轻量调度器载体线程数: %,d%n", config.schedulerCarrierThreads());
    System.out.println();
  }

  private static void printResults(List<BenchmarkResult> results) {
    System.out.println("性能结果");
    System.out.printf("%-28s %12s %14s %18s %16s%n",
        "场景", "完成任务", "耗时(ms)", "吞吐量(tasks/s)", "平均耗时(us)");
    for (BenchmarkResult result : results) {
      System.out.printf(Locale.ROOT, "%-28s %,12d %,14.3f %,18.2f %,16.2f%n",
          result.scenario(),
          result.completedTasks(),
          result.elapsedMillis(),
          result.throughputPerSecond(),
          result.averageMicrosPerTask());
    }
  }

  private static class OneParkLightweightTask implements io.github.stellhub.threading.scheduler.LightweightTask {

    private final long blockingMillis;
    private boolean parked;

    private OneParkLightweightTask(long blockingMillis) {
      this.blockingMillis = blockingMillis;
    }

    /**
     * 第一次执行时模拟挂起，第二次被恢复时完成任务。
     */
    @Override
    public TaskDecision run() {
      if (!parked) {
        parked = true;
        return TaskDecision.park(Duration.ofMillis(blockingMillis));
      }
      return TaskDecision.complete();
    }
  }

  private record BenchmarkConfig(
      int taskCount,
      long blockingMillis,
      int platformPoolSize,
      int schedulerCarrierThreads
  ) {

    private static BenchmarkConfig from(String[] args) {
      Map<String, String> options = parseOptions(args);
      int processors = Runtime.getRuntime().availableProcessors();
      return new BenchmarkConfig(
          intOption(options, "tasks", 1_000),
          longOption(options, "blockingMillis", 10),
          intOption(options, "platformPoolSize", 200),
          intOption(options, "schedulerCarriers", Math.max(1, processors))
      );
    }

    private static Map<String, String> parseOptions(String[] args) {
      return List.of(args).stream()
          .filter(argument -> argument.startsWith("--"))
          .map(argument -> argument.substring(2).split("=", 2))
          .filter(parts -> parts.length == 2)
          .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (left, right) -> right));
    }

    private static int intOption(Map<String, String> options, String name, int defaultValue) {
      return Integer.parseInt(options.getOrDefault(name, String.valueOf(defaultValue)));
    }

    private static long longOption(Map<String, String> options, String name, long defaultValue) {
      return Long.parseLong(options.getOrDefault(name, String.valueOf(defaultValue)));
    }
  }

  private record BenchmarkResult(
      String scenario,
      long completedTasks,
      double elapsedMillis,
      double throughputPerSecond,
      double averageMicrosPerTask
  ) {

    private static BenchmarkResult from(String scenario, long completedTasks, long startedAt) {
      long elapsedNanos = System.nanoTime() - startedAt;
      double elapsedMillis = elapsedNanos / 1_000_000.0;
      double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
      double throughput = completedTasks / elapsedSeconds;
      double averageMicros = elapsedNanos / 1_000.0 / completedTasks;
      return new BenchmarkResult(scenario, completedTasks, elapsedMillis, throughput, averageMicros);
    }
  }
}
