mod TestValidation {

    use Errors.{InputField, FieldProblem, ZettaiError}
    use Errors.InputField.{UserName, ListNameField, NewName}
    use Errors.FieldProblem.{Blank, TooLong, ForbiddenCharacter}

    def problems(v: Validation[FieldProblem, a]): List[FieldProblem] =
        match v {
            case Validation.Failure(errors) => Nec.toList(errors)
            case Validation.Success(_)      => Nil
        }

    def tooLongName(): String = String.repeat(41, "x")

    @Test
    def aPlainNameIsAccepted(): Unit \ Assert =
        Assert.assertEq(expected = Validation.Success("book"), Validate.validateName(ListNameField, "book"))

    @Test
    def aBlankNameIsRejected(): Unit \ Assert =
        Assert.assertEq(expected = List#{Blank(NewName)}, problems(Validate.validateName(NewName, "   ")))

    @Test
    def aNameOverFortyCharactersIsTooLong(): Unit \ Assert =
        Assert.assertEq(expected = List#{TooLong(NewName, 40)}, problems(Validate.validateName(NewName, tooLongName())))

    @Test
    def aSlashIsForbidden(): Unit \ Assert =
        Assert.assertEq(expected = List#{ForbiddenCharacter(UserName)}, problems(Validate.validateName(UserName, "a/b")))

    @Test
    def everyBrokenRuleOfOneInputIsReportedInRuleOrder(): Unit \ Assert =
        Assert.assertEq(
            expected = List#{TooLong(NewName, 40), ForbiddenCharacter(NewName)},
            problems(Validate.validateName(NewName, String.repeat(41, "/")))
        )

    @Test
    def everyBrokenInputOfOneRequestIsReportedInInputOrder(): Unit \ Assert =
        Assert.assertEq(
            expected = List#{Blank(UserName), ForbiddenCharacter(ListNameField), TooLong(NewName, 40)},
            problems(Validate.validateRename(" ", "a/b", tooLongName()))
        )

    @Test
    def aValidRequestBecomesACommand(): Unit \ Assert =
        Assert.assertEq(
            expected = Validation.Success(Commands.ToDoListCommand.RenameToDoList(
                Domain.User.User("uberto"), Domain.ListName.ListName("book"), Domain.ListName.ListName("shop"))),
            Validate.validateRename("uberto", "book", "shop")
        )

    /// 同じ規則を Result の連鎖でつなぐと、最初の失敗で止まる。
    def stopsAtTheFirstProblem(field: InputField, raw: String): Result[FieldProblem, String] =
        forM (
            _ <- Validate.toResult(Validate.notBlank(field, raw));
            _ <- Validate.toResult(Validate.withinLength(field, raw));
            _ <- Validate.toResult(Validate.noForbiddenCharacter(field, raw))
        ) yield raw

    @Test
    def chainingWithFlatMapHidesTheSecondProblem(): Unit \ Assert = {
        let raw = String.repeat(41, "/");
        Assert.assertEq(expected = Err(TooLong(NewName, 40)), stopsAtTheFirstProblem(NewName, raw));
        Assert.assertEq(expected = 2, List.length(problems(Validate.validateName(NewName, raw))))
    }

}
