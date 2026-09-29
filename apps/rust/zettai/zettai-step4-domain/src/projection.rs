//! 射影（第 8 章）。**同じイベントから、別の形を作る。**
//!
//! 第 5 章の畳み込みは `ToDoList` を作った。こちらは**クエリのための形**を
//! 作る。イベントは同じで、行き先が違うだけ。
//!
//! **コマンド側とクエリ側は別の型**にする。混ぜるとコンパイルが止まる。

use crate::{ToDoListEvent, Transform};

/// クエリのための形。**コマンド側の `ToDoList` とは別の型。**
///
/// 画面に出したいものだけを持つ。**項目の中身は持たない**ので、
/// 一覧を出すのに全部を読み込まなくてよい。
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct ListSummary {
    pub list_name: String,
    pub item_count: usize,
}

/// 射影の変換。**第 5 章と同じ形（状態から状態への関数）。**
pub type SummaryTransform = Box<dyn Fn(ListSummary) -> ListSummary>;

pub fn summary_identity() -> SummaryTransform {
    Box::new(|s| s)
}

pub fn summary_compose(f: SummaryTransform, g: SummaryTransform) -> SummaryTransform {
    Box::new(move |s| g(f(s)))
}

/// イベント 1 つを、射影の変換にする。
pub fn summary_transform_for(event: ToDoListEvent) -> SummaryTransform {
    match event {
        ToDoListEvent::ListCreated { list_name } => Box::new(move |_previous| ListSummary {
            list_name: list_name.0.clone(),
            item_count: 0,
        }),
        ToDoListEvent::ItemAdded { .. } => Box::new(|summary| ListSummary {
            item_count: summary.item_count + 1,
            ..summary
        }),
    }
}

/// イベントの並びから、クエリのための形を作る。
pub fn summarize(events: Vec<ToDoListEvent>) -> ListSummary {
    events
        .into_iter()
        .map(summary_transform_for)
        .fold(summary_identity(), summary_compose)(ListSummary::default())
}

/// **型が違うことを示すための道具。** 第 5 章の変換は射影に使えない。
///
/// この関数は存在しない。**存在しないことがこの章の主題**なので、
/// 型の名前だけを並べておく。
pub fn kinds() -> (&'static str, &'static str) {
    (
        std::any::type_name::<Transform>(),
        std::any::type_name::<SummaryTransform>(),
    )
}

/// クエリの結果。**一覧を返すときの入れ物。**
///
/// `map` を持つ。中身に関数をかけても、**入れ物の形は変わらない**。
/// これがファンクタ。
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct Summaries(pub Vec<ListSummary>);

impl Summaries {
    /// **`Iterator::map` に任せる。** 自前で書くものが無い。
    ///
    /// Kotlin 版はここで `Functor` を自作した。なでしこ3 版は
    /// `射影写像` を書いた。Rust は**入れ物を包み直すだけ**。
    pub fn map(self, f: impl Fn(ListSummary) -> ListSummary) -> Summaries {
        Summaries(self.0.into_iter().map(f).collect())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{ListName, ToDoItem};

    fn created(name: &str) -> ToDoListEvent {
        ToDoListEvent::ListCreated {
            list_name: ListName::new(name),
        }
    }

    fn added(description: &str) -> ToDoListEvent {
        ToDoListEvent::ItemAdded {
            item: ToDoItem::new(description),
        }
    }

    #[test]
    fn summarizing_nothing_gives_the_empty_summary() {
        assert_eq!(summarize(vec![]), ListSummary::default());
    }

    #[test]
    fn the_summary_counts_the_items() {
        let summary = summarize(vec![created("book"), added("write"), added("publish")]);
        assert_eq!(summary.list_name, "book");
        assert_eq!(summary.item_count, 2);
    }

    #[test]
    fn the_summary_does_not_hold_the_items() {
        // **項目の中身を持たない。** 一覧を出すのに全部を読まなくてよい。
        let summary = summarize(vec![created("book"), added("write")]);
        assert_eq!(summary.item_count, 1);
        // `summary` に項目の説明は無い。型にフィールドが無いので参照できない。
    }

    #[test]
    fn the_two_kinds_of_transform_are_different_types() {
        let (command_side, query_side) = kinds();
        assert_ne!(command_side, query_side);
    }
}
