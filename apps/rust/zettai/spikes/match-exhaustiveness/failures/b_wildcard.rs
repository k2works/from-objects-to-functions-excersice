// ワイルドカードがあると、落としても止まらない。
enum State { Missing, Empty, HasItems }
enum Command { CreateList, AddItem }
fn step(state: State, command: Command) -> State {
    match (state, command) {
        (State::Missing, Command::CreateList) => State::Empty,
        (State::Empty, Command::AddItem) => State::HasItems,
        _ => State::Missing,   // ここが穴を隠す
    }
}
fn main() { let _ = step(State::HasItems, Command::AddItem); }
