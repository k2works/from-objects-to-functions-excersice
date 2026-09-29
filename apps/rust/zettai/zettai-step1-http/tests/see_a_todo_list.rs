//! 受け入れシナリオ（第 2 章）。
//!
//! 業務の言葉で書く。第 2 章はドメインを直接呼ぶ 1 経路だけで、
//! 第 3 章で経路を抽象化して HTTP 経由を足す。

use zettai_step1_http::{fetch_list, ListName, User};

#[test]
fn uberto_sees_his_book_list() {
    // 一度変数に受ける。`fetch_list(..).expect(..).items.iter()` と繋ぐと
    // 一時値が借用中に破棄される（E0716）。**所有権に押された 1 件目。**
    let list =
        fetch_list(&User::new("uberto"), &ListName::new("book")).expect("book のリストがある");

    let descriptions: Vec<&str> = list
        .items
        .iter()
        .map(|item| item.description.as_str())
        .collect();

    assert_eq!(
        descriptions,
        vec!["write chapter", "insert code", "publish book"]
    );
}

#[test]
fn an_unknown_list_is_not_found() {
    assert!(fetch_list(&User::new("uberto"), &ListName::new("nope")).is_none());
}
