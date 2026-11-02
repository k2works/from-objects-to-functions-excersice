pub mod Domain.Validation.Validate {

    use Domain.{User, ListName}
    use Domain.Errors.{InputField, FieldProblem, ZettaiError}
    use Domain.Commands.ToDoListCommand

    pub def maxLength(): Int32 = 40

    def fail(problem: FieldProblem): Validation[FieldProblem, a] =
        Validation.Failure(Nec.singleton(problem))

    pub def notBlank(field: InputField, raw: String): Validation[FieldProblem, Unit] =
        if (String.isEmpty(String.trim(raw))) fail(FieldProblem.Blank(field)) else Validation.Success(())

    pub def withinLength(field: InputField, raw: String): Validation[FieldProblem, Unit] =
        if (String.length(raw) > maxLength()) fail(FieldProblem.TooLong(field, maxLength())) else Validation.Success(())

    /// 「/」と制御文字を許さない。
    pub def noForbiddenCharacter(field: InputField, raw: String): Validation[FieldProblem, Unit] =
        if (String.exists(c -> c == '/' or Char.isISOControl(c), raw)) fail(FieldProblem.ForbiddenCharacter(field)) else Validation.Success(())

    /// 1 つの入力の 3 つの規則は互いに独立なので、すべて確かめて、誤りを規則の順に集める。
    pub def validateName(field: InputField, raw: String): Validation[FieldProblem, String] =
        forA (
            _ <- notBlank(field, raw);
            _ <- withinLength(field, raw);
            _ <- noForbiddenCharacter(field, raw)
        ) yield raw

    /// 3 つの入力も互いに独立なので、誤りを入力の順にすべて集める。
    pub def validateRename(rawUser: String, rawList: String, rawNewName: String): Validation[FieldProblem, ToDoListCommand] =
        forA (
            user    <- validateName(InputField.UserName, rawUser);
            list    <- validateName(InputField.ListNameField, rawList);
            newName <- validateName(InputField.NewName, rawNewName)
        ) yield ToDoListCommand.RenameToDoList(User.User(user), ListName.ListName(list), ListName.ListName(newName))

    def label(field: InputField): String =
        match field {
            case InputField.UserName      => "利用者名"
            case InputField.ListNameField => "リスト名"
            case InputField.NewName       => "新しい名前"
        }

    /// 利用者に見せる説明。誤りのもとになった入力の値は、含めない。
    pub def describe(problem: FieldProblem): String =
        match problem {
            case FieldProblem.Blank(field)              => "${label(field)}は空にできません"
            case FieldProblem.TooLong(field, limit)     => "${label(field)}は ${limit} 文字以内にしてください"
            case FieldProblem.ForbiddenCharacter(field) => "${label(field)}に使えない文字が含まれています"
        }

    /// すべての誤りを、1 つの説明にまとめる。
    pub def explain(problems: List[FieldProblem]): String =
        "入力に誤りがあります。" + String.intercalate("", List.map(p -> "\n- ${describe(p)}", problems))

    /// 最初の誤りだけを取り出す。Result の連鎖と比べるための変換。
    pub def toResult(v: Validation[FieldProblem, a]): Result[FieldProblem, a] =
        match v {
            case Validation.Success(a)      => Ok(a)
            case Validation.Failure(errors) => Err(Nec.head(errors))
        }

    /// すべての誤りを、失敗の型に載せる。
    pub def toZettai(v: Validation[FieldProblem, a]): Result[ZettaiError, a] =
        match v {
            case Validation.Success(a)      => Ok(a)
            case Validation.Failure(errors) => Err(ZettaiError.InvalidInput(Nec.toList(errors)))
        }

}
