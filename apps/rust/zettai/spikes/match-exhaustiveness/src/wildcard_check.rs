//! ワイルドカードを clippy で禁じられるか。
#![warn(clippy::wildcard_enum_match_arm)]

pub enum State {
    Missing,
    Empty,
    HasItems,
}

pub fn describe(state: State) -> &'static str {
    match state {
        State::Missing => "無い",
        _ => "ある",
    }
}
