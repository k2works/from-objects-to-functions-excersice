// マスは全部あるが、行き先が間違っている。
enum State { Missing, Empty, HasItems }
enum Command { CreateList, AddItem }
fn step(state: State, command: Command) -> State {
    match (state, command) {
        (State::Missing, Command::CreateList) => State::Empty,
        (State::Missing, Command::AddItem) => State::Empty,   // 誤り: 無いリストに足せてしまう
        (State::Empty, Command::CreateList) => State::Empty,
        (State::Empty, Command::AddItem) => State::HasItems,
        (State::HasItems, Command::CreateList) => State::HasItems,
        (State::HasItems, Command::AddItem) => State::HasItems,
    }
}
fn main() { let _ = step(State::Missing, Command::AddItem); }
