// 案 B（Box<dyn Fn>）で落ちると、バックトレースに何が残るか。
fn run(f: &dyn Fn(i32) -> i32) -> i32 { f(1) }
fn main() {
    let boom: Box<dyn Fn(i32) -> i32> = Box::new(|n: i32| -> i32 { panic!("ここで落ちた: {n}") });
    println!("{}", run(boom.as_ref()));
}
