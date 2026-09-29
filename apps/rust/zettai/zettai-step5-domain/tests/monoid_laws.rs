//! 状態変換の合成がモノイドであることを、性質テストで確かめる（第 5 章）。
//!
//! **既製品を先に調べた。** `proptest` は依存 38・ビルド 26.77 秒で
//! `just check` の上限 20 秒を単独で超える。`quickcheck` は依存 21・12.82 秒で
//! 収まるが、**得るのは生成器と縮小だけ**で、検出率を測るときに縮小は使わない
//! （[ADR-017](../../../../docs/adr/ADR-017-own-property-testing.md)）。
//!
//! **法則が通ることだけでは信用しない。** 壊して、何回検出するかを数える。

use zettai_step5_domain::{
    compose, empty_list, identity, transform_for, ListName, ToDoItem, ToDoList, ToDoListEvent,
    Transform,
};

/// 乱数。**依存を足さないために自前で書く。**
///
/// xorshift64。種を握るので、落ちたときに同じ並びを作り直せる。
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

/// イベントを 1 つ作る。
fn any_event(rng: &mut Rng) -> ToDoListEvent {
    let n = rng.below(1000);
    if rng.below(4) == 0 {
        ToDoListEvent::ListCreated {
            list_name: ListName::new(&format!("list{n}")),
        }
    } else {
        ToDoListEvent::ItemAdded {
            item: ToDoItem::new(&format!("item{n}")),
        }
    }
}

/// 状態を 1 つ作る。
fn any_list(rng: &mut Rng) -> ToDoList {
    let count = rng.below(4) as usize;
    ToDoList {
        list_name: ListName::new(&format!("start{}", rng.below(100))),
        items: (0..count)
            .map(|i| ToDoItem::new(&format!("before{i}")))
            .collect(),
    }
}

const TRIALS: usize = 200;

/// 単位元: `identity` を前に繋いでも後ろに繋いでも、結果が変わらない。
#[test]
fn identity_is_a_unit() {
    let mut rng = Rng::new(20260929);
    for _ in 0..TRIALS {
        let list = any_list(&mut rng);
        let event = any_event(&mut rng);

        let plain = transform_for(event.clone())(list.clone());
        let left = compose(identity(), transform_for(event.clone()))(list.clone());
        let right = compose(transform_for(event), identity())(list);

        assert_eq!(left, plain, "左から繋いでも変わらない");
        assert_eq!(right, plain, "右から繋いでも変わらない");
    }
}

/// 結合律: 繋ぐ順序を変えても、結果が変わらない。
#[test]
fn composition_is_associative() {
    let mut rng = Rng::new(31415926);
    for _ in 0..TRIALS {
        let list = any_list(&mut rng);
        let (e1, e2, e3) = (
            any_event(&mut rng),
            any_event(&mut rng),
            any_event(&mut rng),
        );

        let left = compose(
            compose(transform_for(e1.clone()), transform_for(e2.clone())),
            transform_for(e3.clone()),
        )(list.clone());
        let right = compose(
            transform_for(e1),
            compose(transform_for(e2), transform_for(e3)),
        )(list);

        assert_eq!(left, right);
    }
}

// ---------------------------------------------------------------------------
// 検出率。**法則が通ることだけでは、テストが何かを見ていることにならない。**
// ---------------------------------------------------------------------------

/// 壊した合成。**後ろの変換を捨てる。**
fn broken_compose(f: Transform, _g: Transform) -> Transform {
    f
}

/// 壊した合成を単位元の法則にかけて、何回検出するかを数える。
///
/// `compose(f, identity())` は壊しても `f` のままなので検出できない。
/// 検出できるのは `compose(identity(), f)` のほう（`identity` が残る）。
/// **状態と変換が「何もしないのと同じ」になったときは検出できない。**
#[test]
fn breaking_the_unit_law_is_detected() {
    let mut rng = Rng::new(20260929);
    let mut detections = 0;

    for _ in 0..TRIALS {
        let list = any_list(&mut rng);
        let event = any_event(&mut rng);

        let plain = transform_for(event.clone())(list.clone());
        let left = broken_compose(identity(), transform_for(event))(list);

        if left != plain {
            detections += 1;
        }
    }

    // 壊れた合成は `identity()` を返すので、結果はもとの状態そのもの。
    // 見逃すのは「変換をかけても状態が変わらない」ときだけで、
    // `ItemAdded` は必ず項目が増え、`ListCreated` は名前を start.. から
    // list.. に変えるので、**どちらも必ず変わる**。
    //
    // 実測は 200 / 200。p はほぼ 1 で σ はほぼ 0。しきい値 190 は
    // 余裕を取っただけで、分布の計算が要る値ではない。
    //
    // **こういう壊し方では、数えても何も分からない。** 数える意味が
    // あるのは、下の `a_hidden_bug_is_detected_often_enough` のほう。
    println!("検出 {detections} / {TRIALS}");
    assert!(
        detections >= 190,
        "壊した合成を {detections} / {TRIALS} しか検出していない。\
         テストが法則を見ていない疑いがある"
    );
}

/// **見逃しのある壊し方。** 特定の入力でだけ法則を破る。
///
/// `item7` を足すときだけ「足さない」変換を返す。
/// **こういう壊れ方は、その入力を引かないかぎり検出できない。**
/// 検出率を数える意味があるのはこちらで、前のテストのように
/// いつも検出する壊し方では、数えても分からない。
fn transform_with_a_hidden_bug(event: ToDoListEvent) -> Transform {
    match &event {
        ToDoListEvent::ItemAdded { item } if item.description == "item7" => Box::new(|list| list),
        _ => transform_for(event),
    }
}

/// 狭い範囲からイベントを引く。**検出率を意味のある値にするため。**
fn narrow_event(rng: &mut Rng) -> ToDoListEvent {
    let n = rng.below(10);
    if rng.below(4) == 0 {
        ToDoListEvent::ListCreated {
            list_name: ListName::new(&format!("list{n}")),
        }
    } else {
        ToDoListEvent::ItemAdded {
            item: ToDoItem::new(&format!("item{n}")),
        }
    }
}

#[test]
fn a_hidden_bug_is_detected_often_enough() {
    let mut rng = Rng::new(16180339);
    let mut detections = 0;

    for _ in 0..TRIALS {
        let list = any_list(&mut rng);
        let event = narrow_event(&mut rng);

        let plain = transform_for(event.clone())(list.clone());
        let buggy = transform_with_a_hidden_bug(event)(list);

        if buggy != plain {
            detections += 1;
        }
    }

    println!("隠れた欠陥の検出 {detections} / {TRIALS}");

    // 検出するのは `ItemAdded { item7 }` を引いたときだけ。
    //
    //   P(ItemAdded) = 3/4、P(n == 7) = 1/10 なので p = 0.075
    //   期待値 = 200 × 0.075 = 15
    //   σ = √(200 × 0.075 × 0.925) = 3.72
    //
    // **しきい値は期待値から 3σ 下**（15 − 11.2 ≒ 4）に置く。
    // なでしこ3 版ではしきい値が 1.58σ しか離れておらず、
    // CI が確率 5.7% で落ちた。
    //
    // **200 回だから捕まえられる。** 20 回なら期待値 1.5 で、
    // 1 度も引かない確率が e^-1.5 ≒ 22% ある。
    assert!(
        detections >= 4,
        "隠れた欠陥を {detections} / {TRIALS} しか検出していない。\
         生成する入力が狭すぎる疑いがある"
    );
}

/// 畳み込みそのものが空でないことを確かめる。
///
/// **検出率のテストが「常に検出する」だけでは、逆に何も言えない。**
/// 壊していないときは 0 件であることも確かめる。
#[test]
fn the_unbroken_law_is_never_detected() {
    let mut rng = Rng::new(27182818);
    let mut detections = 0;

    for _ in 0..TRIALS {
        let list = any_list(&mut rng);
        let event = any_event(&mut rng);

        let plain = transform_for(event.clone())(list.clone());
        let left = compose(identity(), transform_for(event))(list);

        if left != plain {
            detections += 1;
        }
    }

    assert_eq!(detections, 0, "壊していないのに検出している");
}

/// 空の並びを畳み込むと、出発点のまま。
#[test]
fn folding_nothing_changes_nothing() {
    assert_eq!(identity()(empty_list()), empty_list());
}
