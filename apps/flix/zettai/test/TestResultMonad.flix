mod TestResultMonad {

    use Domain.{User, ListName}
    use Errors.ZettaiError

    def failure(): ZettaiError = ZettaiError.ListNotFound(User.User("u"), ListName.ListName("l"))

    def values(): List[Result[ZettaiError, Int32]] = List#{
        Ok(Int32.minValue()), Ok(-1), Ok(0), Ok(1), Ok(Int32.maxValue()),
        Err(failure()), Err(ZettaiError.StorageFailure("down"))
    }

    /// Ok を返す関数と、条件によって Err を返す関数。
    def steps(): List[Int32 -> Result[ZettaiError, Int32]] = List#{
        x -> Ok(x),
        x -> Ok(x / 2),
        x -> if (x > 0) Ok(x) else Err(failure()),
        x -> if (x == 0) Err(ZettaiError.StorageFailure("zero")) else Ok(0 - x)
    }

    def leftIdentity(bind: (Int32 -> Result[ZettaiError, Int32], Result[ZettaiError, Int32]) -> Result[ZettaiError, Int32]): Bool =
        List.forAll(x -> List.forAll(f -> bind(f, Ok(x)) == f(x), steps()),
            List#{Int32.minValue(), -1, 0, 1, Int32.maxValue()})

    def rightIdentity(bind: (Int32 -> Result[ZettaiError, Int32], Result[ZettaiError, Int32]) -> Result[ZettaiError, Int32]): Bool =
        List.forAll(m -> bind(x -> Ok(x), m) == m, values())

    def associativity(bind: (Int32 -> Result[ZettaiError, Int32], Result[ZettaiError, Int32]) -> Result[ZettaiError, Int32]): Bool =
        List.forAll(m ->
            List.forAll(f ->
                List.forAll(g -> bind(g, bind(f, m)) == bind(x -> bind(g, f(x)), m), steps()),
                steps()),
            values())

    /// 負の値だけ、関数の結果を無視して失敗にしてしまう壊れた bind。
    def brokenBind(f: Int32 -> Result[ZettaiError, Int32], m: Result[ZettaiError, Int32]): Result[ZettaiError, Int32] =
        match m {
            case Ok(x) => if (x < 0) Err(failure()) else f(x)
            case Err(e) => Err(e)
        }

    @Test
    def flatMapObeysTheThreeLaws(): Unit \ Assert = {
        Assert.assertTrue(leftIdentity(Result.flatMap));
        Assert.assertTrue(rightIdentity(Result.flatMap));
        Assert.assertTrue(associativity(Result.flatMap))
    }

    @Test
    def theLawTestsTellABrokenBindApart(): Unit \ Assert = {
        Assert.assertFalse(leftIdentity(brokenBind));
        Assert.assertFalse(rightIdentity(brokenBind))
    }

    @Test
    def aFailureStopsTheChainAndSkipsTheRest(): Unit \ Assert = {
        let result = forM (
            a <- Ok(1);
            b <- (Err(failure()): Result[ZettaiError, Int32]);
            c <- (Ok(100): Result[ZettaiError, Int32])
        ) yield a + b + c;
        Assert.assertEq(expected = Err(failure()), result)
    }

}
