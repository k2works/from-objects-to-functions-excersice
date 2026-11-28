import { databaseRoute } from "./database-route.ts"
import { domainRoute } from "./domain-route.ts"
import type { Route } from "./driver.ts"
import { httpRoute } from "./http-route.ts"

// 経路を選ぶのは、テストのコードだけです。
export const routes: Record<"domain" | "http" | "database", Route> = {
  domain: domainRoute,
  http: httpRoute,
  database: databaseRoute
}
