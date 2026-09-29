// コマンド側の変換をクエリ側に渡せるか。
use zettai_step3_domain::projection::{summary_compose, summary_identity};
use zettai_step3_domain::identity;
fn main() {
    let _ = summary_compose(identity(), summary_identity());
}
