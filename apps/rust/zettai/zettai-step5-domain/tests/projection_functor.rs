//! 射影の `map` がファンクタであることを確かめる（第 8 章）。
//!
//! 第 7 章の `Result` と同じ形。**入れ物が変わっても法則は同じ**、
//! というのがファンクタの主張なので、2 つ確かめて初めて主張が立つ。

use zettai_step5_domain::projection::{ListSummary, Summaries};

/// 乱数。xorshift64。**依存を足さない**（[ADR-017]）。
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

fn any_summaries(rng: &mut Rng) -> Summaries {
    let count = rng.below(4) as usize;
    Summaries(
        (0..count)
            .map(|_| ListSummary {
                list_name: format!("list{}", rng.below(10)),
                item_count: rng.below(3) as usize,
            })
            .collect(),
    )
}

fn bump(mut s: ListSummary) -> ListSummary {
    s.item_count += 1;
    s
}

fn shout(mut s: ListSummary) -> ListSummary {
    s.list_name = format!("{}!", s.list_name);
    s
}

const TRIALS: usize = 200;

#[test]
fn mapping_identity_changes_nothing() {
    let mut rng = Rng::new(20260929);
    for _ in 0..TRIALS {
        let summaries = any_summaries(&mut rng);
        assert_eq!(summaries.clone().map(|s| s), summaries);
    }
}

#[test]
fn mapping_twice_equals_mapping_the_composition() {
    let mut rng = Rng::new(31415926);
    for _ in 0..TRIALS {
        let summaries = any_summaries(&mut rng);
        let twice = summaries.clone().map(bump).map(shout);
        let once = summaries.map(|s| shout(bump(s)));
        assert_eq!(twice, once);
    }
}

/// 壊した `map`。**件数が 2 のときだけ、変換を飛ばす。**
fn broken_map(s: Summaries, f: fn(ListSummary) -> ListSummary) -> Summaries {
    if s.0.len() == 2 {
        s
    } else {
        s.map(f)
    }
}

#[test]
fn a_hidden_bug_in_map_is_detected_often_enough() {
    let mut rng = Rng::new(16180339);
    let mut detections = 0;

    for _ in 0..TRIALS {
        let summaries = any_summaries(&mut rng);
        let good = summaries.clone().map(bump);
        let bad = broken_map(summaries, bump);
        if good != bad {
            detections += 1;
        }
    }

    println!("隠れた欠陥の検出 {detections} / {TRIALS}");

    // 検出するのは「件数がちょうど 2」のとき。件数は below(4) なので
    // 0・1・2・3 が各 1/4。**ただし件数 0 のときは `map` しても変わらない**
    // ので、そもそも壊しても検出できない。
    //
    //   p = P(件数 == 2) = 0.25
    //   期待値 = 200 × 0.25 = 50
    //   σ = √(200 × 0.25 × 0.75) = 6.12
    //
    // **しきい値は期待値から 4σ 下**（50 − 24.5 ≒ 25）。
    //
    // **実測は 61 で、期待値から 1.8σ 上**。偏りを疑って、種を 20 通り
    // 変えて数え直した（平均 50.1）。**生成器の偏りではなく、
    // この種での揺れ**だった。
    //
    // 1.8σ は 2 回に 1 回は出ない程度だが、20 回まわせば何度か出る。
    // **しきい値を 1.58σ に置くとこういう揺れで落ちる**（なでしこ3 版
    // Unit 6 で実際に落ちた）。4σ 取ってある理由がこれ。
    assert!(
        detections >= 25,
        "隠れた欠陥を {detections} / {TRIALS} しか検出していない"
    );
}
