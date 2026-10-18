# Shared message fixtures

Contract between `dari-web` and the Kotlin side. Each file holds recorder `events`, the store `options`
and the `expected` entries as `WireMessageDetail` JSON.

- `dari-web/test/contract.test.ts` replays the events and compares the result with `expected`.
- `dari-inspector-protocol` `SharedFixturesTest` strictly decodes `expected`, so the web output stays
  readable by the desktop inspector.

Both CI workflows watch `fixtures/**`; a change here must pass on both sides.
