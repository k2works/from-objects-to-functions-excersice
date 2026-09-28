# Zettai（なでしこ3 版）のタスクランナー。
# なでしこ3 にはテストフレームワークが無いので、テストランナーもここに置く。

CNAKO := ./node_modules/.bin/cnako3
SRCS  := $(wildcard src/*.nako3) $(wildcard test/*.nako3) $(wildcard test/ddt/*.nako3) $(wildcard doctest/*.nako3)
TESTS := $(wildcard test/*_test.nako3)
SCENARIOS := $(wildcard doctest/*.nako3)

.PHONY: help check lint test doctest run clean

help: ## タスク一覧を表示する
	@grep -E '^[a-z-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  %-10s %s\n", $$1, $$2}'

check: lint test doctest ## 文法検査・テスト・受け入れシナリオをまとめて実行する

lint: ## 全 .nako3 を構文解析だけして文法エラーを見つける
	@# cnako3 は文法エラーでも終了コードが 0 なので、-A（AST 出力）の
	@# 標準エラー出力が空かどうかで判定する。
	@fail=0; \
	for f in $(SRCS); do \
	  err=$$($(CNAKO) -A "$$f" 2>&1 >/dev/null); \
	  if [ -n "$$err" ]; then echo "NG $$f"; echo "$$err" | head -3; fail=1; \
	  else echo "ok $$f"; fi; \
	done; \
	if [ $$fail -ne 0 ]; then echo "文法エラーがあります"; exit 1; fi; \
	echo "文法エラーなし"

test: ## test/*_test.nako3 を 1 本ずつ実行する
	@# 判定を 2 つ重ねる。テストの失敗は helper.nako3 のテスト結果報告が
	@# 終了コード 1 を返すことで、文法エラー・取り込みエラー・実行時エラーは
	@# cnako3 が終了コード 0 のまま出力に「…エラー]」と書くことで検出する。
	@# [エラー] だけを見ていたときは [実行時エラー] を取りこぼした。
	@pass=0; fail=0; \
	for t in $(TESTS); do \
	  echo "--- $$t"; \
	  out=$$($(CNAKO) "$$t" 2>&1); code=$$?; \
	  echo "$$out"; \
	  if [ $$code -ne 0 ] || echo "$$out" | grep -qE 'エラー\]'; then \
	    fail=$$((fail+1)); echo "  -> FAILED"; \
	  else pass=$$((pass+1)); fi; \
	done; \
	echo "テスト $$pass 本成功 / $$fail 本失敗"; \
	if [ $$fail -ne 0 ]; then exit 1; fi

doctest: ## doctest/ の受け入れシナリオを実行する（サーバを起動して HTTP 経由で叩く）
	@# cnako3 ではテストの中からサーバを起こすと親プロセスが返らないので、
	@# サーバの起動と停止をここに置く。
	@$(CNAKO) src/main.nako3 > /dev/null 2>&1 & \
	pid=$$!; \
	trap "kill $$pid 2>/dev/null" EXIT; \
	sleep 3; \
	fail=0; \
	for t in $(SCENARIOS); do \
	  echo "--- $$t"; \
	  out=$$($(CNAKO) "$$t" 2>&1); code=$$?; \
	  echo "$$out"; \
	  if [ $$code -ne 0 ] || echo "$$out" | grep -qE 'エラー\]'; then fail=1; echo "  -> FAILED"; fi; \
	done; \
	if [ $$fail -ne 0 ]; then exit 1; fi; \
	echo "受け入れシナリオ green"

run: ## アプリケーションを実行する
	@$(CNAKO) src/main.nako3

clean: ## 生成物を削除する
	@rm -f src/*.mjs test/*.mjs
