// 案 A: イベントの種類が違うと、クロージャの型も違う。
// 同じ Vec に入れられるか？
fn add(w: &'static str) -> impl Fn(Vec<String>) -> Vec<String> {
    move |mut s| { s.push(w.to_string()); s }
}
fn clear() -> impl Fn(Vec<String>) -> Vec<String> {
    |_s| Vec::new()
}
fn main() {
    let fs = vec![add("x"), clear()];
    let _ = fs;
}
