mod TestApplicativeLaws {

    use Domain.Errors.{InputField, FieldProblem}
    use Domain.Errors.InputField.{UserName, ListNameField, NewName}

    /// 成功するもの、規則を 1 つ破るもの、2 つ破るもの、3 つ破るものを含む入力の全体。
    def raws(): List[String] = List#{
        "ok",
        "",
        "a/b",
        String.repeat(41, "x"),
        String.repeat(41, "/"),
        "   "
    }

    def problems(v: Validation[FieldProblem, a]): List[FieldProblem] =
        match v {
            case Validation.Failure(errors) => Nec.toList(errors)
            case Validation.Success(_)      => Nil
        }

    def each3(f: (String, String, String) -> Unit \ ef): Unit \ ef =
        List.forEach(a -> List.forEach(b -> List.forEach(c -> f(a, b, c), raws()), raws()), raws())

    @Test
    def whenEverythingSucceedsItIsPlainFunctionApplication(): Unit \ Assert =
        Assert.assertEq(
            expected = Validation.Success((1, 2, 3)),
            forA (x <- (Validation.Success(1): Validation[FieldProblem, Int32]); y <- Validation.Success(2); z <- Validation.Success(3)) yield (x, y, z)
        )

    @Test
    def theProblemsOfARequestAreTheProblemsOfEachInputInOrder(): Unit \ Assert =
        each3((a, b, c) -> Assert.assertEq(
            expected = problems(Domain.Validation.Validate.validateName(UserName, a))
                ::: problems(Domain.Validation.Validate.validateName(ListNameField, b))
                ::: problems(Domain.Validation.Validate.validateName(NewName, c)),
            problems(Domain.Validation.Validate.validateRename(a, b, c))
        ))

    @Test
    def theNumberOfProblemsIsTheSumOfTheFailingParts(): Unit \ Assert =
        each3((a, b, c) -> Assert.assertEq(
            expected = List.length(problems(Domain.Validation.Validate.validateName(UserName, a)))
                + List.length(problems(Domain.Validation.Validate.validateName(ListNameField, b)))
                + List.length(problems(Domain.Validation.Validate.validateName(NewName, c))),
            List.length(problems(Domain.Validation.Validate.validateRename(a, b, c)))
        ))

    @Test
    def groupingTheCombinationDifferentlyDoesNotChangeTheProblems(): Unit \ Assert =
        each3((a, b, c) -> {
            let va = Domain.Validation.Validate.validateName(UserName, a);
            let vb = Domain.Validation.Validate.validateName(ListNameField, b);
            let vc = Domain.Validation.Validate.validateName(NewName, c);
            let leftFirst = forA (ab <- forA (x <- va; y <- vb) yield (x, y); z <- vc) yield (ab, z);
            let rightFirst = forA (x <- va; bc <- forA (y <- vb; z <- vc) yield (y, z)) yield (x, bc);
            Assert.assertEq(expected = problems(leftFirst), problems(rightFirst))
        })

    @Test
    def aSuccessOfUnitChangesNothing(): Unit \ Assert =
        List.forEach(a -> {
            let v = Domain.Validation.Validate.validateName(NewName, a);
            let withUnit = forA (x <- v; _ <- (Validation.Success(()): Validation[FieldProblem, Unit])) yield x;
            Assert.assertEq(expected = v, withUnit)
        }, raws())

    @Test
    def mapPreservesIdentityAndComposes(): Unit \ Assert =
        List.forEach(a -> {
            let v = Domain.Validation.Validate.validateName(NewName, a);
            Assert.assertEq(expected = v, Functor.map(x -> x, v));
            Assert.assertEq(
                expected = Functor.map(x -> String.length(x) + 1, v),
                Functor.map(n -> n + 1, Functor.map(x -> String.length(x), v)))
        }, raws())

    /// 最初の誤りだけを残してしまう、壊れた合成。
    def firstOnly(a: Validation[FieldProblem, String], b: Validation[FieldProblem, String]): Validation[FieldProblem, String] =
        match a {
            case Validation.Failure(e) => Validation.Failure(e)
            case Validation.Success(_) => b
        }

    @Test
    def theCountTestTellsAFirstOnlyCombinationApart(): Unit \ Assert = {
        let a = Domain.Validation.Validate.validateName(UserName, "");
        let b = Domain.Validation.Validate.validateName(NewName, "");
        Assert.assertEq(expected = 2, List.length(problems(forA (x <- a; y <- b) yield x + y)));
        Assert.assertEq(expected = 1, List.length(problems(firstOnly(a, b))))
    }

}
