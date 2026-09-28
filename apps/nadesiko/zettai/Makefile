# Zettai（なでしこ3 版）のタスクランナー。
# なでしこ3 にはテストフレームワークが無いので、テストランナーもここに置く。
#
# src/stepN/ は章の段階ごとのスナップショット。Kotlin 版がモジュールを
# Unit の境界で切っているのに対応する（ADR-015）。過去の章のコードが
# その場に残るので、記事のコード例が実装から消えない。
#
# STEP=step1 のように指定すると 1 つの段階だけを扱える。

CNAKO := ./node_modules/.bin/cnako3
# 並列数。ファイルごとにプロセスを起こすので、段階が増えるほど効く。
JOBS  ?= 8
STEPS := $(notdir $(wildcard src/step*))
# STEP を指定しなければ「最新の段階だけ」を対象にする。
# 書いている間の待ち時間を短く保つため（ADR-015）。
# 全段階は make check-all（CI が毎回回す）。
STEP  ?=
LATEST_STEP := $(lastword $(sort $(STEPS)))
TARGET_STEPS := $(if $(STEP),$(if $(filter all,$(STEP)),$(STEPS),$(STEP)),$(LATEST_STEP))

SRCS  := $(foreach s,$(TARGET_STEPS),$(wildcard src/$(s)/*.nako3) $(wildcard test/$(s)/*.nako3) $(wildcard test/$(s)/ddt/*.nako3) $(wildcard doctest/$(s)/*.nako3))
TESTS := $(foreach s,$(TARGET_STEPS),$(wildcard test/$(s)/*_test.nako3))
SCENARIOS := $(foreach s,$(TARGET_STEPS),$(wildcard doctest/$(s)/*.nako3))

# 最後の段階が「今の Zettai」。make run はこれを起動する。
LATEST := $(lastword $(sort $(STEPS)))

.PHONY: help check check-all lint test doctest run clean steps

help: ## タスク一覧を表示する
	@grep -E '^[a-z-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  %-10s %s\n", $$1, $$2}'

steps: ## 段階の一覧を表示する
	@echo "段階: $(STEPS)（最新: $(LATEST)）"
	@echo "make check は $(TARGET_STEPS) を対象にする。全段階は make check-all"

check: lint test doctest ## 最新の段階だけを検査する（書いている間はこれ）

check-all: ## 全段階を検査する（CI が毎回回す）
	@$(MAKE) --no-print-directory check STEP=all

lint: ## 全 .nako3 を構文解析だけして文法エラーを見つける
	@# cnako3 は文法エラーでも終了コードが 0 なので、-A（AST 出力）の
	@# 標準エラー出力が空かどうかで判定する。
	@# ファイル 1 つごとにプロセスを起こすため、段階が増えると遅くなる。
	@# 並列で回す（-P 8）。1 ファイルの検査は互いに独立している。
	@# -I{} は BSD xargs で行長の上限に当たるので、-n 1 でファイルを $$0 に渡す。
	@echo "$(SRCS)" | tr ' ' '\n' | xargs -P $(JOBS) -n 1 sh -c \
	  'err=$$($(CNAKO) -A "$$0" 2>&1 >/dev/null); \
	   if [ -n "$$err" ]; then echo "NG $$0"; echo "$$err" | head -3; exit 1; \
	   else echo "ok $$0"; fi' \
	  || { echo "文法エラーがあります"; exit 1; }
	@echo "文法エラーなし"

test: ## test/stepN/*_test.nako3 を 1 本ずつ実行する
	@# 判定を 2 つ重ねる。テストの失敗は helper.nako3 のテスト結果報告が
	@# 終了コード 1 を返すことで、文法エラー・取り込みエラー・実行時エラーは
	@# cnako3 が終了コード 0 のまま出力に「…エラー]」と書くことで検出する。
	@# [エラー] だけを見ていたときは [実行時エラー] を取りこぼした。
	@# テストも段階が増えると本数で効いてくる。互いに独立なので並列で回す。
	@echo "$(TESTS)" | tr ' ' '\n' | xargs -P $(JOBS) -n 1 sh -c \
	  'out=$$($(CNAKO) "$$0" 2>&1); code=$$?; \
	   echo "--- $$0"; echo "$$out"; \
	   if [ $$code -ne 0 ] || echo "$$out" | grep -qE "エラー\]"; then echo "  -> FAILED $$0"; exit 1; fi' \
	  || { echo "失敗したテストがあります"; exit 1; }
	@echo "テスト green" 

doctest: ## doctest/stepN/ の受け入れシナリオを段階ごとに実行する
	@# cnako3 ではテストの中からサーバを起こすと親プロセスが返らないので、
	@# サーバの起動と停止をここに置く。段階ごとにサーバを立て直す。
	@# 第 9 章から、起動したサーバがイベントログを作る。
	@# テストが作ったものを残さない。段階ごとに前後で消す。
	@fail=0; \
	for s in $(TARGET_STEPS); do \
	  scenarios=$$(ls doctest/$$s/*.nako3 2>/dev/null); \
	  if [ -z "$$scenarios" ]; then continue; fi; \
	  rm -f .zettai_events.log; \
	  $(CNAKO) src/$$s/main.nako3 > /dev/null 2>&1 & \
	  pid=$$!; \
	  n=0; \
	  until curl -s -o /dev/null -m 1 http://localhost:8080/ || [ $$n -ge 40 ]; do sleep 0.25; n=$$((n+1)); done; \
	  for t in $$scenarios; do \
	    echo "--- $$t"; \
	    out=$$($(CNAKO) "$$t" 2>&1); code=$$?; \
	    echo "$$out"; \
	    if [ $$code -ne 0 ] || echo "$$out" | grep -qE 'エラー\]'; then fail=1; echo "  -> FAILED"; fi; \
	  done; \
	  kill $$pid 2>/dev/null; \
	  rm -f .zettai_events.log; \
	  sleep 1; \
	done; \
	if [ $$fail -ne 0 ]; then exit 1; fi; \
	echo "受け入れシナリオ green"

run: ## 最新の段階のアプリケーションを実行する
	@$(CNAKO) src/$(LATEST)/main.nako3

clean: ## 生成物を削除する
	@rm -f src/*/*.mjs test/*/*.mjs
