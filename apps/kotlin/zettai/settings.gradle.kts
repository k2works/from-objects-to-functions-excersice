rootProject.name = "zettai"

// 章の進行にあわせてモジュールを追加する。
// モジュール名は原著のコンパニオンコード（zettai_stepN_*）に対応させ、
// Gradle の慣習にあわせてハイフン区切りにする。
include("zettai-step1-http")
include("zettai-step2-domain")
