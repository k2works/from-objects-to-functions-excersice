//! `Converter` がプロファンクタであることを、性質テストで確かめる（第 12 章）。
//!
//! **入力側と出力側で向きが逆**なので、写す関数も逆向きに要ります。
//! 法則が言うのは「**写しても対であることが保たれる**」ことです。

use zettai_step5_domain::converter::Converter;
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

/// 数と文字列の対。
fn digits() -> Converter<i64, String> {
    Converter::new(
        |n: &i64| n.to_string(),
        |s: &String| {
            s.parse::<i64>().map_err(|_| ZettaiError::StoreUnavailable {
                detail: format!("数として読めない: {s}"),
            })
        },
    )
}

const TRIALS: usize = 200;

/// 恒等: 恒等関数で写しても、何も変わらない。
#[test]
fn dimap_with_identities_changes_nothing() {
    let mut rng = Rng::new(20260929);
    let plain = digits();
    let mapped = digits().dimap(|n: &i64| *n, |n| n, |s| s, |s: &String| s.clone());

    for _ in 0..TRIALS {
        let n = rng.below(1000) as i64;
        assert_eq!(plain.write(&n), mapped.write(&n));
        let text = plain.write(&n);
        assert_eq!(plain.read(&text).unwrap(), mapped.read(&text).unwrap());
    }
}

/// 合成: 2 回写すのと、合成して 1 回写すのは同じ。
#[test]
fn mapping_twice_equals_mapping_the_composition() {
    let mut rng = Rng::new(31415926);

    // 2 回写す: i32 → i64、そのあと String → 角括弧つき
    let twice = digits().contramap(|n: &i32| *n as i64, |n| n as i32).map(
        |s| format!("[{s}]"),
        |s: &String| s.trim_matches(|c| c == '[' || c == ']').to_string(),
    );

    // 1 回で写す
    let once = digits().dimap(
        |n: &i32| *n as i64,
        |n| n as i32,
        |s| format!("[{s}]"),
        |s: &String| s.trim_matches(|c| c == '[' || c == ']').to_string(),
    );

    for _ in 0..TRIALS {
        let n = rng.below(1000) as i32;
        assert_eq!(twice.write(&n), once.write(&n));
        let text = twice.write(&n);
        assert_eq!(twice.read(&text).unwrap(), once.read(&text).unwrap());
    }
}

/// **対であることが保たれる。** 写しても往復で戻る。
#[test]
fn the_pairing_survives_mapping() {
    let mut rng = Rng::new(27182818);
    let c = digits().dimap(
        |n: &i32| *n as i64,
        |n| n as i32,
        |s| format!("[{s}]"),
        |s: &String| s.trim_matches(|c| c == '[' || c == ']').to_string(),
    );

    for _ in 0..TRIALS {
        let n = rng.below(1000) as i32;
        assert_eq!(c.read(&c.write(&n)).unwrap(), n);
    }
}

// ---------------------------------------------------------------------------
// 検出率。**見逃しのある壊し方で測る。**
// ---------------------------------------------------------------------------

/// **書き側だけ直した対。** 第 9 章で実際にやったこと。
///
/// 3 桁の数だけ、書くときに空白を足す。読み側は直っていない。
fn half_fixed() -> Converter<i64, String> {
    Converter::new(
        |n: &i64| {
            let text = n.to_string();
            if text.len() == 3 {
                format!(" {text}")
            } else {
                text
            }
        },
        |s: &String| {
            s.parse::<i64>().map_err(|_| ZettaiError::StoreUnavailable {
                detail: format!("数として読めない: {s}"),
            })
        },
    )
}

#[test]
fn a_half_fixed_pair_is_detected_often_enough() {
    let mut rng = Rng::new(16180339);
    let broken = half_fixed();
    let mut detections = 0;

    for _ in 0..TRIALS {
        let n = rng.below(1000) as i64;
        if broken.read(&broken.write(&n)).is_err() {
            detections += 1;
        }
    }

    println!("片方だけ直した対の検出 {detections} / {TRIALS}");

    // 検出するのは 3 桁のとき。0..999 のうち 100..999 が 900 通り。
    //
    //   p = 900 / 1000 = 0.9
    //   期待値 = 200 × 0.9 = 180
    //   σ = √(200 × 0.9 × 0.1) = 4.24
    //
    // **しきい値は期待値から 4σ 下**（180 − 17 = 163）。
    assert!(
        detections >= 163,
        "片方だけ直した対を {detections} / {TRIALS} しか検出していない"
    );
}

/// **壊していないときは 0 件。** 検出率のテストが「常に検出する」だけでは
/// 何も言えない。
#[test]
fn an_unbroken_pair_is_never_detected() {
    let mut rng = Rng::new(14142135);
    let good = digits();
    for _ in 0..TRIALS {
        let n = rng.below(1000) as i64;
        assert!(good.read(&good.write(&n)).is_ok());
    }
}
