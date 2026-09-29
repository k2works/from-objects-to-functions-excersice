//! 未適用のタグをどう扱うか。**要求はこれ 1 つ。**
//!
//! > タグを書いたのにデータを渡し忘れたら、**失敗として扱う。**

#[cfg(test)]
mod tests {
    #[test]
    fn tera_with_a_missing_tag() {
        let mut tera = tera::Tera::default();
        tera.add_raw_template("t", "<h1>{{ name }}</h1><p>{{ forgotten }}</p>")
            .unwrap();
        let mut ctx = tera::Context::new();
        ctx.insert("name", "book");
        // `forgotten` を渡し忘れた
        let result = tera.render("t", &ctx);
        println!("tera: {result:?}");
    }

    #[test]
    fn minijinja_with_a_missing_tag() {
        let mut env = minijinja::Environment::new();
        env.add_template("t", "<h1>{{ name }}</h1><p>{{ forgotten }}</p>")
            .unwrap();
        let tmpl = env.get_template("t").unwrap();
        let result = tmpl.render(minijinja::context! { name => "book" });
        println!("minijinja（既定）: {result:?}");

        // 厳しい設定にすると？
        let mut strict = minijinja::Environment::new();
        strict.set_undefined_behavior(minijinja::UndefinedBehavior::Strict);
        strict
            .add_template("t", "<h1>{{ name }}</h1><p>{{ forgotten }}</p>")
            .unwrap();
        let tmpl = strict.get_template("t").unwrap();
        let result = tmpl.render(minijinja::context! { name => "book" });
        println!("minijinja（Strict）: {:?}", result.map_err(|e| e.to_string()));
    }
}
