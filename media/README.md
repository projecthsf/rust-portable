# Marketplace media

`marketplace/*.png` are the screenshots for the JetBrains Marketplace listing, at the same
2400×1520 the other Portable plugins use.

## Regenerating

```bash
cd media/src && python3 build.py
```

`build.py` writes one 1200×760 HTML page per screen and drives headless Chrome at
`--force-device-scale-factor=2` to get 2400×1520 PNGs. `shell.css` holds the shared IDE chrome
and the Darcula token palette.

Two things about the renderer worth knowing:

- Chrome on macOS **writes the PNG correctly but still exits non-zero** on harmless
  `CVDisplayLinkCreateWithCGDisplay` errors, so the script checks the output file rather than the
  exit code. Don't "fix" it by adding `check=True`.
- The editor pane clips its overflow on purpose. Without `overflow: hidden` the code runs under
  the run console instead of scrolling behind it.

## Keeping the listing honest

Every string in these renders is copied from the plugin's own source — banner text, button
labels, dialog titles, the toolchain list, the cargo output. **If you change UI copy, change it
here and re-render**, or the listing drifts from the product.

The sample file is valid Rust and deliberately exercises what the lexer handles specially: `//!`
and `///` doc comments, attributes, lifetimes (vs char literals), raw strings, macros, and
suffixed hex literals.

## What is deliberately missing

python-portable's set includes completion, go-to-declaration, quick documentation and error
diagnostics. Those are all rust-analyzer features, and they are **not** depicted here because
they had not been exercised end-to-end when these were made — only the toolchain download, the
run paths, the two editor banners and the settings panel were confirmed in a sandbox IDE.

Add them once rust-analyzer has actually been seen running (install the component, install
LSP4IJ, restart, confirm completion and go-to-definition work), not before.

One caveat on `01-syntax-highlighting.png`: the lifetime colour (`'a`, rendered `#9876AA`) is a
reconstruction. `RustTokenTypes.LIFETIME` maps to `DefaultLanguageHighlighterColors.LABEL`, and
the Darcula value for `LABEL` isn't a loose resource in the IDE distribution, so it couldn't be
read off. Worth eyeballing in a sandbox and correcting either the render or the mapping.
