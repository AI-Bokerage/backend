# Contributing to Gen-Fin

## Branch Strategy

- `main` is always releasable.
- Work happens on short-lived `feature/<short-description>` or `fix/<short-description>` branches cut from `main`.
- Merge via pull request only; no direct pushes to `main`.

## Coding Conventions

- Java 21, one namespace: `io.genfin`.
- Every module follows `api / port / internal`: only `api` is public; never import another module's `internal` package.
- No public implementation classes — expose interfaces, not concrete types.
- Keep `fin-core` free of framework/provider/infrastructure dependencies. Integrations belong in `fin-providers`, `spring-starter`, or `demo`.

## Formatting

- Formatting is enforced by Spotless (`googleJavaFormat`) and is not a matter of taste — run it before committing:

```bash
./gradlew spotlessApply
```

- `./gradlew check` runs Spotless, Checkstyle, PMD, Error Prone, and tests. It must pass before opening a PR.

## Commit Standards

Follow [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `build:`, `chore:`. Subject line imperative, ≤72 characters.

## Release Process

1. Land changes on `main` via reviewed PRs.
2. Update `CHANGELOG.md` under `[Unreleased]` as changes merge.
3. Cut a release: bump `genfin.version` in `gradle.properties`, move the `[Unreleased]` section to a dated version heading, tag `vX.Y.Z`.
4. CI publishes tagged builds via the `publishing-conventions` convention plugin.
