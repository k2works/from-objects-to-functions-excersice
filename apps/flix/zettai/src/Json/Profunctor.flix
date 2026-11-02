pub mod Json.Profunctor {

    /// 入力を読み替え（逆向きに写し）、出力を書き換える（順向きに写す）。
    /// 入力の側は反変、出力の側は共変になる。
    pub def dimap(pre: a2 -> a, post: b -> b2, f: a -> b): a2 -> b2 =
        x -> post(f(pre(x)))

}
