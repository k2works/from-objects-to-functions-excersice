import { defineConfig } from "vitest/config"

export default defineConfig({
  test: {
    include: ["zettai-step*/test/**/*.test.ts"],
    passWithNoTests: false
  }
})
