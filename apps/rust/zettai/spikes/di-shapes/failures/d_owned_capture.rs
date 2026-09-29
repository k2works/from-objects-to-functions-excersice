// 押された先: 借りずに、所有した値を閉じ込める。
type Transform = Box<dyn Fn(Vec<String>) -> Vec<String>>;

fn make(prefix: &str) -> Transform {
    let owned = prefix.to_string();
    Box::new(move |mut s| { s.push(owned.clone()); s })
}

fn main() {
    let word = String::from("x");
    let f = make(&word);
    assert_eq!(f(Vec::new()), vec!["x".to_string()]);
    println!("ok");
}
