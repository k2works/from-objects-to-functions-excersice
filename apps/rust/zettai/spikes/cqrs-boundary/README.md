# スパイク: コマンド側とクエリ側を混ぜたら止まるか（Unit 5 / 第 8 章）

```bash
cargo build   # 落ちるのが正しい
```

```text
error[E0308]: mismatched types
   expected `Box<dyn Fn(ListSummary) -> ListSummary>`,
      found `Box<dyn Fn(ToDoList) -> ToDoList>`
```

**型が CQRS の境界を守る。** 2 対象は約束（命名と置き場所）で守った。
