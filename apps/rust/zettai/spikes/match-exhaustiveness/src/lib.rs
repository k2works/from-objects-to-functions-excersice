//! スパイク: `match` の網羅は、遷移表の穴をどこまで見るか（Unit 4 / スパイク 4）。
//!
//! なでしこ3 版は 16 マスを手で数えて穴を 1 つ見つけた。
//! Rust はコンパイラが網羅を見る。**どこまで見るのかを確かめる。**

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum State {
    Missing,
    Empty,
    HasItems,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Command {
    CreateList,
    AddItem,
}

/// 全マスを書いた遷移。
pub fn step(state: State, command: Command) -> State {
    match (state, command) {
        (State::Missing, Command::CreateList) => State::Empty,
        (State::Missing, Command::AddItem) => State::Missing,
        (State::Empty, Command::CreateList) => State::Empty,
        (State::Empty, Command::AddItem) => State::HasItems,
        (State::HasItems, Command::CreateList) => State::HasItems,
        (State::HasItems, Command::AddItem) => State::HasItems,
    }
}
pub mod wildcard_check;
