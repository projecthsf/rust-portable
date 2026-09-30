// A standalone .rs file with no Cargo.toml — Rust Portable compiles it with rustc
// and runs the binary. Click the green ▶ in the gutter next to fn main.

#[derive(Debug)]
struct Greeting<'a> {
    name: &'a str,
    times: u8,
}

impl<'a> Greeting<'a> {
    fn new(name: &'a str) -> Self {
        Greeting { name, times: 3 }
    }

    /// Doc comments highlight differently from ordinary ones.
    fn say(&self) {
        for i in 1..=self.times {
            println!("{i}. Hello, {}!", self.name);
        }
    }
}

fn main() {
    let raw = r#"raw strings keep "quotes" and \backslashes verbatim"#;
    let bytes = b"byte string";
    let hex = 0xFF_u32;
    let float = 1_234.5e-2;

    let g = Greeting::new("world");
    g.say();

    println!("{raw}");
    println!("{} bytes, hex={hex}, float={float}", bytes.len());
    println!("{g:?}");
}
