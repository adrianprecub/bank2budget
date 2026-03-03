# bank2budget

Spring Boot 4.0.3 / Java 25 app that parses CAMT.053 bank statement XMLs, categorizes
transactions via keyword matching + AI fallback (LangChain4j), and uploads to Google Sheets.
Stateless — no database, no persistence layer.

## Build & Run

```bash
mvn package -DskipTests        # build JAR
mvn spring-boot:run             # run locally (port 8080)
mvn test                        # run all tests
mvn test -Dtest=CamtParserServiceTest                          # single test class
mvn test -Dtest=CamtParserServiceTest#shouldParseValidCamt053File  # single method
docker build -t bank2budget .   # Docker build (Temurin JDK 25)
```

No linter, formatter, or static analysis tooling is configured. Style is enforced by convention.

## Project Structure

```
com.bank2budget/
  PaymentProcessorApp.java              # @SpringBootApplication entry point
  config/                               # @ConfigurationProperties records, @Configuration beans
  controller/                           # REST controllers (thin, delegate to services)
  exception/                            # CamtParseException + @RestControllerAdvice handler
  model/                                # Immutable data records (Transaction, ProcessingResult)
  service/                              # All business logic
src/main/resources/
  application.yml                       # All config including categorization rules
  static/index.html                     # Single-page vanilla HTML/JS upload UI
src/test/resources/
  sample-camt053.xml, sample-camt053-v8.xml   # Test fixtures
```

## Code Style

### Java Version & Features

Target is Java 25. Use modern features freely: records, text blocks, `var`,
`String.formatted()` (instance method, not `String.format()`), unnamed variables (`_`),
`List.copyOf()`, `.toList()`, `List.getFirst()`.

### Formatting

- 4-space indentation, ~120 char line length.
- K&R braces (opening on same line). Blank line after class opening brace.
- Blank line between methods. No blank line between same-type field declarations.
- Method chaining: each call on its own indented line.
- Use `var` when the type is obvious from the right-hand side.

### Naming

| Element       | Convention         | Example                                    |
|---------------|--------------------|--------------------------------------------|
| Classes       | PascalCase nouns   | `PaymentProcessingService`, `CamtParserService` |
| Records       | PascalCase nouns   | `Transaction`, `ProcessingResult`          |
| Methods       | camelCase verbs    | `processZip()`, `matchByKeyword()`         |
| Boolean methods | `is` prefix      | `isConfigured()`                           |
| Constants     | UPPER_SNAKE_CASE   | `MAX_ZIP_ENTRIES`, `MAX_BATCH_SIZE`        |
| Logger        | Always `log`       | `private static final Logger log = ...`    |
| Packages      | lowercase singular | `controller`, `service`, `model`           |

### Imports

Three groups separated by blank lines, in this order:

1. Project imports (`com.bank2budget.*`)
2. Third-party imports (`org.springframework.*`, `org.slf4j.*`, `dev.langchain4j.*`)
3. JDK stdlib (`java.*`, `javax.*`)

Prefer explicit imports. Wildcard imports only when importing many items from one package.

### Records vs Classes

- **Records** for immutable data carriers: DTOs, config properties, internal value types.
- **Classes** for services, controllers, config, exceptions — anything with behavior or Spring semantics.
- Records may have compact constructors (for defaults/validation), business methods, and `with*()` copy methods.

### Collections

- `new ArrayList<>()` for mutable lists being built up (pre-size when count is known).
- `List.of()` / `Map.of()` for immutable literals and empty returns.
- `List.copyOf()` for defensive immutable copies.
- `.toList()` on streams (returns unmodifiable list).
- `LinkedHashMap` when insertion order matters.

### Strings

- `"text %s".formatted(arg)` — primary pattern (instance method).
- Text blocks (`"""..."""`) for multi-line content.
- `StringBuilder` for building in loops.
- `+` concatenation only for simple one-liners (exception messages).

### Null Handling

- No `Optional` usage. Explicit null checks with ternary or early return.
- Compact record constructors provide null-safe defaults (`if (x == null) x = Map.of()`).
- `orEmpty()` pattern: `return value != null ? value : ""`.

### Logging

SLF4J with parameterized `{}` placeholders. Never concatenate strings in log calls.

```java
private static final Logger log = LoggerFactory.getLogger(ClassName.class);
log.info("Parsed {} transactions from {}", count, filename);
log.error("Failed to upload to Google Sheets", exception);  // exception as last arg
```

Levels: `info` for business operations, `debug` for low-level detail, `warn` for
recoverable failures, `error` for actual errors.

### Error Handling

- Single custom exception: `CamtParseException` (unchecked, extends `RuntimeException`).
- `@RestControllerAdvice` returns RFC 7807 `ProblemDetail` responses.
- Catch-and-rethrow to add context: `throw new CamtParseException("msg: " + e.getMessage(), e)`.
- Catch-log-and-degrade for non-critical features (AI, Google Sheets).

### Dependency Injection

Constructor injection only. No `@Autowired`. Spring auto-detects the single constructor.
Multi-param constructors align one parameter per line.

### Javadoc & Comments

- Javadoc on classes and non-obvious public methods. Uses `{@code}`, `{@link}`, HTML tags.
- Single-line `/** ... */` for constants and simple methods.
- `//` inline comments for implementation notes.
- No Javadoc on test methods — names are self-documenting.

### Method Ordering in a Class

1. `static final` constants (logger first, then business constants)
2. Instance fields (`private final`)
3. Constructor
4. Public methods
5. Private helper methods
6. Static utility methods
7. Inner classes/records (at the end)

### Visibility

- Fields: always `private final` (or `private static final` for constants).
- Methods: `public` for API, `private` for internals. Package-private (no modifier) for
  methods that need direct testing from same-package test classes.

## Spring Conventions

- `@ConfigurationPropertiesScan` on main class. Properties are records, not classes.
- `@Service` for services. `@RestController` for controllers. No `@Component`.
- Controllers return `ResponseEntity<T>`. Validation is manual (no Bean Validation).
- Controllers are thin: validate input, delegate to service, return result.
- REST client: use `RestClient` (not `RestTemplate` or `WebClient`).
- External features degrade gracefully when env vars are missing.
- Config uses `${ENV_VAR:}` (empty default) pattern in `application.yml`.

## Test Conventions

- JUnit 5 with `assertEquals`/`assertTrue`/`assertThrows` (no AssertJ).
- Test naming: `shouldDoSomething` (not `test_*` or `given_when_then`).
- Flat structure — no `@Nested` classes. Section comments group related tests.
- File naming: `<ClassUnderTest>Test.java` in the same package.
- No Mockito. Real objects instantiated directly. `DisabledChatModel` as no-op stub.
- Integration tests: `@SpringBootTest` + `@AutoConfigureMockMvc`, property overrides
  to disable external integrations.
- Test fixtures: XML files in `src/test/resources/`, loaded via `getResourceAsStream()`.
  Inline XML text blocks for edge cases.
- Helper methods: `tx(...)`, `serviceWithRules(...)`, `createZip(...)` for test data.

## Frontend (index.html)

- Single-file vanilla HTML/CSS/JS. No framework, no build step, no npm.
- Inline `<style>` and `<script>`. Double quotes for JS strings.
- `const` for references, `async/await` + `fetch()` for HTTP, `FormData` for uploads.
- CSS class toggling (`.visible`) for state management.
- XSS-safe output via `escapeHtml()` using DOM text nodes.

## Architecture Notes

- Layered: Controller -> Service -> External integrations. No repository/DAO layer.
- No interfaces for services — concrete classes only.
- Immutable data flow: records with `withCategory()` copy methods.
- Security: ZIP bomb detection, path traversal prevention, XXE protection, log sanitization.
