# Rust Portable

Write & run Rust in **IntelliJ IDEA Community** for free — no RustRover license, no system-wide
Rust install. IDEA Community doesn't bundle the official Rust plugin (that's Ultimate / RustRover
/ CLion only), so this fills the gap.

## Features
- **Rust Portable toolchain** — install a toolchain from inside the IDE using the official
  `rustup`; stored under `~/.rust-portable` with its own `RUSTUP_HOME` / `CARGO_HOME`. Pick
  stable, beta, nightly or a pinned release. `--no-modify-path` means nothing touches your shell
  profile or an existing system Rust.
- **Run a `.rs` file** — `cargo run` from the crate root via a run configuration (right-click ▸
  Run, or the gutter ▶ on `fn main`). A loose file with no `Cargo.toml` is compiled with `rustc`
  into a temp dir and executed, so scratch files stay runnable.
- **Syntax highlighting** — lexer-based, covering the things a C-like lexer gets wrong in Rust:
  lifetimes vs char literals, nested block comments, raw/byte strings (`r#"…"#`, `b"…"`),
  attributes, macros and doc comments.
- **Code intelligence (optional)** — completion, go-to-definition, hover docs and diagnostics via
  **rust-analyzer** (the same server RustRover runs underneath), bridged with the free **LSP4IJ**
  plugin. Installed as an official rustup component, so it always matches the toolchain. Fully
  offline.
- **Run cargo tools** — `build` / `check` / `test` / `run` / `add` / `update` / `fmt` / `clippy` /
  `tree` in a console.

On **RustRover / IDEA Ultimate / CLion** the native Rust support owns `.rs`, and this plugin steps
aside (`<incompatible-with>com.jetbrains.rust</incompatible-with>`).

## Architecture
A re-instantiation of the `go-portable` skeleton for Rust: portable toolchain installer, static
`.rs` file-type claim, `cargo run` config, LSP4IJ bridge (languageId **must** be `"rust"`), and a
one-click "Enable code intelligence" onboarding banner.

Two places where Rust genuinely differs from the Go/Python plugins, both deliberate:

1. **Install, not unpack.** Go and Python ship self-contained archives you extract and use. Rust's
   standalone tarballs are component trees that expect `install.sh`, and Windows gets an `.msi`
   instead — neither is a plain extract. So `RustSdkDownloadTask` downloads the official
   `rustup-init` and runs it into an isolated prefix. That also buys `rustup component add
   rust-analyzer`, which is how the language server gets installed, matched to that exact rustc.
2. **No `go run` equivalent.** Inside a crate we run `cargo run`; outside one there is no
   single-file runner in the toolchain, so the run config shells out to `rustc` first and then
   executes the produced binary.

### The rust-analyzer shim trap

Do **not** detect rust-analyzer by looking for `<CARGO_HOME>/bin/rust-analyzer`. rustup creates a
shim there for every tool it knows about the moment a toolchain is installed, whether or not the
component is present, and that shim is a symlink to the `rustup` executable — so even
`Files.isRegularFile` (which follows the link to a real file) reports **true** on a toolchain that
has never seen the component. Running it then fails with:

```
error: Unknown binary 'rust-analyzer' in official toolchain 'stable-aarch64-apple-darwin'
```

which surfaces as code intelligence that silently never starts, with the install step skipped
because everything "looked" present. Verified against a real install: before
`rustup component add rust-analyzer`, `rustup component list --installed` shows only
`cargo` / `rust-src` / `rust-std` / `rustc`, yet the shim is already there.

The component's real binary lands at `<RUSTUP_HOME>/toolchains/<toolchain>/bin/rust-analyzer` and
only once it is genuinely installed. That is what `RustAnalyzerManager.binFor` looks for, and what
the LSP connection provider launches.

## Why `.rs` was safe to claim
The Marketplace file-type index lists `org.jetbrains.android` as claiming `*.rs` (RenderScript)
with a compatibility list that includes plain `IDEA`. That entry is stale. Verified against a real
IntelliJ IDEA Community 2025.2 distribution:

- `org.jetbrains.android` is **not bundled** in IDEA Community (only `android-gradle-dsl` and
  `android-gradle-declarative-lang-ide`)
- **zero** occurrences of "RenderScript" across the 72 bundled plugins
- **no** bundled `plugin.xml` registers extension `rs`

So `.rs` is an unknown file type in a stock IDEA Community, which is what makes the IDE's
"plugin recommended for *.rs" banner fire. That banner is the plugin's main distribution channel,
and the static `extensions="rs"` attribute in `plugin.xml` is its only input — see the comment
there before changing it.

## Build
```
JAVA_HOME=<a JBR 17> ./gradlew buildPlugin      # -> build/distributions/rust-portable-*.zip
JAVA_HOME=<a JBR 17> ./gradlew runIde           # sandbox IDE for testing
JAVA_HOME=<a JBR 17> ./gradlew verifyPlugin     # JetBrains Plugin Verifier (publish gate)
```

Build with a JDK ≤ 21 — Kotlin 1.9.x's compiler crashes on JDK 24+ (`CoreJrtFileSystem`). On this
machine: `~/Library/Java/JavaVirtualMachines/jbr-17.0.9/Contents/Home`.

## Releasing
Same as `php-portable`: publishing is decoupled from merging. `.github/workflows/publish.yml`
triggers on `v*` tag pushes and refuses to run when the tag disagrees with `version` in
`build.gradle.kts`. Bump the version + `<change-notes>`, merge, then
`git tag vX.Y.Z && git push origin vX.Y.Z`.
