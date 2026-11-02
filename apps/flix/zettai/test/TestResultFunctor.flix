mod TestResultFunctor {

    use Domain.{User, ListName, ToDoStatus}
    use Domain.Errors.ZettaiError

    /// Right に相当する値を端の値まで含めて、Left に相当する値を 4 種類すべて並べる。
    def outcomes(): List[Result[ZettaiError, Int32]] = List#{
        Ok(Int32.minValue()), Ok(-1), Ok(0), Ok(1), Ok(Int32.maxValue()),
        Err(ZettaiError.ListNotFound(User.User("u"), ListName.ListName("l"))),
        Err(ZettaiError.ListAlreadyExists(User.User("u"), ListName.ListName("l"))),
        Err(ZettaiError.ItemNotFound(User.User("u"), ListName.ListName("l"), "x")),
        Err(ZettaiError.InvalidTransition("x", ToDoStatus.Todo, ToDoStatus.Blocked))
    }

    def intFunctions(): List[Int32 -> Int32] = List#{x -> x + 1, x -> x * 2, x -> 0 - x, x -> x}

    def showFunctions(): List[Int32 -> String] = List#{x -> "n${x}", x -> "is-positive:${x > 0}"}

    /// 渡された map が、恒等則を満たすか。
    def identityLaw(mapper: (Int32 -> Int32, Result[ZettaiError, Int32]) -> Result[ZettaiError, Int32]): Bool =
        List.forAll(o -> mapper(x -> x, o) == o, outcomes())

    /// 渡された map が、合成則を満たすか。
    def compositionLaw(mapper: (Int32 -> Int32, Result[ZettaiError, Int32]) -> Result[ZettaiError, Int32]): Bool =
        List.forAll(o ->
            List.forAll(f ->
                List.forAll(g -> mapper(g, mapper(f, o)) == mapper(x -> g(f(x)), o), intFunctions()),
                intFunctions()),
            outcomes())

    /// 成功した値に関数を 2 回当ててしまう、壊れた map。
    def brokenMap(f: Int32 -> Int32, r: Result[ZettaiError, Int32]): Result[ZettaiError, Int32] =
        match r {
            case Ok(x)  => Ok(f(f(x)))
            case Err(e) => Err(e)
        }

    @Test
    def mapPreservesIdentity(): Unit \ Assert =
        Assert.assertTrue(identityLaw(Result.map))

    @Test
    def mapComposes(): Unit \ Assert =
        Assert.assertTrue(compositionLaw(Result.map))

    @Test
    def mapComposesAcrossDifferentResultTypes(): Unit \ Assert =
        List.forEach(o ->
            List.forEach(f ->
                List.forEach(g ->
                    Assert.assertEq(
                        expected = Result.map(x -> g(f(x)), o),
                        Result.map(g, Result.map(f, o))),
                    showFunctions()),
                intFunctions()),
            outcomes())

    @Test
    def aFailureIsLeftUntouchedByMap(): Unit \ Assert =
        List.forEach(o -> match o {
            case Err(e) => Assert.assertEq(expected = Err(e), Result.map(x -> x + 1, o))
            case Ok(_)  => ()
        }, outcomes())

    @Test
    def theSameMapShapeWorksOnListOptionAndResult(): Unit \ Assert = {
        let ok: Result[ZettaiError, Int32] = Ok(1);
        Assert.assertEq(expected = 2 :: Nil, Functor.map(x -> x + 1, 1 :: Nil));
        Assert.assertEq(expected = Some(2), Functor.map(x -> x + 1, Some(1)));
        Assert.assertEq(expected = Ok(2), Functor.map(x -> x + 1, ok))
    }

    @Test
    def theLawTestsTellABrokenMapApart(): Unit \ Assert = {
        Assert.assertTrue(identityLaw(brokenMap));
        Assert.assertFalse(compositionLaw(brokenMap))
    }

}
