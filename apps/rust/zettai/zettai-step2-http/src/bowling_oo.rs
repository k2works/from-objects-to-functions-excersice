//! ボウリングの得点計算（オブジェクト指向でよくある形）。
//!
//! 投球を溜め込む状態を持ち、それを書き換えるメソッドを並べる。
//! 第 1 章の対比のために置いている。

use crate::bowling::score;

#[derive(Debug, Default)]
pub struct Game {
    rolls: Vec<u16>,
}

impl Game {
    pub fn new() -> Self {
        Game::default()
    }

    /// 1 投する。**self を書き換える。**
    pub fn roll(&mut self, pins: u16) {
        self.rolls.push(pins);
    }

    pub fn roll_many(&mut self, pins: u16, n: usize) {
        for _ in 0..n {
            self.roll(pins);
        }
    }

    pub fn score(&self) -> u16 {
        score(&self.rolls)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_perfect_game_scores_three_hundred() {
        let mut game = Game::new();
        game.roll_many(10, 12);
        assert_eq!(game.score(), 300);
    }

    #[test]
    fn asking_before_ten_frames_panics() {
        use std::panic::{catch_unwind, AssertUnwindSafe};
        let mut game = Game::new();
        game.roll_many(0, 4); // 2 フレーム分しか投げていない

        let asked_too_early = catch_unwind(AssertUnwindSafe(|| game.score()));

        // **いつ聞いてよいかが型に現れない。** 溜め込む側は「もう聞ける」を知らない。
        assert!(asked_too_early.is_err());
    }
}
