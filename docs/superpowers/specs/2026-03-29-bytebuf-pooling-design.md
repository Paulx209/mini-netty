# ByteBuf Pooling Design

## Context

The current buffer layer provides:

- `ByteBufAllocator` as the allocation abstraction
- `UnpooledByteBufAllocator` as the only allocator implementation
- `HeapByteBuf` as the concrete heap-backed buffer
- reference counting through `AbstractReferenceCountedByteBuf`

This is enough to add pooling without changing the public `ByteBuf` usage model, but targeted internal refactors are required to make pooling safe:

- a central accessibility check for released buffers
- reset hooks for all mutable runtime state, including marked indexes
- a resettable `maxCapacity` for reused pooled instances

The first version should stay focused on heap buffers and avoid introducing Netty-style arena complexity too early.

## Goals

- Add pooling for `HeapByteBuf`
- Reuse both `ByteBuf` objects and backing `byte[]`
- Keep the existing `ByteBufAllocator` API unchanged
- Return pooled buffers to the pool when `release()` drives `refCnt` to `0`
- Keep a clean extension point for future direct-buffer pooling

## Non-Goals

- Real direct-memory pooling in this version
- Arena / page / chunk management
- Thread-local caches
- Full parity with Netty's pooled allocator internals
- Performance tuning beyond basic bounded bucket reuse

## Recommended Approach

Use a new `PooledByteBufAllocator` that manages a heap-only pool. The pool groups reusable buffers by normalized capacity buckets. A pooled heap buffer behaves like the current `HeapByteBuf` while borrowed, and returns itself to the bucket when the final `release()` happens.

This approach is preferred because it:

- preserves the current public allocation API
- limits the first implementation to one memory kind
- gives immediate wins from object and array reuse
- keeps future direct-buffer pooling parallel to the heap design

## Alternatives Considered

### 1. Pool only `ByteBuf` objects

Rejected for the first version. Most allocation churn comes from backing arrays, so object-only pooling would provide limited value.

### 2. Pool arbitrary heap requests with fully mutable buffer metadata

Possible, but more invasive. It would require broad lifecycle refactoring and make state reset more error-prone.

### 3. Build arena/page/chunk pooling immediately

Rejected as over-scoped for the current codebase. The implementation and test burden would outweigh the value of a first pooling milestone.

## Architecture

### `PooledByteBufAllocator`

Responsibilities:

- implement `ByteBufAllocator`
- normalize requested capacities
- choose pooled vs unpooled allocation path
- leave direct-buffer methods on the current fallback path

Behavior:

- `heapBuffer(...)` should prefer pooled allocation when the request is within pooling limits
- `buffer(...)` should prefer pooled allocation only when it resolves to the heap path
- when `preferDirect` is `true`, `buffer(...)` delegates to `directBuffer(...)` and therefore stays non-pooled in version 1
- `directBuffer(...)` remains non-pooled in version 1 even though the current simplified implementation still returns `HeapByteBuf`
- requests outside supported pooling limits should fall back to `UnpooledByteBufAllocator`

This rule defines pooling by allocator path rather than by the current placeholder implementation behind `directBuffer(...)`.

### `HeapByteBufPool`

Responsibilities:

- maintain heap buffer buckets keyed by normalized capacity
- borrow and recycle `PooledHeapByteBuf` instances
- bound per-bucket cache size

Suggested structure:

- one concurrent queue per bucket
- one atomic cached-entry counter per bucket
- bounded number of cached entries per bucket enforced by atomic admission
- drop excess returned entries instead of growing without limit

### `PooledHeapByteBuf`

Responsibilities:

- behave like `HeapByteBuf` during normal use
- remember its owning pool and current bucket capacity
- reset runtime state when activated
- return itself to the pool instead of nulling the array on final release

This class should remain a heap-backed `ByteBuf` so existing buffer operations continue to work without API changes.

### Internal buffer refactor required for pooling

To make pooling implementation-safe, the buffer base classes must support:

- `ensureAccessible()` style checks on public `ByteBuf` operations
- resetting `maxCapacity` when a pooled instance is borrowed for a new request
- resetting private marker state during recycle and re-activation

The design assumes a narrow internal refactor in `AbstractByteBuf`:

- `maxCapacity` becomes resettable through a protected or package-private lifecycle hook used only by allocator / pool internals
- marker state gets an internal reset hook so pooled subclasses do not need illegal access to private fields
- shared public operations in `AbstractByteBuf` call an accessibility guard before touching state
- concrete storage accessors in `HeapByteBuf` that bypass shared code also perform the same guard

This keeps the external API unchanged while making post-release behavior enforceable.

## Capacity Normalization and Pooling Boundaries

### Supported pooled capacities

- minimum bucket size: `64`
- capacities up to and including `4MB` are normalized to powers of two
- requests larger than `4MB` fall back to unpooled allocation

Examples:

- `1` -> `64`
- `64` -> `64`
- `200` -> `256`
- `1025` -> `2048`

### `maxCapacity` handling

- the backing array capacity is determined by the normalized bucket size
- `maxCapacity` still constrains later growth
- pooled buffers reset `maxCapacity` on borrow to match the current request
- if `maxCapacity < normalizedInitialCapacity`, allocation fails fast

Because pooled instances are reused across requests, resettable `maxCapacity` is a required part of the design rather than an optional optimization.

### Direct buffer handling

- `directBuffer(...)` remains on the existing non-pooled path
- the allocator design keeps room for a future `DirectByteBufPool`
- the current placeholder fact that `directBuffer(...)` still returns `HeapByteBuf` does not make that path poolable in version 1

## Buffer Lifecycle

### Borrow

1. allocator receives a heap allocation request
2. requested initial capacity is normalized to a bucket
3. pool attempts to poll a `PooledHeapByteBuf` from that bucket
4. if none is available, a new pooled buffer is created with a backing array sized to the bucket
5. before returning the buffer, runtime state is reset

### Active state reset

Before a pooled buffer is returned to the caller, it must reset:

- `readerIndex = 0`
- `writerIndex = 0`
- marked reader / writer indexes to `0`
- `refCnt = 1`
- `maxCapacity` to the current request value
- owning pool reference
- bucket capacity metadata
- recycled / in-pool guard flag

### Use

The buffer should behave the same as `HeapByteBuf` during reads, writes, capacity checks, and reference counting.

### Recycle

When `release()` reduces `refCnt` from `1` to `0`:

- the buffer must be marked as no longer active
- runtime indexes are cleared
- the buffer is returned to the correct bucket if the bucket still accepts entries
- if the bucket is full, the object is dropped and becomes GC-eligible

## Capacity Change, Growth, and Shrink

The current buffer implementation allows `capacity(int)` to both grow and shrink. Pooling must define how bucket ownership changes across either direction.

Recommended rule:

- pooled buffers may still use the existing capacity-change logic
- any capacity change that replaces the backing array swaps in a new array and detaches the old one
- in version 1, the detached old array is discarded immediately rather than returned to a separate standalone array pool
- after a grow or shrink, if the current backing array capacity is still within pooling limits, the buffer updates its bucket metadata to the normalized bucket for that current capacity
- on recycle, the buffer returns to the bucket matching its current backing array capacity
- if the current backing array moves beyond the pooling limit, the buffer is treated as non-pooled for recycle and is dropped instead of cached

This preserves object reuse across capacity changes and still allows the final active backing array to be reused on recycle, without introducing a second array-pool subsystem in version 1.

## Safety Rules

### Double recycle protection

A buffer that has already been recycled must not be inserted into the pool again. Returning the same object twice would corrupt the pool.

### Post-release misuse protection

After `refCnt` reaches `0`, subsequent operations should fail consistently as illegal reference-count usage rather than silently operating on a recycled object.

This requires a central accessibility contract:

- all public buffer operations must verify that the buffer is still accessible
- pooled recycle must mark the buffer inaccessible before it re-enters the pool
- a subsequent borrow reactivates the buffer only for the new owner

This guard is mandatory because pooled buffers no longer become harmlessly unusable through `array = null`.

### Exposed array and `ByteBuffer` view caveat

`array()` and `nioBuffer()` expose direct views of the backing storage. Pooling cannot revoke a `byte[]` or `ByteBuffer` that was already handed out before release.

Version 1 therefore defines this explicitly:

- using a previously obtained `byte[]` or `ByteBuffer` after the parent buffer has been released is caller misuse
- stale aliases may read or mutate data that now belongs to a later borrower
- the pooled allocator does not attempt to invalidate or zero previously exposed views
- tests and documentation should make this ownership rule explicit

### Reference-count integration

- `retain()` and intermediate `release()` calls must continue to work exactly as they do now
- only the final release triggers recycle
- illegal retain / release cases should keep the existing exception behavior

## Testing Strategy

### Unit coverage

- pooled allocation returns a valid heap buffer
- releasing to `0` makes the buffer recyclable
- borrowing again resets indexes, marker state, `refCnt`, and `maxCapacity`
- intermediate `retain()` / `release()` calls do not recycle early
- double release is rejected
- use-after-release is rejected
- post-release calls on read / write / random-access / `array()` / `nioBuffer()` are rejected consistently
- grow and shrink paths update recycle bucket selection correctly when still poolable
- replaced arrays from grow / shrink are discarded without corrupting the active buffer
- buffers larger than pooling limits fall back to unpooled allocation
- per-bucket capacity limits are enforced
- normalization boundary cases: `0`, `64`, `4MB`, `4MB + 1`
- invalid pooled request where `maxCapacity < normalizedInitialCapacity` fails fast
- `buffer()` behavior when allocator is configured to prefer direct stays on the non-pooled direct path

### Concurrency coverage

- concurrent borrow / release across the same bucket does not corrupt pool state
- reference counting remains correct under concurrent `retain()` / `release()`
- final release only recycles once under concurrent release races
- bucket admission limit remains bounded under contention

## Implementation Plan

1. Refactor `AbstractByteBuf` and `HeapByteBuf` to support accessibility checks, marker reset hooks, and resettable pooled state.
2. Add `PooledHeapByteBuf` with recycle-on-final-release behavior.
3. Add `HeapByteBufPool` with bucket normalization, atomic bounded admission, and recycle logic.
4. Add `PooledByteBufAllocator` and wire heap allocation methods to the pool.
5. Keep direct allocation on the existing unpooled path.
6. Add focused unit and concurrency tests.

## Open Points Chosen for Version 1

These choices are intentionally fixed for the first version to avoid scope drift:

- no array zeroing on recycle
- no thread-local cache
- no direct-memory pooling
- no arena/page/chunk allocator
- no standalone array sub-pool for detached arrays during capacity changes

## Success Criteria

The first implementation is successful if:

- pooled heap allocation is available through a new allocator
- final `release()` returns buffers to the pool safely
- subsequent allocations can reuse pooled objects and backing arrays
- the existing `ByteBuf` calling style does not need to change
- tests demonstrate reset correctness, reference-count correctness, and bounded pooling behavior
