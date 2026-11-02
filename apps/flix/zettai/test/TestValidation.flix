mod TestValidation {

    use Domain.Errors.{InputField, FieldProblem, ZettaiError}
    use Domain.Errors.InputField.{UserName, ListNameField, NewName}
    use Domain.Errors.FieldProblem.{Blank, TooLong, ForbiddenCharacter}

    def problems(v: Validation[FieldProblem, a]): List[FieldProblem] =
        match v {
            case Validation.Failure(errors) => Nec.toList(errors)
            case Validation.Success(_)      => Nil
        }

    def tooLongName(): String = String.repeat(41, "x")

    @Test
    def aPlainNameIsAccepted(): Unit \ Assert =
        Assert.assertEq(expected = Validation.Success("book"), Domain.Validation.Validate.validateName(ListNameField, "book"))

    @Test
    def aBlankNameIsRejected(): Unit \ Assert =
        Assert.assertEq(expected = List#{Blank(NewName)}, problems(Domain.Validation.Validate.validateName(NewName, "   ")))

    @Test
    def aNameOverFortyCharactersIsTooLong(): Unit \ Assert =
        Assert.assertEq(expected = List#{TooLong(NewName, 40)}, problems(Domain.Validation.Validate.validateName(NewName, tooLongName())))

    @Test
    def aSlashIsForbidden(): Unit \ Assert =
        Assert.assertEq(expected = List#{ForbiddenCharacter(UserName)}, problems(Domain.Validation.Validate.validateName(UserName, "a/b")))

    @Test
    def everyBrokenRuleOfOneInputIsReportedInRuleOrder(): Unit \ Assert =
        Assert.assertEq(
            expected = List#{TooLong(NewName, 40), ForbiddenCharacter(NewName)},
            problems(Domain.Validation.Validate.validateName(NewName, String.repeat(41, "/")))
        )

    @Test
    def everyBrokenInputOfOneRequestIsReportedInInputOrder(): Unit \ Assert =
        Assert.assertEq(
            expected = List#{Blank(UserName), ForbiddenCharacter(ListNameField), TooLong(NewName, 40)},
            problems(Domain.Validation.Validate.validateRename(" ", "a/b", tooLongName()))
        )

    @Test
    def aValidRequestBecomesACommand(): Unit \ Assert =
        Assert.assertEq(
            expected = Validation.Success(Domain.Commands.ToDoListCommand.RenameToDoList(
                Domain.User.User("uberto"), Domain.ListName.ListName("book"), Domain.ListName.ListName("shop"))),
            Domain.Validation.Validate.validateRename("uberto", "book", "shop")
        )

    /// 同じ規則を Result の連鎖でつなぐと、最初の失敗で止まる。
    def stopsAtTheFirstProblem(field: InputField, raw: String): Result[FieldProblem, String] =
        forM (
            _ <- Domain.Validation.Validate.toResult(Domain.Validation.Validate.notBlank(field, raw));
            _ <- Domain.Validation.Validate.toResult(Domain.Validation.Validate.withinLength(field, raw));
            _ <- Domain.Validation.Validate.toResult(Domain.Validation.Validate.noForbiddenCharacter(field, raw))
        ) yield raw

    @Test
    def chainingWithFlatMapHidesTheSecondProblem(): Unit \ Assert = {
        let raw = String.repeat(41, "/");
        Assert.assertEq(expected = Err(TooLong(NewName, 40)), stopsAtTheFirstProblem(NewName, raw));
        Assert.assertEq(expected = 2, List.length(problems(Domain.Validation.Validate.validateName(NewName, raw))))
    }

}
