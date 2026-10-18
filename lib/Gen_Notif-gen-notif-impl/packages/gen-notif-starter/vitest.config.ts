import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    fakeTimers: {
      // ponytail: default toFake list includes setImmediate, which Express's
      // router/finalhandler use internally to dispatch its final "no route
      // matched" 404 — faking it hangs any supertest request against an
      // unmatched route under vi.useFakeTimers(). Excluded here so
      // create-gen-reg.test.ts's worker-interval tests can fake
      // setInterval/setTimeout without breaking plain HTTP 404s.
      toFake: ["setTimeout", "clearTimeout", "setInterval", "clearInterval", "Date"],
    },
  },
});
