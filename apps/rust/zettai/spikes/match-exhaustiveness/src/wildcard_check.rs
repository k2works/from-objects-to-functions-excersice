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

// ---------------------------------------------------------------------------
// **lint が効かない形。** 遷移表はこちらの形になる。
// ---------------------------------------------------------------------------

pub enum Command {
    CreateList,
    AddItem,
}

/// 組（タプル）で受けると、`wildcard_enum_match_arm` は発火しない。
pub fn step(state: State, command: Command) -> State {
    match (state, command) {
        (State::Missing, Command::CreateList) => State::Empty,
        (State::Empty, Command::AddItem) => State::HasItems,
        _ => State::Missing,
    }
}

/// 参照ごし（`Option<&T>`）でも発火しない。
pub fn last_is_missing(states: &[State]) -> bool {
    match states.last() {
        Some(State::Missing) => true,
        _ => false,
    }
}
