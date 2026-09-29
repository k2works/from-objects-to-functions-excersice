//! `Result` がモナドであることを、性質テストで確かめる（第 9 章）。
//!
//! **`and_then` は言語にある。** Kotlin 版は `Outcome.bind` を、
//! なでしこ3 版は `結果連鎖` を書いた。ここで書くのは法則の確認だけ。
//!
//! 第 9 章でこれが要るのは、**読む・決める・書くの 3 つが続けて失敗しうる**
//! ためです。`?` が短く書けるのは、この法則が成り立っているからです。

use zettai_step5_domain::{ListName, ToDoItem, ToDoList, ZettaiError};

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

fn any_list(rng: &mut Rng) -> ToDoList {
    ToDoList {
        list_name: ListName::new(&format!("list{}", rng.below(10))),
        items: (0..rng.below(3))
            .map(|i| ToDoItem::new(&format!("item{i}")))
            .collect(),
    }
}

fn any_outcome(rng: &mut Rng) -> Outcome {
    if rng.below(4) == 0 {
        Err(ZettaiError::StoreUnavailable {
            detail: format!("detail{}", rng.below(10)),
        })
    } else {
        Ok(any_list(rng))
    }
}

/// 続きの処理 1: 項目を足す。**失敗しうる。**
fn add_one(mut list: ToDoList) -> Outcome {
    if list.items.len() >= 2 {
        return Err(ZettaiError::ListAlreadyExists {
            list_name: list.list_name,
        });
    }
    list.items.push(ToDoItem::new("added"));
    Ok(list)
}

/// 続きの処理 2: 名前を変える。**失敗しうる。**
fn rename(mut list: ToDoList) -> Outcome {
    if list.list_name.0.ends_with('0') {
        return Err(ZettaiError::EmptyDescription);
    }
    list.list_name = ListName::new(&format!("{}!", list.list_name.0));
    Ok(list)
}

const TRIALS: usize = 200;

/// 左恒等: `Ok(x).and_then(f)` は `f(x)` と同じ。
#[test]
fn left_identity() {
    let mut rng = Rng::new(20260929);
    for _ in 0..TRIALS {
        let list = any_list(&mut rng);
        let wrapped: Outcome = Ok(list.clone());
        assert_eq!(wrapped.and_then(add_one), add_one(list));
    }
}

/// 右恒等: `m.and_then(Ok)` は `m` と同じ。
///
/// **clippy が `bind_instead_of_map` で止める。** 「`and_then(Ok)` は
/// no-op だから直接書け」はふだんは正しいが、**ここでは no-op で
/// あること自体が確かめたい法則**。消すとテストが消える。
/// 第 7 章の `map_identity` と同じ形（2 件目）。
#[allow(clippy::bind_instead_of_map)]
#[test]
fn right_identity() {
    let mut rng = Rng::new(31415926);
    for _ in 0..TRIALS {
        let outcome = any_outcome(&mut rng);
        assert_eq!(outcome.clone().and_then(Ok), outcome);
    }
}

/// 結合律: 繋ぐ順序を変えても同じ。
#[allow(clippy::bind_instead_of_map)]
#[test]
fn associativity() {
    let mut rng = Rng::new(27182818);
    for _ in 0..TRIALS {
        let outcome = any_outcome(&mut rng);

        let left = outcome.clone().and_then(add_one).and_then(rename);
        let right = outcome.and_then(|list| add_one(list).and_then(rename));

        assert_eq!(left, right);
    }
}

/// 失敗のあとは続きが呼ばれない。**`?` が短く書ける理由。**
#[allow(clippy::bind_instead_of_map)]
#[test]
fn a_failure_short_circuits() {
    let failed: Outcome = Err(ZettaiError::EmptyDescription);
    let result: Outcome = failed.and_then(|_| panic!("呼ばれてはいけない"));
    assert_eq!(result, Err(ZettaiError::EmptyDescription));
}

// ---------------------------------------------------------------------------
// 検出率。**見逃しのある壊し方で測る。**
// ---------------------------------------------------------------------------

/// 壊した `and_then`。**項目が 1 件のときだけ、続きを飛ばす。**
fn broken_and_then(outcome: Outcome, f: fn(ToDoList) -> Outcome) -> Outcome {
    match outcome {
        Ok(list) if list.items.len() == 1 => Ok(list),
        Ok(list) => f(list),
        Err(e) => Err(e),
    }
}

#[test]
fn a_hidden_bug_is_detected_often_enough() {
    let mut rng = Rng::new(16180339);
    let mut detections = 0;

    for _ in 0..TRIALS {
        let outcome = any_outcome(&mut rng);
        let good = outcome.clone().and_then(add_one);
        let bad = broken_and_then(outcome, add_one);
        if good != bad {
            detections += 1;
        }
    }

    println!("隠れた欠陥の検出 {detections} / {TRIALS}");

    // 検出するのは「成功していて、項目がちょうど 1 件」のとき。
    //
    //   P(Ok) = 3/4、項目数は below(3) なので 0・1・2 が各 1/3
    //   p = 3/4 × 1/3 = 0.25
    //   期待値 = 200 × 0.25 = 50
    //   σ = √(200 × 0.25 × 0.75) = 6.12
    //
    // **しきい値は期待値から 4σ 下**（25）。
    // 第 8 章では実測が 1.8σ 上に出た。**σ のぶんの余裕は実際に要る。**
    assert!(
        detections >= 25,
        "隠れた欠陥を {detections} / {TRIALS} しか検出していない"
    );
}
