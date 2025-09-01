# Http4s Emitter Module - CLAUDE.md

## Module Overview

The Http4s Emitter is the modern, Cats Effect 3 compatible implementation for sending events to Snowplow collectors. It uses fs2 streams for async processing, http4s for HTTP communication, and provides sophisticated queue management with backpressure handling. This is the recommended emitter for new projects using Cats Effect.

### Key Technologies
- **Http4s**: Pure functional HTTP client
- **FS2**: Functional streams for Scala
- **Cats Effect 3**: Modern effect system with Resource management
- **SLF4J**: Logging abstraction

## Architecture

The Http4s Emitter uses a queue-based architecture with stream processing:

```
Event → Queue[F, Action] → Stream Processing → HTTP Client → Collector
           ↓                      ↓                ↓
    Queue Policies         Retry Logic      Batch Formation
```

### Core Components
- **Queue**: Bounded/unbounded queue for event buffering
- **Stream Processor**: FS2 stream that drains the queue
- **HTTP Client**: Http4s client for sending requests
- **Buffer**: Event batching before transmission

## Http4s Emitter Patterns

### Resource-Based Construction
```scala
// ✅ Proper resource management
Http4sEmitter.build[IO](
  collector = EndpointParams("collector.example.com", None, true),
  client = httpClient,
  bufferConfig = BufferConfig.Default
).use { emitter =>
  // Use emitter within resource scope
}

// ❌ Manual allocation
val emitter = Http4sEmitter.build(...).allocated.unsafeRunSync()._1
```

### Queue Policy Configuration
```scala
// ✅ Choose appropriate queue policy
EventQueuePolicy.BlockWhenFull(10000)     // Block when queue full
EventQueuePolicy.IgnoreWhenFull(10000)    // Drop new events silently
EventQueuePolicy.ErrorWhenFull(10000)     // Throw exception

// ❌ Unbounded queue for high volume
EventQueuePolicy.UnboundedQueue  // Can cause OOM
```

### Callback Implementation
```scala
// ✅ Implement callbacks for monitoring
val callback: Callback[IO] = (endpoint, request, result) =>
  result match {
    case Result.Success(code) => 
      IO(logger.debug(s"Sent ${request.attempt} to $endpoint"))
    case Result.Failure(code) =>
      IO(logger.warn(s"Failed with $code"))
    case Result.TrackerFailure(e) =>
      IO(logger.error("Network failure", e))
  }

// ❌ Side effects in callbacks
(_, _, result) => println(result)  // Must return F[Unit]
```

## Stream Processing Internals

### Queue Draining Logic
```scala
// ✅ Understanding the stream pipeline
Stream.repeatEval(queue.take)
  .through(bufferPipe)      // Batch events
  .through(sendPipe)        // Send to collector
  .through(retryPipe)       // Handle retries
  .compile.drain

// ❌ Don't try to manually process queue
while (true) { queue.take... }  // Use streams
```

### Buffer Pipe Pattern
```scala
// ✅ Stateful stream transformation
def bufferPipe[F[_]]: Pipe[F, Action, Request] =
  _.evalMapAccumulate(Buffer(config)) { (buffer, action) =>
    val (newBuffer, request) = buffer.handle(action)
    F.pure((newBuffer, request))
  }
```

## HTTP Request Formation

### POST Request Construction
```scala
// ✅ Proper request building
HttpRequest[F](
  method = Method.POST,
  uri = Uri.unsafeFromString(collector.getPostUri)
).withEntity(payloadData)
  .withHeaders(`Content-Type`(MediaType.application.json))

// ❌ Manual string concatenation
s"$host:$port/com.snowplowanalytics.snowplow/tp2"
```

### GET Request Parameters
```scala
// ✅ Use http4s URI builder
uri.withQueryParams(payload.toMap)

// ❌ Manual query string building
s"$uri?${params.mkString("&")}"  // Encoding issues
```

## Retry Logic Implementation

### Exponential Backoff
```scala
// ✅ Proper retry with backoff
for {
  seed <- Random[F].nextDouble
  delay = RetryPolicy.getDelay(attempt, seed)
  _ <- Temporal[F].sleep(delay.millis)
  result <- sendRequest(request.updateAttempt)
} yield result

// ❌ Fixed delay retry
Thread.sleep(1000)  // Blocks thread
```

### Retry Policy Handling
```scala
// ✅ Check retry limits
if (request.isFailed(retryPolicy))
  F.pure(Result.RetriesExceeded(lastResult))
else
  retry(request.updateAttempt)

// ❌ Infinite retries without checking
retry(request)  // Can retry forever
```

## Resource Lifecycle Management

### Graceful Shutdown
```scala
// ✅ Shutdown with timeout
def shutdown[F[_]: Temporal](
  fiber: Fiber[F, Throwable, Unit],
  queue: Queue[F, Action],
  timeout: Option[FiniteDuration]
): F[Unit] = for {
  _ <- queue.offer(Action.Terminate)
  _ <- timeout.fold(fiber.join)(t => 
    fiber.joinWithNever.timeout(t).attempt.void
  )
} yield ()

// ❌ Abrupt termination
fiber.cancel  // Loses pending events
```

### Resource Composition
```scala
// ✅ Compose resources properly
for {
  client <- EmberClientBuilder.default[IO].build
  emitter <- Http4sEmitter.build[IO](collector, client)
} yield emitter

// ❌ Nested resource allocation
Resource.eval(
  EmberClientBuilder.default[IO].build.use { client =>
    // Wrong! Client released too early
  }
)
```

## Error Handling Patterns

### Network Failures
```scala
// ✅ Handle connection errors
client.run(request).attempt.map {
  case Right(response) if response.status.isSuccess =>
    Result.Success(response.status.code)
  case Right(response) =>
    Result.Failure(response.status.code)
  case Left(throwable) =>
    Result.TrackerFailure(throwable)
}

// ❌ Unhandled exceptions
client.run(request).map(_.status)  // Can throw
```

### Queue Overflow
```scala
// ✅ Handle queue full scenarios
queuePolicy match {
  case EventQueuePolicy.ErrorWhenFull(limit) =>
    queue.tryOffer(action).flatMap {
      case false => F.raiseError(new EventQueueException(limit))
      case true => F.unit
    }
}

// ❌ Ignore queue limits
queue.offer(action)  // Blocks indefinitely
```

## Performance Optimization

### Batching Strategy
```scala
// ✅ Efficient batching
BufferConfig.PayloadSize(40000)      // Batch by size
BufferConfig.EventsCardinality(100)  // Batch by count
BufferConfig.OneOf(size, count)      // Either condition

// ❌ No batching for high volume
BufferConfig.NoBuffering  // One request per event
```

### Connection Pooling
```scala
// ✅ Configure connection pool
EmberClientBuilder.default[IO]
  .withMaxTotal(100)
  .withMaxPerKey(10)
  .build

// ❌ New connection per request
client.withConnection  // Inefficient
```

## Testing Http4s Emitter

### Test Directory Structure
```
http4s-emitter/src/test/scala/com.snowplowanalytics.snowplow.scalatracker.emitters.http4s/
└── Http4sEmitterSpec.scala  # All Http4s emitter tests
```

### Mock HTTP Client Pattern
```scala
// ✅ Test with deterministic mock client
val mockClient = Client[IO] { req =>
  capturedRequests.update(_ :+ req) >>
  Resource.pure(Response[IO](Status.Ok))
}
val emitter = Http4sEmitter.build[IO](endpoint, mockClient)

// ❌ Test against real collector
val client = EmberClientBuilder.default[IO].build  // Flaky tests
```

### Queue Policy Testing
```scala
// ✅ Test all queue overflow behaviors
"ErrorWhenFull" should "throw when queue full" in {
  Http4sEmitter.build[IO](
    queuePolicy = EventQueuePolicy.ErrorWhenFull(1)
  ).use { emitter =>
    emitter.send(payload1) >> 
    emitter.send(payload2).attempt.map(_ must beLeft)
  }
}

// ❌ Only test happy path
emitter.send(payload)  // Don't test queue limits
```

### Resource Lifecycle Testing
```scala
// ✅ Test proper resource cleanup
"shutdown gracefully" in {
  val test = for {
    fiber <- Ref[IO].of(Option.empty[Fiber[IO, Throwable, Unit]])
    emitter <- Http4sEmitter.build[IO](
      endpoint, client,
      shutdownTimeout = Some(5.seconds)
    )
    _ <- emitter.send(payload)
    // Resource automatically cleaned up
  } yield ()
  test.use(_ => IO.unit).unsafeRunSync()
}

// ❌ Manual fiber management
val fiber = stream.compile.drain.start.unsafeRunSync()
```

### Buffer Behavior Testing
```scala
// ✅ Test buffer accumulation and flushing
"batch events by size" in {
  var sentBatches = List.empty[Request]
  val client = mockClientCapturing(sentBatches)
  Http4sEmitter.build[IO](
    bufferConfig = BufferConfig.PayloadSize(1000)
  ).use { emitter =>
    for {
      _ <- emitter.send(smallPayload)  // Buffered
      _ <- IO(sentBatches must beEmpty)
      _ <- emitter.send(largePayload)  // Triggers send
      _ <- IO(sentBatches must haveSize(1))
    } yield ()
  }
}

// ❌ Don't verify batching behavior
emitter.send(p1) >> emitter.send(p2)  // Is it batched?
```

### Retry Logic Testing
```scala
// ✅ Test exponential backoff retries
"retry with backoff on failure" in {
  val failThenSucceed = Ref[IO].of(0).flatMap { counter =>
    Client[IO] { req =>
      counter.modify(c => (c + 1, c)).flatMap {
        case 0 => Resource.pure(Response[IO](Status.InternalServerError))
        case _ => Resource.pure(Response[IO](Status.Ok))
      }
    }
  }
  Http4sEmitter.build[IO](
    retryPolicy = RetryPolicy.MaxAttempts(3)
  ).use(_.send(payload)).unsafeRunSync() must not(throwA[Exception])
}

// ❌ Don't test retry scenarios
val client = alwaysSucceedsClient  // Too optimistic
```

### Callback Testing
```scala
// ✅ Verify callbacks are invoked correctly
"invoke callbacks on success and failure" in {
  val results = Ref[IO].of(List.empty[Result]).unsafeRunSync()
  val callback: Callback[IO] = (_, _, result) => 
    results.update(_ :+ result)
  
  Http4sEmitter.build[IO](callback = Some(callback)).use { emitter =>
    for {
      _ <- emitter.send(payload)
      _ <- emitter.flushEmitters()
      res <- results.get
      _ <- IO(res must contain(Result.Success(200)))
    } yield ()
  }
}

// ❌ Ignore callback testing
Http4sEmitter.build[IO](endpoint, client)  // No verification
```

### Concurrent Send Testing
```scala
// ✅ Test concurrent event sending
"handle concurrent sends safely" in {
  Http4sEmitter.build[IO](endpoint, client).use { emitter =>
    val sends = (1 to 100).map(i => 
      emitter.send(Payload().add("id", i.toString))
    ).toList
    sends.parSequence.map(_ => succeed)
  }
}

// ❌ Only test sequential sends
emitter.send(p1) >> emitter.send(p2)  // Not realistic
```

### Error Recovery Testing
```scala
// ✅ Test queue behavior on network errors
"continue after transient failures" in {
  val unstableClient = randomlyFailingClient(0.3)
  Http4sEmitter.build[IO](
    client = unstableClient,
    queuePolicy = EventQueuePolicy.BlockWhenFull(100)
  ).use { emitter =>
    (1 to 10).map(_ => emitter.send(payload)).toList
      .sequence
      .map(_ must not(throwA[Exception]))
  }
}

// ❌ Assume network is always reliable
val client = perfectClient  // Unrealistic
```

### Stream Processing Testing
```scala
// ✅ Test internal stream behavior
"process queue items in order" in {
  val receivedOrder = Ref[IO].of(List.empty[Int]).unsafeRunSync()
  val client = orderCapturingClient(receivedOrder)
  Http4sEmitter.build[IO](client = client).use { emitter =>
    for {
      _ <- (1 to 5).map(i => 
        emitter.send(Payload().add("order", i.toString))
      ).toList.sequence
      _ <- emitter.flushEmitters()
      order <- receivedOrder.get
      _ <- IO(order must beEqualTo(List(1,2,3,4,5)))
    } yield ()
  }
}
```

### Test Utilities for Http4s
```scala
// ✅ Reusable test utilities
object Http4sTestUtils {
  def captureClient(captured: Ref[IO, List[Request[IO]]]): Client[IO] =
    Client { req => 
      captured.update(_ :+ req) >> Resource.pure(Response[IO](Status.Ok))
    }
    
  def failingClient(status: Status): Client[IO] = 
    Client(_ => Resource.pure(Response[IO](status)))
    
  def delayedClient(delay: FiniteDuration): Client[IO] =
    Client { req =>
      IO.sleep(delay) >> Resource.pure(Response[IO](Status.Ok))
    }
}

// ❌ Inline client creation in every test
val client = Client[IO] { ... }  // Duplication
```

### Testing Best Practices for Http4s
✅ **Http4s Testing DOs:**
- Test with mock `Client[IO]` for determinism
- Verify queue overflow behaviors
- Test resource cleanup with `.use`
- Check retry logic with failing clients
- Test concurrent sends with `.parSequence`
- Verify callback invocations
- Test buffer accumulation and flushing

❌ **Http4s Testing DON'Ts:**
- Don't test against real HTTP endpoints
- Don't ignore resource lifecycle
- Don't skip testing error scenarios
- Don't manually manage fibers
- Don't assume perfect network conditions

## Common Issues & Solutions

### Issue: Events Not Sending
```scala
// ❌ Forgetting to start the stream
Http4sEmitter.build(...).allocated  // Stream not running

// ✅ Use Resource properly
Http4sEmitter.build(...).use { emitter =>
  // Stream starts automatically
}
```

### Issue: Memory Leak
```scala
// ❌ Unbounded queue with slow collector
EventQueuePolicy.UnboundedQueue

// ✅ Bounded queue with overflow strategy
EventQueuePolicy.BlockWhenFull(10000)
```

### Issue: Lost Events on Shutdown
```scala
// ❌ No shutdown timeout
shutdownTimeout = None  // Waits forever

// ✅ Reasonable timeout
shutdownTimeout = Some(30.seconds)
```

## Configuration Reference

### BufferConfig Options
- `NoBuffering`: Send immediately
- `EventsCardinality(n)`: Buffer n events
- `PayloadSize(bytes)`: Buffer until size reached
- `OneOf(a, b)`: Either condition triggers send

### EventQueuePolicy Options
- `UnboundedQueue`: No limit (danger!)
- `BlockWhenFull(limit)`: Block tracker thread
- `IgnoreWhenFull(limit)`: Silent drop
- `ErrorWhenFull(limit)`: Throw exception

### RetryPolicy Options
- `RetryForever`: Never give up
- `MaxAttempts(n)`: Stop after n attempts
- `NoRetry`: MaxAttempts(1)

## Migration from ID Emitter

### Key Differences
```scala
// ID Emitter (old)
import com.snowplowanalytics.snowplow.scalatracker.emitters.id._
AsyncEmitter.createAndStart(endpoint)

// Http4s Emitter (new)
import com.snowplowanalytics.snowplow.scalatracker.emitters.http4s._
Http4sEmitter.build[IO](endpoint, client).use { ... }
```

### Feature Comparison
| Feature | ID Emitter | Http4s Emitter |
|---------|------------|----------------|
| Effect Type | `Id` only | Any `F[_]: Async` |
| Streaming | No | Yes (FS2) |
| Resource Safety | Manual | Automatic |
| Backpressure | Limited | Full support |
| CE3 Compatible | No | Yes |

## Contributing to CLAUDE.md

When adding or updating content in this document, please follow these guidelines:

### File Size Limit
- **CLAUDE.md must not exceed 40KB** (currently ~19KB)
- Check file size after updates: `wc -c CLAUDE.md`
- Remove outdated content if approaching the limit

### Code Examples
- Keep all code examples **4 lines or fewer**
- Focus on the essential pattern, not complete implementations
- Use `// ❌` and `// ✅` to clearly show wrong vs right approaches

### Content Organization
- Add new patterns to existing sections when possible
- Create new sections sparingly to maintain structure
- Update the architectural principles section for major changes
- Ensure examples follow current codebase conventions

### Quality Standards
- Test any new patterns in actual code before documenting
- Verify imports and syntax are correct for the codebase
- Keep language concise and actionable
- Focus on "what" and "how", minimize "why" explanations

### Multiple CLAUDE.md Files
- **Directory-specific CLAUDE.md files** can be created for specialized modules
- Follow the same structure and guidelines as this root CLAUDE.md
- Keep them focused on directory-specific patterns and conventions
- Maximum 20KB per directory-specific CLAUDE.md file

### Instructions for LLMs
When editing files in this repository, **always check for CLAUDE.md guidance**:

1. **Look for CLAUDE.md in the same directory** as the file being edited
2. **If not found, check parent directories** recursively up to project root
3. **Follow the patterns and conventions** described in the applicable CLAUDE.md
4. **Prioritize directory-specific guidance** over root-level guidance when conflicts exist