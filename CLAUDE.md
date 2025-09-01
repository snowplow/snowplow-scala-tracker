# Snowplow Scala Tracker - CLAUDE.md

## Project Overview

The Snowplow Scala Tracker is a client library for sending analytics events to Snowplow collectors. It provides a purely functional, typesafe API for tracking user interactions, page views, transactions, and custom events. The tracker uses tagless final pattern with higher-kinded types (`F[_]`) to support different effect types and maintains referential transparency throughout the codebase.

### Key Technologies
- **Scala**: 2.12.14, 2.13.6 (cross-compiled)
- **Cats/Cats Effect**: Core FP abstractions and effect management
- **Circe**: JSON encoding/decoding
- **Iglu**: Schema registry integration for self-describing JSONs
- **Http4s**: Modern HTTP client for CE3-compatible emitters
- **FS2**: Streaming for async event processing
- **Specs2/ScalaCheck**: Property-based testing

## Development Commands

```bash
# Compile all modules
sbt compile

# Run tests for all modules
sbt test

# Test specific module
sbt "project core" test
sbt "project http4sEmitter" test

# Format code
sbt scalafmtAll

# Check formatting
sbt scalafmtCheckAll

# Cross-compile for different Scala versions
sbt +compile
sbt +test

# Publish locally
sbt publishLocal

# Check binary compatibility
sbt mimaReportBinaryIssues
```

## Architecture

The tracker follows a modular, layered architecture with clear separation of concerns:

```
┌─────────────────────────────────────────────────┐
│                  Tracker[F]                      │  <- Main API entry point
├─────────────────────────────────────────────────┤
│                 Emitter[F]                       │  <- Event transmission layer
├─────────────────────────────────────────────────┤
│     Buffer     │    Payload    │    Subject     │  <- Core data structures
├─────────────────────────────────────────────────┤
│              Tracking[F] Typeclass               │  <- Effect abstraction
└─────────────────────────────────────────────────┘
```

### Module Organization
- **core**: Core tracking logic, data models, and abstractions
- **id-emitter**: Legacy IO-based emitter implementation
- **http4s-emitter**: Modern CE3-compatible emitter using http4s
- **metadata**: Cloud platform metadata enrichment (EC2, GCE)

## Core Architectural Principles

### 1. Tagless Final Pattern
```scala
// ✅ Abstract over effect type
class Tracker[F[_]: Monad: Tracking](...)

// ❌ Hardcoded effect type
class Tracker(...)  // Avoid concrete IO/Future
```

### 2. Immutable Data Structures
```scala
// ✅ Return new instance
def setUserId(id: String): Subject = 
  Subject(subjectInformation + ("uid" -> id))

// ❌ Mutate state
var userId: String = null  // Never use mutable fields
```

### 3. Referential Transparency
```scala
// ✅ Pure functions with F[_]
def generateUUID: F[UUID]
def getCurrentTimeMillis: F[Long]

// ❌ Side effects without F
UUID.randomUUID()  // Wrap in F[_]
```

### 4. Composition Over Inheritance
```scala
// ✅ Compose with typeclasses
implicit val tracking: Tracking[F] = ...

// ❌ Extend abstract classes
class MyTracker extends BaseTracker  // Avoid inheritance
```

## Layer Organization & Responsibilities

### Core Module (`modules/core/`)
- **Tracker**: Main API for tracking events
- **Emitter**: Abstract interface for event transmission
- **Payload**: Event data representation
- **Subject**: User/device information
- **Buffer**: Event batching logic
- **Tracking**: Effect typeclass for UUID/time generation

### Http4s Emitter Module (`modules/http4s-emitter/`)
- **Http4sEmitter**: CE3-compatible streaming emitter
- Queue-based async processing with fs2
- Configurable retry policies and buffering
- Backpressure handling

### ID Emitter Module (`modules/id-emitter/`)
- Legacy emitter using scalaj-http
- Synchronous and asynchronous variants
- Thread-based processing (being phased out)

## Critical Import Patterns

### Standard Imports
```scala
// ✅ Correct import order
import cats.Monad
import cats.implicits._
import cats.effect.{Async, Clock}
import io.circe.Json
import io.circe.syntax._
import com.snowplowanalytics.iglu.core.{SchemaKey, SelfDescribingData}
import com.snowplowanalytics.snowplow.scalatracker._

// ❌ Avoid wildcards for Java imports
import java.util._  // Import specific classes
```

### Package Object Aliases
```scala
// ✅ Use type aliases from package object
type SelfDescribingJson = SelfDescribingData[Json]

// ❌ Don't create duplicate aliases
type MyJson = Json  // Use standard types
```

## Essential Library Patterns

### Tracker Creation
```scala
// ✅ Proper tracker setup
implicit val tracking: Tracking[IO] = new Tracking[IO] {
  def getCurrentTimeMillis: IO[Long] = IO(System.currentTimeMillis())
  def generateUUID: IO[UUID] = IO(UUID.randomUUID())
}
val emitter = Http4sEmitter.build[IO](collector, client)
val tracker = Tracker[IO](emitter, "namespace", "appId")

// ❌ Missing implicits
val tracker = new Tracker(...)  // Won't compile
```

### Event Tracking
```scala
// ✅ Use for-comprehension for sequential tracking
for {
  _ <- tracker.trackPageView("https://example.com")
  _ <- tracker.trackStructEvent("category", "action")
  _ <- tracker.flushEmitters()
} yield ()

// ❌ Forget to handle F[_]
tracker.trackPageView("url")  // Returns F[Unit], must be run
```

### Self-Describing Events
```scala
// ✅ Use Iglu schemas properly
val event = SelfDescribingData(
  SchemaKey("com.acme", "event", "jsonschema", SchemaVer.Full(1, 0, 0)),
  Json.obj("key" := "value")
)
tracker.trackSelfDescribingEvent(event)

// ❌ Use deprecated string-based schemas
SelfDescribingJson("iglu:com.acme/event/1-0-0", json)
```

## Model Organization Pattern

### Event Payloads
```scala
// ✅ Use Payload builder pattern
Payload()
  .add("e", "pv")
  .add("url", pageUrl)
  .add("page", pageTitle)

// ❌ Manual map construction
Map("e" -> "pv", ...)  // Use Payload class
```

### Subjects
```scala
// ✅ Chain Subject modifications
Subject()
  .setUserId("user123")
  .setPlatform(Server)
  .setIpAddress("192.168.1.1")

// ❌ Mutate Subject fields
subject.userId = "user123"  // Subjects are immutable
```

## Common Pitfalls & Solutions

### 1. Effect Execution
```scala
// ❌ Creating effects without running them
val track = tracker.trackPageView("url")
// Event never sent!

// ✅ Run effects appropriately
tracker.trackPageView("url").unsafeRunSync()  // IO
tracker.trackPageView("url")  // Id (runs immediately)
```

### 2. Buffer Configuration
```scala
// ❌ No buffering for high-volume events
BufferConfig.NoBuffering  // Inefficient for many events

// ✅ Appropriate buffering
BufferConfig.Default  // 40KB payload size
BufferConfig.EventsCardinality(100)  // Batch by count
```

### 3. Retry Policy
```scala
// ❌ Retry forever without backoff
RetryPolicy.RetryForever  // Can cause queue buildup

// ✅ Bounded retries with exponential backoff
RetryPolicy.Default  // Max 10 attempts
RetryPolicy.MaxAttempts(5)  // Custom limit
```

### 4. Resource Management
```scala
// ❌ Manual resource handling
val emitter = Http4sEmitter.build(...).allocated.unsafeRunSync()._1

// ✅ Use Resource properly
Http4sEmitter.build[IO](...).use { emitter =>
  // Use emitter
}
```

## File Structure Template

```
modules/
├── core/
│   └── src/
│       ├── main/scala/com.snowplowanalytics.snowplow/scalatracker/
│       │   ├── Tracker.scala         # Main API
│       │   ├── Emitter.scala        # Emitter trait & ADTs
│       │   ├── Payload.scala        # Event data
│       │   ├── Subject.scala        # User data
│       │   ├── Buffer.scala         # Batching logic
│       │   ├── Tracking.scala       # Effect typeclass
│       │   ├── package.scala        # Type aliases
│       │   └── utils/
│       │       ├── ErrorTracking.scala
│       │       └── JsonUtils.scala
│       └── test/scala/
├── http4s-emitter/
│   └── src/main/scala/.../Http4sEmitter.scala
├── id-emitter/
│   └── src/main/scala/.../
└── metadata/
    └── src/main/scala/.../
```

## Testing Framework & Patterns

### Test Directory Structure
```
modules/
├── core/src/test/scala/com.snowplowanalytics.snowplow.scalatracker/
│   ├── BufferSpec.scala      # Buffer state machine tests
│   ├── EmitterSpec.scala     # Emitter abstraction tests  
│   ├── PayloadSpec.scala     # Payload construction & serialization
│   ├── TrackerSpec.scala     # Main tracker API behaviors
│   └── syntax.scala          # Id monad helpers for testing
├── http4s-emitter/src/test/scala/.../Http4sEmitterSpec.scala
├── id-emitter/src/test/scala/.../
│   ├── BatchEmitterSpec.scala  # Batch emitter logic
│   └── StressTest.scala        # Performance testing (only *Test.scala)
└── metadata/src/test/scala/.../MetadataSpec.scala
```

### Specs2 Test Structure
```scala
// ✅ Standard Spec naming and structure
class TrackerSpec extends Specification {
  "methodName" should {
    "behavior description" in new TestScope {
      result must beEqualTo(expected)
    }
  }
}

// ❌ Wrong naming convention
class TrackerTest extends AnyFunSuite  // Use *Spec.scala
```

### Test Fixture Pattern with Scope
```scala
// ✅ Reusable test fixtures extending Scope
trait DummyTracker extends Scope {
  implicit val idTracking: Tracking[Id] = syntax.id.idTracking
  val emitter = new TestEmitter[Id]
  val tracker = new Tracker(NonEmptyList.one(emitter), "ns", "app")
}

// ❌ Duplicate setup in each test
"test" in {
  val emitter = new TestEmitter[Id]  // Repeated
  val tracker = new Tracker(...)      // in every test
}
```

### Id Monad for Synchronous Testing
```scala
// ✅ Use cats.Id for deterministic tests
import cats.Id
import com.snowplowanalytics.snowplow.scalatracker.syntax.id._

implicit val idTracking: Tracking[Id] = new Tracking[Id] {
  def getCurrentTimeMillis: Long = 1234567890L
  def generateUUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
}

// ❌ Test with IO unnecessarily
tracker.trackPageView(url).unsafeRunSync()  // Overkill for unit tests
```

### Clean Mock Pattern
```scala
// ✅ Simple test emitter with behavior capture
class TestEmitter[F[_]: Applicative] extends Emitter[F] {
  var lastInput: Map[String, String] = Map.empty
  var allInputs: List[Map[String, String]] = Nil
  def send(p: Payload): F[Unit] = {
    lastInput = p.nvPairs
    allInputs = p.nvPairs :: allInputs
    ().pure[F]
  }
}

// ❌ Complex mocking frameworks
mock[Emitter[F]]  // Difficult with higher-kinded types
```

### JSON Validation with Circe Optics
```scala
// ✅ Use optics for clean JSON assertions
import io.circe.optics.JsonPath._
root.data.schema.string.getOption(json) must beSome("iglu:com.snowplow/event/1-0-0")
root.data.data.userId.string.getOption(json) must beSome("user123")

// ❌ Manual cursor navigation
json.hcursor.downField("data").downField("schema").as[String]
```

### ScalaCheck Property Testing
```scala
// ✅ Generate realistic test data with generators
implicit val payloadArb: Arbitrary[Payload] = Arbitrary {
  Gen.mapOf(Gen.alphaStr.map(k => (k, Gen.alphaStr.sample.get)))
    .map(Payload(_))
}
prop("payload serialization round-trip") = forAll { (p: Payload) =>
  decode[Payload](p.asJson.noSpaces) == Right(p)
}

// ❌ Only example-based tests
"test with payload1" in { ... }  // Miss edge cases
```

### Regex Pattern Validation
```scala
// ✅ Validate UUID and timestamp formats
emitter.lastInput("eid") must beMatching(
  "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
)
emitter.lastInput("dtm") must beMatching("\\d{13}")  // Millisecond timestamp

// ❌ Exact value matching for generated data
emitter.lastInput("eid") must beEqualTo("specific-uuid")  // Too brittle
```

### Resource Testing for Http4s
```scala
// ✅ Proper IO resource testing with cleanup
Http4sEmitter.build[IO](endpoint, client).use { emitter =>
  for {
    tracker <- IO(new Tracker(NonEmptyList.one(emitter), "ns", "app"))
    _ <- tracker.trackPageView("http://example.com")
    _ <- tracker.flushEmitters()
    // Assertions within resource scope
  } yield ()
}.unsafeRunSync()

// ❌ Manual resource management
val (emitter, release) = Http4sEmitter.build(...).allocated.unsafeRunSync()
```

### Module-Specific Testing Patterns
**Core Module**: Focus on Id monad, pure functions, schema validation
**Http4s-Emitter**: Test IO effects, queue behavior, resource cleanup
**ID-Emitter**: Legacy tests, thread safety, batch processing
**Metadata**: HTTP client mocking, timeout scenarios

### Testing Best Practices
✅ **DOs:**
- Use `*Spec.scala` naming (except StressTest.scala)
- Create `trait TestScope extends Scope` for fixtures
- Use `Id` monad for synchronous unit tests
- Test JSON structure with Circe optics
- Use ScalaCheck for property-based testing
- Keep tests focused and atomic
- Test error scenarios and edge cases

❌ **DON'Ts:**
- Don't use `*Test.scala` (except performance tests)
- Don't mix effect types unnecessarily in unit tests
- Don't test implementation details, focus on behavior
- Don't ignore resource cleanup in IO tests
- Don't use complex mocking frameworks with F[_]

## Quick Reference

### Event Types Checklist
- [ ] `trackPageView` - Page/screen views
- [ ] `trackStructEvent` - Structured events (category/action)
- [ ] `trackSelfDescribingEvent` - Custom schema events
- [ ] `trackTransaction` - E-commerce transactions
- [ ] `trackTransactionItem` - Transaction line items
- [ ] `trackAddToCart` - Add to cart events
- [ ] `trackRemoveFromCart` - Remove from cart events
- [ ] `trackError` - Application errors

### Emitter Configuration Options
- [ ] `BufferConfig` - Event batching strategy
- [ ] `RetryPolicy` - Failed request retry behavior
- [ ] `EventQueuePolicy` - Queue overflow handling
- [ ] `Callback` - Success/failure callbacks
- [ ] `shutdownTimeout` - Graceful shutdown timeout

### Subject Properties
- [ ] `userId` - User identifier
- [ ] `domainUserId` - Domain-specific user ID
- [ ] `networkUserId` - Network user ID
- [ ] `ipAddress` - User IP address
- [ ] `useragent` - User agent string
- [ ] `platform` - Platform (server, web, mobile)
- [ ] `screenResolution` - Device screen resolution
- [ ] `viewport` - Browser viewport size
- [ ] `timezone` - User timezone
- [ ] `lang` - User language

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