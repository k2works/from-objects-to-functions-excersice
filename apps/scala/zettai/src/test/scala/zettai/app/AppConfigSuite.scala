package zettai.app

import com.comcast.ip4s.*
import munit.FunSuite

class AppConfigSuite extends FunSuite:

  test("指定が無ければ 8080 番を使う") {
    assertEquals(AppConfig.portFrom(None), Right(port"8080"))
  }

  test("環境変数の値でポートを変えられる") {
    assertEquals(AppConfig.portFrom(Some("9090")), Right(port"9090"))
    assertEquals(AppConfig.portFrom(Some("0")), Right(port"0"))
  }

  test("数字でない値や範囲外の値は、起動前に失敗の値になる") {
    for bad <- List("", "abc", "-1", "65536", "80 80") do
      assert(AppConfig.portFrom(Some(bad)).isLeft, s"通ってしまった: $bad")
  }
