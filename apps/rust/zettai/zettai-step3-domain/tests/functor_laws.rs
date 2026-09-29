//! `Result` がファンクタであることを、性質テストで確かめる（第 7 章）。
//!
//! **`map` は言語にある。** 書くのは法則の確認だけ。
//! それでも確かめる価値があるのは、**この連載が自前で `map` を書く章
//! （Kotlin 版・なでしこ3 版の第 7 章）と同じ法則を、同じやり方で
//! 測っている**ことを見せるため。
//!
//! 乱数は自前（[ADR-017](../../../../docs/adr/ADR-017-own-property-testing.md)）。

use zettai_step3_domain::{ListName, ToDoItem, ToDoList, User, ZettaiError};

/// 乱数。xorshift64。**依存を足さないために自前で書く。**
struct Rng(u64);

impl Rng {
    fn new(seed: u64) -> Self {
        Rng(seed | 1)
    }
    fn next(&mut self) -> u64 {
        self.0 ^= self.0 << 13;
        self.0 ^= self.0 >> 7;
        self.0 ^= self.0 << 17;
        self.0
    }
    fn below(&mut self, n: u64) -> u64 {
        self.next() % n
    }
}

type Outcome = Result<ToDoList, ZettaiError>;

/// 成功も失敗も引く。**失敗だけ／成功だけでは法則を確かめたことにならない。**
fn any_outcome(rng: &mut Rng) -> Outcome {
    let n = rng.below(10);
    if rng.below(4) == 0 {
        Err(ZettaiError::ListNotFound {
            user: User::new("uberto"),
            list_name: ListName::new(&format!("list{n}")),
        })
    } else {
        Ok(ToDoList {
            list_name: ListName::new(&format!("list{n}")),
            items: (0..rng.below(3))
                .map(|i| ToDoItem::new(&format!("item{i}")))
                .collect(),
        })
    }
}

/// 変換 1: 項目を 1 つ足す。
fn add_marker(mut list: ToDoList) -> ToDoList {
    list.items.push(ToDoItem::new("marker"));
    list
}

/// 変換 2: 名前を変える。
fn rename(mut list: ToDoList) -> ToDoList {
    list.list_name = ListName::new(&format!("{}!", list.list_name.0));
    list
}

const TRIALS: usize = 200;

/// 恒等: `map` に恒等関数を渡すと、何も変わらない。
///
/// **clippy が `map_identity` で止める。** 「恒等関数を `map` するな」は
/// ふだんは正しいが、**ここではそれが確かめたい法則そのもの**。
/// 消すとテストが消える。
#[allow(clippy::map_identity)]
#[test]
fn mapping_identity_changes_nothing() {
    let mut rng = Rng::new(20260929);
    for _ in 0..TRIALS {
        let outcome = any_outcome(&mut rng);
        assert_eq!(outcome.clone().map(|list| list), outcome);
    }
}

/// 合成: 2 回 `map` するのと、合成した関数を 1 回 `map` するのは同じ。
#[test]
fn mapping_twice_equals_mapping_the_composition() {
    let mut rng = Rng::new(31415926);
    for _ in 0..TRIALS {
        let outcome = any_outcome(&mut rng);

        let twice = outcome.clone().map(add_marker).map(rename);
        let once = outcome.map(|list| rename(add_marker(list)));

        assert_eq!(twice, once);
    }
}

/// 失敗のときは `map` が呼ばれない。
#[test]
fn mapping_a_failure_keeps_the_reason() {
    let reason = ZettaiError::ListNotFound {
        user: User::new("uberto"),
        list_name: ListName::new("nope"),
    };
    let failed: Outcome = Err(reason.clone());

    assert_eq!(failed.map(add_marker), Err(reason));
}

// ---------------------------------------------------------------------------
// 検出率。**見逃しのある壊し方で測る**（Unit 3 の学び）。
// ---------------------------------------------------------------------------

/// 壊した `map`。**項目が 2 件のときだけ、変換を飛ばす。**
///
/// 「いつも壊れる」壊し方だと検出率が 1 に張り付き、数えた意味が無くなる
/// （Unit 3 で実際にそうなった）。
fn broken_map(outcome: Outcome, f: fn(ToDoList) -> ToDoList) -> Outcome {
    match outcome {
        Ok(list) if list.items.len() == 2 => Ok(list),
        Ok(list) => Ok(f(list)),
        Err(e) => Err(e),
    }
}

#[test]
fn a_hidden_bug_in_map_is_detected_often_enough() {
    let mut rng = Rng::new(16180339);
    let mut detections = 0;

    for _ in 0..TRIALS {
        let outcome = any_outcome(&mut rng);

        let good = outcome.clone().map(add_marker);
        let bad = broken_map(outcome, add_marker);

        if good != bad {
            detections += 1;
        }
    }

    println!("隠れた欠陥の検出 {detections} / {TRIALS}");

    // 検出するのは「成功していて、項目がちょうど 2 件」のとき。
    //
    //   P(Ok) = 3/4
    //   項目数は below(3) なので 0・1・2 が各 1/3。P(2 件) = 1/3
    //   p = 3/4 × 1/3 = 0.25
    //   期待値 = 200 × 0.25 = 50
    //   σ = √(200 × 0.25 × 0.75) = 6.12
    //
    // **しきい値は期待値から 4σ 下**（50 − 24.5 ≒ 25）に置く。
    // なでしこ3 版は 1.58σ しか離しておらず、CI が確率 5.7% で落ちた。
    assert!(
        detections >= 25,
        "隠れた欠陥を {detections} / {TRIALS} しか検出していない"
    );
}
