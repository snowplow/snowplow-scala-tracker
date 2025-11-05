# Core Module - CLAUDE.md

## Module Overview

The Core module provides the fundamental abstractions and data structures for the Snowplow Scala Tracker. It defines the main Tracker API, event payload structures, buffering logic, and the tagless final abstractions that allow the tracker to work with any effect type. This module is dependency-free from specific HTTP implementations, maintaining pure functional programming principles throughout.

### Key Components
- **Tracker[F]**: Main API for event tracking
- **Emitter[F]**: Abstract interface for event transmission
- **Payload**: Event data structure and serialization
- **Subject**: User/device information container
- **Buffer**: Event batching logic
- **Tracking[F]**: Effect typeclass for time and UUID generation

## Core Design Principles

### Tagless Final Everywhere
```scala
// ✅ Abstract over effect type
trait Emitter[F[_]] {
  def send(event: Payload): F[Unit]
  def flushBuffer(): F[Unit]
}

// ❌ Hardcoded effect types
trait Emitter {
  def send(event: Payload): IO[Unit]  // Too specific
}
```

### Immutability by Default
```scala
// ✅ All data structures are immutable
final case class Subject(
  subjectInformation: Map[String, String]
)

// ❌ Mutable fields
class Subject {
  var userId: String = _  // Never use var
}
```

## Tracker Construction Patterns

### Implicit Tracking Instance
```scala
// ✅ Provide Tracking typeclass
implicit val tracking: Tracking[IO] = new Tracking[IO] {
  def getCurrentTimeMillis: IO[Long] = IO.realTime.map(_.toMillis)
  def generateUUID: IO[UUID] = IO(UUID.randomUUID())
}

// ❌ Forget the implicit
val tracker = Tracker[IO](emitter, "ns", "app")  // Won't compile
```

### Multiple Emitters
```scala
// ✅ Use NonEmptyList for multiple emitters
val emitters = NonEmptyList.of(emitter1, emitter2)
new Tracker(emitters, namespace, appId)

// ❌ Empty emitter list
List.empty[Emitter[F]]  // Must have at least one
```

## Event Tracking Patterns

### Self-Describing Events
```scala
// ✅ Use SelfDescribingData with SchemaKey
val event = SelfDescribingData(
  SchemaKey("com.acme", "clicked", "jsonschema", SchemaVer.Full(1, 0, 0)),
  Json.obj("button" := "submit")
)
tracker.trackSelfDescribingEvent(event)

// ❌ String-based schema (deprecated)
SelfDescribingJson("iglu:com.acme/clicked/1-0-0", json)
```

### Structured Events
```scala
// ✅ Provide required fields
tracker.trackStructEvent(
  category = "video",
  action = "play",
  label = Some("tutorial"),
  property = None,
  value = Some(1.5)
)

// ❌ Use nulls for optional fields
tracker.trackStructEvent("video", "play", null, null, null)
```

### Context Attachment
```scala
// ✅ Add contexts to events
val contexts = List(
  SelfDescribingData(weatherSchema, weatherData),
  SelfDescribingData(userSchema, userData)
)
tracker.trackPageView(url, contexts = contexts)

// ❌ Forget to wrap in SelfDescribingData
tracker.trackPageView(url, contexts = List(rawJson))
```

## Payload Construction

### Builder Pattern
```scala
// ✅ Use Payload's fluent API
Payload()
  .add("e", "pv")
  .add("url", pageUrl)
  .add("page", pageTitle)
  .addDict(subject.subjectInformation)

// ❌ Manual map construction
Map("e" -> "pv", "url" -> pageUrl)  // Use Payload
```

### JSON Encoding
```scala
// ✅ Use addJson for complex data
payload.addJson(
  json = contextsEnvelope,
  encodeBase64 = true,
  typeWhenEncoded = "cx",
  typeWhenNotEncoded = "co"
)

// ❌ Manual base64 encoding
Base64.encode(json.noSpaces)  // Let Payload handle it
```

### Timestamp Handling
```scala
// ✅ Use timestamp types
DeviceCreatedTimestamp(millis)  // Client timestamp
TrueTimestamp(millis)           // True timestamp

// ❌ Raw Long values
tracker.trackPageView(url, timestamp = 1234567890L)
```

## Subject Configuration

### Fluent Subject Building
```scala
// ✅ Chain subject modifications
val subject = Subject()
  .setUserId("user-123")
  .setPlatform(Server)
  .setIpAddress("192.168.1.1")
  .setUseragent("MyApp/1.0")

// ❌ Multiple variable assignments
var s = Subject()
s = s.setUserId("user-123")
s = s.setPlatform(Server)
```

### Platform Selection
```scala
// ✅ Use provided Platform ADT
import com.snowplowanalytics.snowplow.scalatracker._
subject.setPlatform(Server)      // srv
subject.setPlatform(Web)         // web
subject.setPlatform(Mobile)      // mob

// ❌ String literals
subject.subjectInformation + ("p" -> "srv")
```

### Resolution Format
```scala
// ✅ Use helper methods
subject.setScreenResolution(1920, 1080)  // "1920x1080"
subject.setViewport(1024, 768)          // "1024x768"

// ❌ Manual string formatting
subject.copy(info + ("res" -> s"${w}x${h}"))
```

## Buffer Management

### Buffer Configuration
```scala
// ✅ Choose appropriate strategy
BufferConfig.PayloadSize(40000)      // By size
BufferConfig.EventsCardinality(100)  // By count
BufferConfig.OneOf(size, count)      // Either

// ❌ Always NoBuffering
BufferConfig.NoBuffering  // Inefficient for volume
```

### Buffer State Machine
```scala
// ✅ Understand buffer transitions
sealed trait Action
case class Enqueue(payload: Payload) extends Action
case object Flush extends Action
case object Terminate extends Action

// Buffer.handle returns (newBuffer, Option[Request])
```

## Error Tracking

### Application Errors
```scala
// ✅ Track exceptions properly
try {
  riskyOperation()
} catch {
  case e: Exception =>
    tracker.trackError(e, contexts = List(contextData))
}

// ❌ Track emitter failures
emitter.send(payload).handleError { e =>
  tracker.trackError(e)  // Circular dependency!
}
```

### Error Schema
```scala
// ✅ Error tracking creates proper schema
// Automatically uses:
SchemaKey(
  "com.snowplowanalytics.snowplow",
  "application_error",
  "jsonschema",
  SchemaVer.Full(1, 0, 1)
)
```

## Global Contexts

### Adding Global Contexts
```scala
// ✅ Attach contexts to all events
val tracker = baseTracker
  .addContext(deviceContext)
  .addContext(sessionContext)

// ❌ Manually add to each event
tracker.trackPageView(url, contexts = List(deviceContext))
tracker.trackStructEvent(..., contexts = List(deviceContext))
```

### Context Ordering
```scala
// ✅ Global contexts combined with event contexts
// Order: globalContexts ++ eventContexts
tracker.addContext(global)
  .trackPageView(url, contexts = List(local))
// Results in: List(global, local)
```

## Generated Code

### Version Information
```scala
// ✅ Use generated version
import com.snowplowanalytics.snowplow.scalatracker.generated._
val version = ProjectSettings.version

// ❌ Hardcode version
val version = "2.0.0"  // Gets out of sync
```

## Testing Core Components

### Test Directory Structure
```
core/src/test/scala/com.snowplowanalytics.snowplow.scalatracker/
├── BufferSpec.scala      # Buffer state machine tests
├── EmitterSpec.scala     # Emitter abstraction tests
├── PayloadSpec.scala     # Payload construction & serialization
├── TrackerSpec.scala     # Main tracker API behaviors
└── syntax.scala          # Id monad implicits for testing
```

### Specs2 Test Organization
```scala
// ✅ Standard test structure
class TrackerSpec extends Specification {
  "Tracker.trackPageView" should {
    "add page view event" in new DummyTracker {
      tracker.trackPageView("http://example.com")
      emitter.lastInput.nvPairs("e") must beEqualTo("pv")
    }
  }
}

// ❌ Wrong test framework
class TrackerTest extends FunSuite  // Use Specs2
```

### Test Fixture Pattern
```scala
// ✅ Reusable test scope with Specs2 Scope trait
trait DummyTracker extends Scope {
  implicit val idTracking: Tracking[Id] = syntax.id.idTracking
  val emitter = new TestEmitter[Id]
  val subject = Subject().setUserId("test-user")
  val tracker = new Tracker(NonEmptyList.one(emitter), "ns", "app", subject)
}

// ❌ Setup duplication
"test" in {
  val emitter = new TestEmitter[Id]  // Repeated
  val tracker = new Tracker(...)      // in every test
}
```

### Test Emitter Implementation
```scala
// ✅ Simple test emitter
class TestEmitter[F[_]: Applicative] extends Emitter[F] {
  var lastInput: Payload = _
  var allInputs: List[Payload] = Nil
  def send(p: Payload): F[Unit] = {
    lastInput = p
    allInputs = p :: allInputs
    ().pure[F]
  }
  def flushBuffer(): F[Unit] = ().pure[F]
}

// ❌ Mock with Mockito
mock[Emitter[F]]  // Complex with F[_]
```

### Id Monad Testing Strategy
```scala
// ✅ Use Id for synchronous unit tests
import cats.Id
import syntax.id._

implicit val idTracking: Tracking[Id] = new Tracking[Id] {
  def getCurrentTimeMillis: Long = 1234567890L     // Fixed
  def generateUUID: UUID = UUID.fromString("...")  // Deterministic
}

// ❌ Use IO for simple unit tests
tracker.trackPageView(url).unsafeRunSync()  // Unnecessary complexity
```

### JSON Validation with Optics
```scala
// ✅ Use Circe optics for clean assertions
import io.circe.optics.JsonPath._
root.e.string.getOption(json) must beSome("pv")
root.url.string.getOption(json) must beSome("http://example.com")
root.co.string.getOption(json) must beMatching("\\{.*\\}")

// ❌ Manual cursor navigation
json.hcursor.downField("e").as[String].toOption must beSome("pv")
```

### Property-Based Testing
```scala
// ✅ Use ScalaCheck generators
implicit val payloadArb: Arbitrary[Payload] = Arbitrary {
  for {
    kvPairs <- Gen.mapOf(Gen.alphaStr.map(k => (k, Gen.alphaStr.sample.get)))
  } yield Payload(kvPairs)
}

prop("payload round-trip") = forAll { (p: Payload) =>
  Payload.parse(p.asJson) == Right(p)
}

// ❌ Only example-based tests
"test with specific payload" in { ... }  // Misses edge cases
```

### Testing Event Timestamps
```scala
// ✅ Test timestamp behavior
"add device timestamp" in new DummyTracker {
  tracker.trackPageView("url", timestamp = Some(DeviceCreatedTimestamp(123L)))
  emitter.lastInput.nvPairs("dtm") must beEqualTo("123")
}

// ❌ Ignore timestamp testing
tracker.trackPageView("url")  // Don't verify timestamp handling
```

### Buffer State Testing
```scala
// ✅ Test buffer state transitions
"buffer until size limit" in {
  val buffer = Buffer(BufferConfig.PayloadSize(100))
  val (b1, req1) = buffer.enqueue(smallPayload)
  req1 must beNone  // Not full yet
  val (b2, req2) = b1.enqueue(largePayload)
  req2 must beSome  // Size exceeded
}

// ❌ Only test happy path
buffer.enqueue(payload)  // Don't test edge cases
```

### Testing Error Tracking
```scala
// ✅ Verify error schema generation
"track application errors" in new DummyTracker {
  val error = new RuntimeException("test error")
  tracker.trackError(error)
  val json = emitter.lastInput.nvPairs("ue_pr")
  root.schema.string.getOption(decode(json)) must 
    contain("application_error")
}

// ❌ Skip error tracking tests
// Errors are critical functionality
```

### Regex Validation Patterns
```scala
// ✅ Validate UUID format
emitter.lastInput.nvPairs("eid") must beMatching(
  "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
)

// ❌ Check exact UUID value
emitter.lastInput.nvPairs("eid") must beEqualTo("specific-uuid")
```

### Context Testing
```scala
// ✅ Test context attachment
"attach contexts to events" in new DummyTracker {
  val context = SelfDescribingData(schema, Json.obj("key" := "value"))
  tracker.trackPageView("url", contexts = List(context))
  val co = decode[Json](emitter.lastInput.nvPairs("co"))
  root.data.arr.getOption(co).map(_.size) must beSome(1)
}

// ❌ Ignore context testing
tracker.trackPageView("url")  // Contexts are important
```

### Testing Best Practices
✅ **Core Module Testing DOs:**
- Test with `Id` monad for synchronous behavior
- Use `Scope` traits for fixture reuse
- Validate JSON structure with Circe optics
- Test all event types and their schemas
- Verify buffer state transitions
- Use property-based testing for serialization

❌ **Core Module Testing DON'Ts:**
- Don't use IO in unit tests unless necessary
- Don't mock with frameworks (use simple test doubles)
- Don't test private methods directly
- Don't ignore edge cases in buffer logic
- Don't skip timestamp validation

## JSON Utilities

### Safe JSON Construction
```scala
// ✅ Use JsonUtils for null handling
JsonUtils.jsonObjectWithoutNulls(
  "sku" := sku,
  "name" := nameOption,  // Omitted if None
  "price" := priceOption
)

// ❌ Manual null filtering
Json.obj(fields.filterNot(_._2.isNull): _*)
```

### Circe Integration
```scala
// ✅ Import circe syntax
import io.circe.syntax._
import io.circe.Json

val json = Json.obj("key" := "value")

// ❌ Manual JSON construction
Json.fromFields(List(("key", Json.fromString("value"))))
```

## Platform Detection

### Platform Enumeration
```scala
// ✅ Use sealed trait pattern
sealed trait Platform {
  def abbreviation: String
}
case object Server extends Platform {
  val abbreviation = "srv"
}

// ❌ String constants
val PLATFORM_SERVER = "srv"
```

## Type Aliases

### Package Object Types
```scala
// ✅ Use standard type aliases
import com.snowplowanalytics.snowplow.scalatracker._
type SelfDescribingJson = SelfDescribingData[Json]

// ❌ Create duplicate aliases
type MyJson = SelfDescribingData[Json]
```

## Common Mistakes

### Mistake: Not Running Effects
```scala
// ❌ Effect created but not executed
val tracking = tracker.trackPageView("url")
// Nothing happens!

// ✅ Execute the effect
tracker.trackPageView("url").unsafeRunSync()  // IO
await(tracker.trackPageView("url"))           // Future
```

### Mistake: Mutable Tracker State
```scala
// ❌ Try to mutate tracker
tracker.namespace = "new-namespace"  // Won't compile

// ✅ Create new tracker instance
val newTracker = new Tracker(
  emitters, "new-namespace", appId
)
```

### Mistake: Wrong Import Scope
```scala
// ❌ Import everything
import com.snowplowanalytics.snowplow.scalatracker._
import com.snowplowanalytics.snowplow.scalatracker.Tracker._

// ✅ Import what you need
import com.snowplowanalytics.snowplow.scalatracker.{Tracker, Emitter}
```

## Performance Considerations

### Payload Size
```scala
// ✅ Be aware of size limits
// GET requests: ~2000 bytes safe limit
// POST requests: Collector-dependent, typically 1MB

// ❌ Huge contexts
val hugeContext = Json.obj(
  "data" := List.fill(10000)("item")  // Too large
)
```

### UUID Generation
```scala
// ✅ Generate UUIDs through F[_]
Tracking[F].generateUUID

// ❌ Direct UUID generation
UUID.randomUUID()  // Breaks referential transparency
```

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