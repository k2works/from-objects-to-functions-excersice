//! 検出率を測れるか。**「落ちるまで」ではなく「200 回中何回落ちたか」が要る。**
#[cfg(test)]
mod tests {
    use proptest::prelude::*;
    use proptest::test_runner::{Config, TestRunner};

    /// わざと壊した法則: 「足し算は可換」を、ある条件でだけ破る。
    fn broken_law(a: i32, b: i32) -> bool {
        if a == 7 {
            return false;
        }
        a + b == b + a
    }

    #[test]
    fn counting_detections_needs_the_runner_api() {
        let mut detections = 0;
        for _ in 0..200 {
            let mut runner = TestRunner::new(Config {
                cases: 1,
                failure_persistence: None,
                ..Config::default()
            });
            let result = runner.run(&(0i32..10, 0i32..10), |(a, b)| {
                prop_assert!(broken_law(a, b));
                Ok(())
            });
            if result.is_err() {
                detections += 1;
            }
        }
        println!("検出 {detections} / 200");
        assert!(detections > 0);
    }
}
