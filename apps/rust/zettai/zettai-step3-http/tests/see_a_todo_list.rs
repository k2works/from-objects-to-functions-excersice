//! 受け入れシナリオ。
//!
//! **業務の言葉だけで書く。** HTTP もドメインの関数名も出てこない。
//! 同じシナリオを全経路で走らせ、片方だけ落ちたらそこに業務のロジックが
//! 漏れていると判断する（[ADR-016]）。

use zettai_step3_http::acceptance::{all_routes, ZettaiActions};

#[test]
fn every_route_tells_the_same_story() {
    for actions in all_routes() {
        uberto_sees_his_book_list(actions.as_ref());
        an_empty_list_is_still_a_list(actions.as_ref());
        an_unknown_list_is_not_found(actions.as_ref());
    }
}

fn uberto_sees_his_book_list(actions: &dyn ZettaiActions) {
    // 期待値は `&str` で並べ、比べる直前に `String` へ揃える。
    // `Vec<String>` に `&[&str]` はそのまま比べられない（E0308）。
    let expected = ["write chapter", "insert code", "publish book"].map(String::from);

    assert_eq!(
        actions.items_of("uberto", "book"),
        Some(expected.to_vec()),
        "{}: uberto は book の項目を 3 件見られる",
        actions.route()
    );
}

fn an_empty_list_is_still_a_list(actions: &dyn ZettaiActions) {
    assert_eq!(
        actions.items_of("uberto", "shopping"),
        Some(vec![]),
        "{}: 空のリストも見られる",
        actions.route()
    );
}

fn an_unknown_list_is_not_found(actions: &dyn ZettaiActions) {
    assert_eq!(
        actions.items_of("uberto", "nope"),
        None,
        "{}: 無いリストは見つからない",
        actions.route()
    );
}

// ---------------------------------------------------------------------------
// 第 4 章のシナリオ。**まだ通らない**（3-1.1 で Red にした）。
// ---------------------------------------------------------------------------

#[test]
fn every_route_can_add_an_item() {
    for actions in all_routes() {
        uberto_adds_an_item_to_his_book_list(actions.as_ref());
    }
}

fn uberto_adds_an_item_to_his_book_list(actions: &dyn ZettaiActions) {
    let before = actions.items_of("uberto", "book").expect("book はある");

    actions.add_item("uberto", "book", "review chapter");

    let after = actions.items_of("uberto", "book").expect("book はある");
    assert_eq!(
        after.len(),
        before.len() + 1,
        "{}: 足した分だけ増える",
        actions.route()
    );
    assert!(
        after.contains(&"review chapter".to_string()),
        "{}: 足した項目が見える",
        actions.route()
    );
}
