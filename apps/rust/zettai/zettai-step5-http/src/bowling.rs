//! ボウリングの得点計算。
//!
//! 入力（投球の並び）から出力（得点）への関数として書く。
//! 状態を持たないので、テストは「何を渡すと何が返るか」だけになる。

/// 同じ本数の投球を n 回並べる。
///
/// なでしこ3 版では `繰り返す` が開始より終了が小さいときに逆向きに回るので、
/// 0 件を先に返すガードが要った。Rust の `0..n` は n が 0 なら空なので要らない。
pub fn repeat_rolls(rolls: &[u16], pins: u16, n: usize) -> Vec<u16> {
    rolls
        .iter()
        .copied()
        .chain(std::iter::repeat_n(pins, n))
        .collect()
}

/// 投球の並びから得点を計算する。
pub fn score(rolls: &[u16]) -> u16 {
    let mut total = 0;
    let mut i = 0;
    for _ in 0..10 {
        if rolls[i] == 10 {
            total += 10 + rolls[i + 1] + rolls[i + 2];
            i += 1;
        } else {
            let frame = rolls[i] + rolls[i + 1];
            total += if frame == 10 {
                10 + rolls[i + 2]
            } else {
                frame
            };
            i += 2;
        }
    }
    total
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn all_gutters_score_zero() {
        assert_eq!(score(&repeat_rolls(&[], 0, 20)), 0);
    }

    #[test]
    fn all_ones_score_twenty() {
        assert_eq!(score(&repeat_rolls(&[], 1, 20)), 20);
    }

    #[test]
    fn one_spare_adds_the_next_roll() {
        let rolls = repeat_rolls(&[5, 5, 3], 0, 17);
        assert_eq!(score(&rolls), 16);
    }

    #[test]
    fn one_strike_adds_the_next_two_rolls() {
        let rolls = repeat_rolls(&[10, 3, 4], 0, 16);
        assert_eq!(score(&rolls), 24);
    }

    #[test]
    fn a_perfect_game_scores_three_hundred() {
        assert_eq!(score(&repeat_rolls(&[], 10, 12)), 300);
    }
}
