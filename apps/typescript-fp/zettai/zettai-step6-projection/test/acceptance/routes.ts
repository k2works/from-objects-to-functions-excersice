import { domainRoute } from "./domain-route.ts"
import type { Route } from "./driver.ts"
import { httpRoute } from "./http-route.ts"

// 経路を選ぶのは、テストのコードだけです。
export const routes: Record<"domain" | "http", Route> = {
  domain: domainRoute,
  http: httpRoute
}
