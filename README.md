# JDK Virtual Thread Benchmark

JMH benchmarks for comparing JDK virtual threads and platform threads under blocking workloads.

The initial benchmark focuses on a common server-side pattern: running many short blocking tasks concurrently. It compares a bounded platform-thread pool with a virtual-thread-per-task executor.

## Requirements

- JDK 21 or later
- Maven 3.9 or later

## Build

```bash
mvn clean package
```

## Run

```bash
java -jar target/benchmarks.jar
```

Run a smaller smoke benchmark:

```bash
java -jar target/benchmarks.jar ThreadingBenchmark -p taskCount=100 -wi 1 -i 2 -f 1
```

## Benchmark Design

- `platformThreads` uses `Executors.newFixedThreadPool(platformPoolSize)`.
- `virtualThreads` uses `Executors.newVirtualThreadPerTaskExecutor()`.
- Each benchmark invocation submits `taskCount` blocking tasks.
- Each task sleeps for `blockingMillis` and returns a small result to prevent dead-code elimination.

The default parameters intentionally model blocking I/O pressure rather than CPU-bound throughput. Virtual threads are designed to improve scalability for blocking workloads, while CPU-bound workloads should still be sized around available processors.

## Parameters

| Parameter | Default values | Description |
| --- | --- | --- |
| `taskCount` | `100`, `1000`, `5000` | Number of blocking tasks submitted per benchmark invocation. |
| `blockingMillis` | `10` | Sleep duration for each task. |
| `platformPoolSize` | `200` | Maximum number of platform threads in the fixed pool. |

## Notes

JMH results depend on hardware, operating system, JDK version, power settings, and background load. Compare results on the same machine and JVM when evaluating virtual threads against platform threads.
