#!/usr/bin/env python3
"""
Generates the JetBrains Marketplace screenshots for Rust Portable.

Renders each screen as a 1200x760 HTML page, then screenshots it with headless
Chrome at device-scale 2 to produce the 2400x1520 PNGs the other Portable
plugins use (see python-portable/media/marketplace).

    python3 build.py

Every string shown here is copied from the plugin's own source — banner text,
button labels, dialog titles, the toolchain list. If you change UI copy, change
it here too or the listing drifts from the product.
"""

import html
import pathlib
import shutil
import subprocess
import sys

SRC = pathlib.Path(__file__).resolve().parent
OUT = SRC.parent / "marketplace"
WORK = SRC / ".render"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

# --- the sample file shown in the editor -------------------------------------
# Valid Rust, chosen to exercise exactly the constructs the lexer handles
# specially: doc comments (//! and ///), attributes, lifetimes vs char
# literals, raw strings, macros, and suffixed hex literals.
CODE = [
    ('<span class="d">//! Formats user greetings.</span>', ""),
    ("", ""),
    ('<span class="a">#[derive(</span><span class="t">Debug, Clone</span><span class="a">)]</span>', ""),
    ('<span class="k">pub struct</span> <span class="t">Greeter</span>&lt;<span class="lt">\'a</span>&gt; {', ""),
    ('    prefix: &amp;<span class="lt">\'a</span> <span class="k">str</span>,', ""),
    ('    times: <span class="k">u8</span>,', ""),
    ("}", ""),
    ("", ""),
    ('<span class="k">impl</span>&lt;<span class="lt">\'a</span>&gt; <span class="t">Greeter</span>&lt;<span class="lt">\'a</span>&gt; {', ""),
    ('    <span class="k">pub fn</span> <span class="f">new</span>(prefix: &amp;<span class="lt">\'a</span> <span class="k">str</span>) -&gt; <span class="k">Self</span> {', ""),
    ('        <span class="t">Greeter</span> { prefix, times: <span class="n">3</span> }', ""),
    ("    }", ""),
    ("", ""),
    ('    <span class="d">/// Greets `name`, trimming surrounding whitespace.</span>', ""),
    ('    <span class="k">pub fn</span> <span class="f">greet</span>(&amp;<span class="k">self</span>, name: &amp;<span class="k">str</span>) -&gt; <span class="t">String</span> {', ""),
    ('        <span class="f">format!</span>(<span class="s">"{}, {}!"</span>, <span class="k">self</span>.prefix, name.<span class="f">trim</span>())', ""),
    ("    }", ""),
    ("}", ""),
    ("", ""),
    ('<span class="k">fn</span> <span class="f">main</span>() {', "run"),
    ('    <span class="k">let</span> greeter = <span class="t">Greeter</span>::<span class="f">new</span>(<span class="s">"Hello"</span>);', ""),
    ('    <span class="k">let</span> raw = <span class="s">r#"raw strings keep "quotes" verbatim"#</span>;', ""),
    ("", ""),
    ('    <span class="k">for</span> n <span class="k">in</span> [<span class="s">"Rust"</span>, <span class="s">"Portable"</span>] {', ""),
    ('        <span class="f">println!</span>(<span class="s">"{}"</span>, greeter.<span class="f">greet</span>(n));', ""),
    ("    }", ""),
    ('    <span class="f">println!</span>(<span class="s">"{raw}  {:#x}"</span>, <span class="n">0xFF_u32</span>);', ""),
    ("}", ""),
]

TREE = """
      <div class="tree">
        <div class="hdr">PROJECT</div>
        <div class="row"><span class="ic ic-folder">▾</span>greeter</div>
        <div class="row nest"><span class="ic ic-folder">▸</span>src</div>
        <div class="row sel" style="padding-left:54px"><span class="ic ic-rs">R</span>main.rs</div>
        <div class="row nest"><span class="ic ic-file">☰</span>Cargo.toml</div>
      </div>
"""


def editor(rows=CODE, banner="", console="", extra=""):
    gutter, code = [], []
    for i, (line, mark) in enumerate(rows, 1):
        gutter.append(f'<span class="run">▶</span>' if mark == "run" else str(i))
        code.append(line if line else "&nbsp;")
    return f"""
      <div class="main">
        <div class="tabs"><div class="tab"><span class="ic ic-rs">R</span>main.rs</div></div>
        {banner}
        <div class="editor">
          <div class="gutter">{"<br>".join(gutter)}</div>
          <div class="code">{"<br>".join(code)}</div>
          {extra}
        </div>
        {console}
      </div>
"""


def page(title, inner, footer=None):
    """footer=None gives the editor status bar; pass HTML for a dialog button row."""
    bottom = footer if footer is not None else (
        '<div class="status"><span>Rust 1.98.1</span><span>UTF-8</span></div>'
    )
    return f"""<!doctype html>
<html><head><meta charset="utf-8"><link rel="stylesheet" href="shell.css"></head>
<body><div class="window">
  <div class="titlebar">
    <div class="lights"><i class="light red"></i><i class="light amber"></i><i class="light green"></i></div>
    <div class="title">{html.escape(title)}</div>
  </div>
  <div class="body">{inner}</div>
  {bottom}
</div></body></html>
"""


SETTINGS_FOOTER = """
  <div class="sfooter">
    <div class="btn">Cancel</div><div class="btn">Apply</div><div class="btn pri">OK</div>
  </div>
"""


BANNER_SETUP = """
        <div class="banner">
          <span class="info">i</span>
          <span>No Rust toolchain configured — download a portable one to run this file.</span>
          <span class="spacer"></span>
          <a>Download Rust…</a><a>Add from Disk…</a><a>Settings…</a>
        </div>
"""

BANNER_CI = """
        <div class="banner">
          <span class="info">i</span>
          <span>Turn on Rust code intelligence — completion, go-to-definition and error highlighting.</span>
          <span class="spacer"></span>
          <a>Enable code intelligence</a><a>Settings…</a><a>Don't show again</a>
        </div>
"""

CONSOLE = """
        <div class="console">
          <div class="ch"><span style="color:#3f9c35">▶</span><span>Run:</span><span style="color:#d6d6d6">main.rs</span></div>
          <div class="cb"><span class="dim">   Compiling</span> greeter v0.1.0 (/Users/you/greeter)
<span class="dim">    Finished</span> `dev` profile [unoptimized + debuginfo] target(s) in 0.61s
<span class="dim">     Running</span> `target/debug/greeter`
Hello, Rust!
Hello, Portable!
raw strings keep "quotes" verbatim  0xff

<span class="dim">Process finished with exit code 0</span></div>
        </div>
"""

DIALOG_DOWNLOAD = """
          <div class="scrim"></div>
          <div class="dialog" style="width:470px">
            <div class="dh">Download Rust Toolchain</div>
            <div class="dc">
              <div class="row2">
                <label>Toolchain:</label>
                <div class="field combo"><span>Rust stable  (1.98.1)  ·  mac/aarch64</span><span class="caret">▾</span></div>
              </div>
              <div class="hint" style="margin-left:98px">Downloaded to ~/.rust-portable and registered as a Rust SDK.</div>
            </div>
            <div class="df"><div class="btn">Cancel</div><div class="btn pri">OK</div></div>
          </div>
"""

DIALOG_CARGO = """
          <div class="scrim"></div>
          <div class="dialog" style="width:430px">
            <div class="dh">Run Cargo Tool</div>
            <div class="dc">
              <div class="row2">
                <label>Command:</label>
                <div class="field combo" style="flex:0 0 190px"><span>clippy</span><span class="caret">▾</span></div>
              </div>
            </div>
            <div class="df"><div class="btn">Cancel</div><div class="btn pri">OK</div></div>
          </div>
"""

SETTINGS_TREE = """
      <div class="stree">
        <div class="trow"><span class="tw">▸</span>Appearance &amp; Behavior</div>
        <div class="trow"><span class="tw"></span>Keymap</div>
        <div class="trow"><span class="tw">▸</span>Editor</div>
        <div class="trow"><span class="tw"></span>Plugins</div>
        <div class="trow"><span class="tw">▾</span>Languages &amp; Frameworks</div>
        <div class="trow sel" style="padding-left:52px"><span class="ic ic-rs" style="margin-right:8px">R</span>Rust Portable</div>
        <div class="trow" style="padding-left:52px">Markdown</div>
        <div class="trow"><span class="tw">▸</span>Build, Execution, Deployment</div>
      </div>
"""

SETTINGS = """
      <div class="settings">
        <div class="sh">Rust Portable</div>
        <div class="sb">
          <div class="sdesc">Rust Portable toolchains. Downloads are stored under <span style="color:#cc7832">~/.rust-portable</span> and shared with Rust run configurations.</div>
          <div class="list">
            <div class="li sel">Rust 1.98.1   —   ~/.rust-portable/rust-stable</div>
            <div class="li">Rust 1.97.0   —   ~/.rust-portable/rust-1.97.0</div>
          </div>
          <div class="btnrow">
            <div class="btn">Download Rust…</div><div class="btn">Add from Disk…</div>
            <div class="btn">Remove</div><div class="btn">Clean Up</div><div class="btn">Open Folder</div>
          </div>
          <div class="sep"></div>
          <div class="check"><span class="box">✓</span>Code intelligence (completion, navigation, errors)
            <span style="margin-left:14px"><span class="btn">Reinstall rust-analyzer…</span></span>
          </div>
          <div class="sdesc" style="margin-top:6px">Runs the official Rust language server (rust-analyzer) on the selected toolchain — fully offline. Also installs the free <b style="color:#bbbbbb">LSP4IJ</b> plugin (one click, may prompt a restart).</div>
        </div>
      </div>
"""

SCREENS = [
    ("01-syntax-highlighting", "main.rs — greeter", TREE + editor(), None),
    ("02-run-a-file", "main.rs — greeter", TREE + editor(console=CONSOLE), None),
    ("03-download-toolchain", "main.rs — greeter", TREE + editor(extra=DIALOG_DOWNLOAD), None),
    ("04-setup-banner", "main.rs — greeter", TREE + editor(banner=BANNER_SETUP), None),
    ("05-code-intelligence", "main.rs — greeter", TREE + editor(banner=BANNER_CI), None),
    ("06-settings", "Settings", SETTINGS_TREE + SETTINGS, SETTINGS_FOOTER),
    ("07-cargo-tools", "main.rs — greeter", TREE + editor(extra=DIALOG_CARGO), None),
]


def main():
    if not pathlib.Path(CHROME).exists():
        sys.exit(f"Chrome not found at {CHROME}")
    WORK.mkdir(exist_ok=True)
    shutil.copy(SRC / "shell.css", WORK / "shell.css")
    OUT.mkdir(parents=True, exist_ok=True)

    for name, title, inner, footer in SCREENS:
        (WORK / f"{name}.html").write_text(page(title, inner, footer))

    for name, *_ in SCREENS:
        png = OUT / f"{name}.png"
        png.unlink(missing_ok=True)
        # Not check=True: headless Chrome on macOS writes the PNG correctly but still
        # exits non-zero on harmless CVDisplayLinkCreateWithCGDisplay errors. The
        # output file is the real success signal.
        subprocess.run(
            [CHROME, "--headless", "--disable-gpu", "--hide-scrollbars",
             "--force-device-scale-factor=2", "--window-size=1200,760",
             f"--screenshot={png}", str(WORK / f"{name}.html")],
            capture_output=True,
        )
        if not png.exists() or png.stat().st_size < 10_000:
            sys.exit(f"render failed: {name}")
        print(f"  rendered {name}.png")

    shutil.rmtree(WORK, ignore_errors=True)
    print(f"\n{len(SCREENS)} screenshots -> {OUT}")


if __name__ == "__main__":
    main()
