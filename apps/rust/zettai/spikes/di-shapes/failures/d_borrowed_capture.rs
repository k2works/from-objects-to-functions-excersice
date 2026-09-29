// スパイク 4: 外の値を借りたクロージャを Box に詰めて持ち回れるか。
type Transform = Box<dyn Fn(Vec<String>) -> Vec<String>>;

fn make(prefix: &str) -> Transform {
    Box::new(move |mut s| { s.push(prefix.to_string()); s })
}

fn main() {
    let word = String::from("x");
    let f = make(&word);
    let _ = f(Vec::new());
}
