//! 記録の行き先（第 12 章）。**インフラ側にあります。**
//!
//! ドメインは [`LogSink`](zettai_step5_domain::logging::LogSink) という契約しか
//! 知りません。**クレートの境界が守ります**（[ADR-027](../../../../docs/adr/ADR-027-crate-boundary.md)）。
//!
//! **既製品（`tracing`）を測ってから自前にしました**（[ADR-037](../../../../docs/adr/ADR-037-own-logging.md)）。

use std::cell::RefCell;
use std::io::Write;
use zettai_step5_domain::logging::{LogSink, Record};

/// 1 行の JSON で書き出す。
///
/// **JSON も自前です。** 第 9 章と同じ判断で、ここでも `serde_json` を
/// 入れていません。
pub fn to_line(record: &Record) -> String {
    let fields: Vec<String> = record
        .fields
        .iter()
        .map(|(k, v)| format!(r#""{}":"{}""#, escape(k), escape(v)))
        .collect();
    format!(
        r#"{{"what":"{}"{}{}}}"#,
        escape(&record.what),
        if fields.is_empty() { "" } else { "," },
        fields.join(",")
    )
}

fn escape(s: &str) -> String {
    s.replace('\\', r"\\").replace('"', "\\\"")
}

/// 標準出力へ書く。
pub struct ToStdout;

impl LogSink for ToStdout {
    fn record(&self, record: &Record) {
        let mut out = std::io::stdout();
        let _ = writeln!(out, "{}", to_line(record));
    }
}

/// 覚えておく。**テストが読むため。**
#[derive(Default)]
pub struct Remembered(RefCell<Vec<String>>);

impl Remembered {
    pub fn lines(&self) -> Vec<String> {
        self.0.borrow().clone()
    }
}

impl LogSink for Remembered {
    fn record(&self, record: &Record) {
        self.0.borrow_mut().push(to_line(record));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_record_becomes_one_line_of_json() {
        let r = Record::new("追記した")
            .with("user", "uberto")
            .with("count", 2);
        assert_eq!(
            to_line(&r),
            r#"{"what":"追記した","user":"uberto","count":"2"}"#
        );
    }

    #[test]
    fn a_record_without_fields_is_still_valid() {
        assert_eq!(to_line(&Record::new("始めた")), r#"{"what":"始めた"}"#);
    }

    #[test]
    fn quotes_are_escaped() {
        let r = Record::new("名前を変えた").with("name", r#"a"b"#);
        assert!(to_line(&r).contains(r#""a\"b""#), "実際: {}", to_line(&r));
    }

    /// **行き先を差し替えられる。**
    #[test]
    fn the_sink_remembers_what_was_recorded() {
        let sink = Remembered::default();
        sink.record(&Record::new("読み出した").with("count", 3));
        assert_eq!(sink.lines().len(), 1);
        assert!(sink.lines()[0].contains(r#""count":"3""#));
    }
}
