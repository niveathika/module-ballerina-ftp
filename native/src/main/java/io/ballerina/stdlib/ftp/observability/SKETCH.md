# Observability sketch — tag-only enrichment

Alternative to [PR #1576](https://github.com/ballerina-platform/module-ballerina-ftp/pull/1576).

## Premise

The Ballerina runtime already auto-instruments every `client->method()` call and
every listener dispatch with these metrics:

- `requests_total_value` (counter)
- `response_time_seconds*` (summary + per-window mean/max/min/stdDev)
- `response_time_nanoseconds_total_value` (counter)
- `inprogress_requests_value` (gauge)

…and a server-/client-span pair in Jaeger. Tags from the active `ObserverContext`
flow into both metrics and spans.

`module-ballerina-http` does **not** register any metrics of its own; it only
enriches the existing `ObserverContext` with HTTP-specific tags
(`http_method`, `http_url`, `peer_address`, `http_status_code_group`, …) — see
[`HttpUtil.java` lines 1222‑1238](../../../../../../module-ballerina-http/native/src/main/java/io/ballerina/stdlib/http/api/HttpUtil.java).

This sketch applies the same approach to FTP. PR #1576's `ftp_file_operations`,
`ftp_file_events`, and `ftp_errors` counters all become equivalent PromQL slices
of the runtime-emitted metrics:

```promql
# "ftp_file_operations" by op type
rate(requests_total_value{operation_type="put", protocol="sftp"}[1m])

# "ftp_file_events" by event
rate(requests_total_value{event_type="change",
                          src_object_name=~".*ftp_listener.*"}[1m])

# "ftp_errors" by category
sum by (error_type) (
  rate(requests_total_value{error="true",
                            src_module=~"ballerina/ftp.*"}[5m]))

# p95 latency per op
histogram_quantile(0.95, response_time_seconds{operation_type="get"})
```

The **one** metric this sketch still registers is `ftp_active_connections`
(gauge) — it tracks state, not per-call counts, so it cannot be derived from
the runtime metrics.

## What this branch changes

| File | Change |
|---|---|
| `observability/FtpObservabilityUtil.java` | **New.** Single utility: `enrichClientSpan`, `newListenerObserverContext`, `markError`, `recordConnectionOpen`/`Close`. Registers exactly one metric (`ftp_active_connections`). |
| `client/FtpClient.java` | Hooks `getBytes`, `putBytes`, `delete` (representative) + connection open/close. Adds a stub `classifyError(BError)`. Other typed methods (`getText`/`getJson`/`putText`/… `rename`/`mkdir`/`rmdir`/…) carry a `SKETCH NOTE` showing the same one-liner pattern. |
| `server/FtpListener.java` | Hooks `invokeMethodAsync` (the `onFileChange` dispatch). Other dispatch sites and `FtpListenerHelper` content-method dispatch carry a `SKETCH NOTE` showing the same pattern. |

`SKETCH NOTE` markers point to every spot that would need the same one-liner
in a real PR — they're intentionally left for the reviewer to confirm the
approach before doing the mechanical fan-out.

## What is intentionally **not** here

- A complete fan-out across all client typed methods, all stream variants,
  the deprecated `put`/`get`/`append`, and all listener dispatch sites.
- A real `BError → error_type` classifier — current `classifyError` is a stub.
- Wiring of `peer_address` + `protocol` tags on the listener side
  (`observabilityProperties` passes `null, null` today). The listener BObject
  needs the URL+protocol threaded through `ServiceContext` during
  `FtpListenerHelper.register`.
- Updates to `changelog.md`, `spec/spec.md`, or `ballerina-tests/`.
- A compiler-plugin check for `observabilityIncluded` build option.

## Tag set proposed

| Tag | Where | Cardinality concern |
|---|---|---|
| `ftp_context` (`client`/`listener`) | metric + span | low |
| `protocol` (`ftp`/`ftps`/`sftp`) | metric + span | low — reuses `ObservabilityConstants.TAG_KEY_PROTOCOL` so it lines up with HTTP dashboards |
| `peer_address` (`host:port`) | metric + span | low — reuses `TAG_KEY_PEER_ADDRESS` |
| `operation_type` (`get`/`put`/`delete`/…) | metric + span | low |
| `event_type` (`change`/`delete`/`error`) | metric + span | low |
| `error` / `error_type` | metric + span | low |
| `file.path` | **span only** | high — never on metrics |
| `destination.path` | **span only** | high — never on metrics |

## How to compare with PR #1576

Both this sketch and PR #1576 cost roughly the same number of source lines.
The trade-off is:

- **PR #1576:** custom metrics with FTP-namespaced names (`ftp_file_operations`,
  `ftp_file_events`, `ftp_errors`). Dedicated namespace, easy mental model,
  but four new things to keep in sync as the FTP API evolves and queries don't
  align with how operators query HTTP/Kafka/etc.

- **This sketch:** one custom metric (the gauge). Everything else is a tag on
  what the runtime already emits. Half-zero metric maintenance, queries align
  with the rest of the Ballerina ecosystem, but PromQL is a bit longer (e.g.
  `requests_total_value{operation_type="put"}` vs `ftp_file_operations{operation_type="put"}`).

Either approach captures the same dimensional data. The choice is mostly about
where the FTP-vs-rest-of-Ballerina consistency boundary should sit.
