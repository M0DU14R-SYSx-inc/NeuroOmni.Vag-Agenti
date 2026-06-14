# AAR decompile — hard rule

The Nexa Android SDK does not ship Javadoc/KDoc. Public docs are a
quickstart + a GitHub README. Production apps using the SDK only exercise
the default code path, so scraping examples misses every optional
parameter, every nullable field, every `Result`-wrapped return type, and
every mangled suspend-fun signature.

**Decompiled bytecode is the only ground truth.** Use it first, not last.

## When this applies

- Any at-bat that calls into `ai.nexa.*` for the first time in this
  session — wrapper constructors, builders, suspend functions, plugin
  ids, device ids, license fields.
- Any at-bat that touches a Kotlin-from-Java boundary where mangled
  names (`-gIAlu-s`, `-IoAF18A`) hide the real signature.
- Any time a sub-agent reports "the docs say X" without an authoritative
  link.

## Procedure

```bash
# 1. Locate the AAR (Gradle has already downloaded it)
find ~/.gradle/caches/modules-2/files-2.1/ai.nexa -name "*.aar"

# 2. Unzip into a temp dir
mkdir -p /tmp/nexa-aar && cd /tmp/nexa-aar
unzip -o "<aar-path>"

# 3. Read every class signature
javap -p -classpath classes.jar ai.nexa.<ClassName>

# 4. For Kotlin-specific metadata (default params, nullability), use:
javap -v -classpath classes.jar ai.nexa.<ClassName> | grep -E "RuntimeVisibleAnnotations|Nullable|NotNull"
```

## What to record

When the bytecode differs from any prior documented assumption, capture
the diff in `wiki/FAILURE_LOG.md` under a "doc gap" entry so the next
agent doesn't re-rediscover it.

Minimum capture per class:

- Full constructor signature with parameter names and defaults.
- Nullability of every field that callers might leave unset.
- Return type, including `Result<T>` wrappers and mangled suffixes.
- Suspend vs. non-suspend; if suspend, the `Continuation`-style JVM name.

## What NOT to do

- Do not paste decompiled bytecode into the wiki — link the FAILURE_LOG
  entry instead. Bytecode is verbose and dates badly.
- Do not trust an agent's "I read the source" without a path. The source
  isn't published; the only readable form is the AAR.
- Do not skip this step because "the SDK feels familiar." It is not.

## Rationale

A previous sub-agent at-bat shipped code that compiled, then silently
no-op'd in production because it didn't unwrap a `Result<T>` return.
The agent had read working call sites but missed that the production app
was calling `.getOrThrow()` implicitly through a helper. 30 seconds of
`javap` would have caught it. The rule pays for itself the first time
it prevents a failed at-bat.
