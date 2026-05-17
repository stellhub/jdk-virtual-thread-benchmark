# JDK Virtual Thread Benchmark

JMH benchmarks for comparing JDK virtual threads and platform threads under blocking workloads.

The initial benchmark focuses on a common server-side pattern: running many short blocking tasks concurrently. It compares a bounded platform-thread pool with a virtual-thread-per-task executor.

## Requirements

- JDK 25 or later
- Maven 3.9 or later

## Build

```bash
mvn clean package
```

## Run

Run the teaching scheduler and thread comparison main:

```bash
mvn exec:java
```

Customize the workload:

```bash
mvn exec:java "-Dexec.args=--tasks=1000 --blockingMillis=10 --platformPoolSize=200 --schedulerCarriers=8"
```

The main program prints completed tasks, elapsed time, throughput, and average task cost for:

- A teaching lightweight scheduler
- A fixed platform-thread pool
- A platform-thread-per-task executor
- A virtual-thread-per-task executor

Run the JMH benchmarks:

```bash
java -jar target/benchmarks.jar
```

Run a smaller smoke benchmark:

```bash
java -jar target/benchmarks.jar ThreadingBenchmark -p taskCount=100 -wi 1 -i 2 -f 1
```

## Benchmark Design

- `platformThreads` uses `Executors.newFixedThreadPool(platformPoolSize)`.
- `platformThreadPerTask` uses `Executors.newThreadPerTaskExecutor(Thread.ofPlatform().factory())`.
- `virtualThreads` uses `Executors.newVirtualThreadPerTaskExecutor()`.
- Each benchmark invocation submits `taskCount` blocking tasks.
- Each task sleeps for `blockingMillis` and returns a small result to prevent dead-code elimination.

The default parameters intentionally model blocking I/O pressure rather than CPU-bound throughput. Virtual threads are designed to improve scalability for blocking workloads, while CPU-bound workloads should still be sized around available processors.

## Teaching Scheduler

The project also contains a small cooperative scheduler under `io.github.stellhub.threading.scheduler`.

It is intentionally simple:

- `LightweightTask` represents a task that runs one small step at a time.
- `TaskDecision.complete()` marks the task as finished.
- `TaskDecision.yieldNow()` puts the task back into the ready queue.
- `TaskDecision.park(Duration)` simulates a blocking wait and resumes the task later.
- `LightweightTaskScheduler` uses a small number of platform carrier threads to run ready tasks.

This scheduler is useful for understanding scheduling ideas, but it is not a real Java virtual thread implementation. Real virtual threads depend on JVM and JDK internals such as continuation support, stack mounting and unmounting, and integration with blocking JDK APIs.

## Parameters

| Parameter | Default values | Description |
| --- | --- | --- |
| `taskCount` | `100`, `1000`, `5000` | Number of blocking tasks submitted per benchmark invocation. |
| `blockingMillis` | `10` | Sleep duration for each task. |
| `platformPoolSize` | `200` | Maximum number of platform threads in the fixed pool. |

## Notes

JMH results depend on hardware, operating system, JDK version, power settings, and background load. Compare results on the same machine and JVM when evaluating virtual threads against platform threads.
