// 関数値の中で落ちたとき、どこで落ちたと表示されるか。
fn run(f: impl Fn(i32) -> i32) -> i32 { f(1) }
fn main() {
    let doubled = |n: i32| n * 2;
    let boom = |n: i32| -> i32 { panic!("ここで落ちた: {n}") };
    println!("{}", run(doubled));
    println!("{}", run(boom));
}
