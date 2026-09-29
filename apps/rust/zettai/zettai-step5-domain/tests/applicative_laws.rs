//! `Validated` がアプリカティブであることを、性質テストで確かめる（第 11 章）。
//!
//! あわせて、**モナド（`Result`）との差を数字で示します**。
//! 「理由を溜める」ことに意味があるのは、**両方だめな入力があるとき**だけです。

use zettai_step5_domain::validation::{unit, Validated};
use zettai_step5_domain::ZettaiError;

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

fn any_reason(rng: &mut Rng) -> ZettaiError {
    match rng.below(3) {
        0 => ZettaiError::EmptyUserName,
        1 => ZettaiError::EmptyListName,
        _ => ZettaiError::ListNameTooLong { limit: 40 },
    }
}

/// **失敗を 1/2 で引く。** 両方だめな組が十分に出るようにする。
fn any(rng: &mut Rng) -> Validated<u64> {
    if rng.below(2) == 0 {
        Validated::invalid(any_reason(rng))
    } else {
        Validated::valid(rng.below(100))
    }
}

const TRIALS: usize = 200;

/// 恒等: 単位元と合わせても中身が変わらない。
#[test]
fn unit_is_an_identity() {
    let mut rng = Rng::new(20260929);
    for _ in 0..TRIALS {
        let v = any(&mut rng);
        let zipped = v.clone().zip(unit()).map(|(a, ())| a);
        assert_eq!(zipped, v);
    }
}

/// 結合律: 合わせる順序を変えても、中身と理由の集まりが同じ。
#[test]
fn zipping_is_associative() {
    let mut rng = Rng::new(31415926);
    for _ in 0..TRIALS {
        let (a, b, c) = (any(&mut rng), any(&mut rng), any(&mut rng));

        let left = a
            .clone()
            .zip(b.clone())
            .zip(c.clone())
            .map(|((x, y), z)| (x, y, z));
        let right = a.zip(b.zip(c)).map(|(x, (y, z))| (x, y, z));

        assert_eq!(left, right);
    }
}

/// ファンクタ則: `map` は形を変えない。
#[test]
fn mapping_keeps_the_shape() {
    let mut rng = Rng::new(27182818);
    for _ in 0..TRIALS {
        let v = any(&mut rng);
        let twice = v.clone().map(|n| n + 1).map(|n| n * 2);
        let once = v.map(|n| (n + 1) * 2);
        assert_eq!(twice, once);
    }
}

/// **理由が落ちない。** 両方だめなら両方返る。
#[test]
fn no_reason_is_dropped() {
    let mut rng = Rng::new(16180339);
    for _ in 0..TRIALS {
        let (a, b) = (any(&mut rng), any(&mut rng));
        let expected = a.reasons().len() + b.reasons().len();
        let zipped = a.zip(b);
        assert_eq!(zipped.reasons().len(), expected);
    }
}

// ---------------------------------------------------------------------------
// モナドとの差。**溜めることに意味があるのは、両方だめなときだけ。**
// ---------------------------------------------------------------------------

/// モナドのやり方。**最初の失敗で止まる。**
fn like_a_monad(a: &Validated<u64>, b: &Validated<u64>) -> Vec<ZettaiError> {
    if !a.is_valid() {
        return a.reasons().to_vec();
    }
    b.reasons().to_vec()
}

#[test]
fn a_monad_drops_reasons_only_when_both_are_bad() {
    let mut rng = Rng::new(14142135);
    let mut both_bad = 0;
    let mut dropped = 0;

    for _ in 0..TRIALS {
        let (a, b) = (any(&mut rng), any(&mut rng));
        let applicative = a.clone().zip(b.clone()).reasons().len();
        let monad = like_a_monad(&a, &b).len();

        if !a.is_valid() && !b.is_valid() {
            both_bad += 1;
        }
        if monad < applicative {
            dropped += 1;
        }
    }

    println!("両方だめ {both_bad} / {TRIALS}、モナドが理由を落とした {dropped} / {TRIALS}");

    // **落とすのは「両方だめ」のときだけ。** 片方だけなら結果は同じ
    assert_eq!(
        dropped, both_bad,
        "モナドが理由を落とすのは、両方だめなときと 1 対 1 で対応する"
    );

    // 失敗は 1/2 で引くので、両方だめは 1/4。
    //   期待値 = 200 × 0.25 = 50、σ = √(200 × 0.25 × 0.75) = 6.12
    // **しきい値は期待値から 4σ 下**（25）。
    assert!(
        both_bad >= 25,
        "両方だめな組が {both_bad} 件しか出ていない。生成する入力が偏っている"
    );
}

/// **壊したら検出するか。** `zip` が片方の理由を捨てるようにする。
fn broken_zip(a: Validated<u64>, b: Validated<u64>) -> Vec<ZettaiError> {
    if !a.is_valid() {
        return a.reasons().to_vec();
    }
    b.reasons().to_vec()
}

#[test]
fn breaking_zip_is_detected_often_enough() {
    let mut rng = Rng::new(17320508);
    let mut detections = 0;

    for _ in 0..TRIALS {
        let (a, b) = (any(&mut rng), any(&mut rng));
        let good = a.clone().zip(b.clone()).reasons().len();
        let bad = broken_zip(a, b).len();
        if good != bad {
            detections += 1;
        }
    }

    println!("壊した zip の検出 {detections} / {TRIALS}");

    // 検出するのは「両方だめ」のときだけ。p = 1/2 × 1/2 = 0.25
    //   期待値 = 50、σ = 6.12。**しきい値は 4σ 下（25）**
    //
    // **200 回だから安定して捕まえられる。** 20 回なら期待値 5 で、
    // 1 度も引かない確率が 0.75^20 = 0.32%、
    // **1 件以下に留まる確率が 2.43%** ある。
    assert!(
        detections >= 25,
        "壊した zip を {detections} / {TRIALS} しか検出していない"
    );
}
