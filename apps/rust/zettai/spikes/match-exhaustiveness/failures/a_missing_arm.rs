// 1 マス落とすと止まるか。
enum State { Missing, Empty, HasItems }
enum Command { CreateList, AddItem }
fn step(state: State, command: Command) -> State {
    match (state, command) {
        (State::Missing, Command::CreateList) => State::Empty,
        (State::Missing, Command::AddItem) => State::Missing,
        (State::Empty, Command::CreateList) => State::Empty,
        (State::Empty, Command::AddItem) => State::HasItems,
        (State::HasItems, Command::CreateList) => State::HasItems,
        // (HasItems, AddItem) をわざと落とす
    }
}
fn main() { let _ = step(State::Empty, Command::AddItem); }
