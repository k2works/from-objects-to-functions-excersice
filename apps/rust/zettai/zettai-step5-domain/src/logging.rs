//! 記録の契約（第 12 章）。**ドメインは記録の手段を知りません。**
//!
//! 3 対象で共通の方針です（[ADR-021](../../../../docs/adr/ADR-021-context-as-path.md)）。
//! Rust では**クレートの境界が守ります**（[ADR-027](../../../../docs/adr/ADR-027-crate-boundary.md)）。
//! ドメインのクレートは依存を 1 つも持たないので、記録の実体を書けません。
//!
//! **既製品（`tracing`）を測ってから自前にしました**（[ADR-037](../../../../docs/adr/ADR-037-own-logging.md)）。
//! `tracing` は要求を満たしますが、依存 +18・ビルド +15.23 秒で
//! `just check` の余裕（4 秒弱）を超えます。

/// 記録の 1 件。**構造を持ちます。**
///
/// 文字列 1 本にしないのは、**後から絞り込めるようにする**ためです。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Record {
    /// 何が起きたか。業務の言葉で書く。
    pub what: String,
    /// 付帯する値。**キーと値の並び。**
    pub fields: Vec<(String, String)>,
}

impl Record {
    pub fn new(what: &str) -> Self {
        Record {
            what: what.to_string(),
            fields: Vec::new(),
        }
    }

    /// 値を足す。**組み立てながら書ける。**
    pub fn with(mut self, key: &str, value: impl std::fmt::Display) -> Self {
        self.fields.push((key.to_string(), value.to_string()));
        self
    }
}

/// 記録の行き先。**実体はインフラ側。**
pub trait LogSink {
    fn record(&self, record: &Record);
}

/// 何も記録しない行き先。**テストと、記録が要らない経路で使う。**
pub struct Silent;

impl LogSink for Silent {
    fn record(&self, _record: &Record) {}
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::cell::RefCell;

    #[derive(Default)]
    struct Collected(RefCell<Vec<Record>>);

    impl LogSink for Collected {
        fn record(&self, record: &Record) {
            self.0.borrow_mut().push(record.clone());
        }
    }

    #[test]
    fn a_record_carries_structure() {
        let r = Record::new("追記した")
            .with("user", "uberto")
            .with("count", 2);
        assert_eq!(r.what, "追記した");
        assert_eq!(r.fields.len(), 2);
        assert_eq!(r.fields[1], ("count".to_string(), "2".to_string()));
    }

    /// **行き先を差し替えられる。**
    #[test]
    fn the_sink_can_be_swapped() {
        let collected = Collected::default();
        collected.record(&Record::new("追記した").with("user", "uberto"));
        assert_eq!(collected.0.borrow().len(), 1);

        // 何も記録しない行き先に差し替えても、呼ぶ側は変わらない
        Silent.record(&Record::new("追記した"));
    }
}
