// 案 C: 合成を型で表すと、並びの長さが型に出る。
// 実行時に決まる長さのイベント列を畳み込めるか？
trait Transform { fn apply(&self, s: Vec<String>) -> Vec<String>; }
struct Composed<F, G>(F, G);
impl<F: Transform, G: Transform> Transform for Composed<F, G> {
    fn apply(&self, s: Vec<String>) -> Vec<String> { self.1.apply(self.0.apply(s)) }
}
struct Identity;
impl Transform for Identity { fn apply(&self, s: Vec<String>) -> Vec<String> { s } }
struct Add(&'static str);
impl Transform for Add {
    fn apply(&self, mut s: Vec<String>) -> Vec<String> { s.push(self.0.to_string()); s }
}
fn main() {
    let events = vec![Add("x"), Add("y"), Add("z")];
    // 畳み込んで 1 つの変換にしたい
    let folded = events.into_iter().fold(Identity, |acc, e| Composed(acc, e));
    let _ = folded.apply(Vec::new());
}
