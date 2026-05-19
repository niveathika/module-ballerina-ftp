/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.ftp.observability;

import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.observability.ObservabilityConstants;
import io.ballerina.runtime.observability.ObserveUtils;
import io.ballerina.runtime.observability.ObserverContext;
import io.ballerina.runtime.observability.metrics.Counter;
import io.ballerina.runtime.observability.metrics.DefaultMetricRegistry;
import io.ballerina.runtime.observability.metrics.Gauge;
import io.ballerina.runtime.observability.metrics.MetricId;
import io.ballerina.runtime.observability.metrics.MetricRegistry;
import io.ballerina.runtime.observability.metrics.Tag;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tag-only observability hooks for the FTP connector.
 *
 * <p>The pattern mirrors {@code module-ballerina-http}: instead of introducing
 * connector-specific counters/summaries, we enrich the {@link ObserverContext}
 * that the Ballerina runtime already attaches to each {@code client->method()}
 * invocation and each listener dispatch. The runtime-emitted metrics
 * ({@code requests_total_value}, {@code response_time_seconds*},
 * {@code inprogress_requests_value}, {@code response_time_nanoseconds_total_value})
 * inherit our tags automatically — so a single PromQL like
 * {@code rate(requests_total_value{operation_type="put",protocol="sftp"}[1m])}
 * gives an "FTP file ops by operation type and protocol" view without
 * us ever registering an {@code ftp_file_operations} counter.
 *
 * <p>The only metric registered here is {@code ftp_active_connections}, a
 * gauge that tracks state (open client/listener sessions) and is therefore
 * not derivable from per-call counters.
 *
 * <p>High-cardinality fields ({@code file.path}, {@code destination.path})
 * go on the span only, never on the metric tag set — see
 * {@link #enrichClientSpan(Environment, String, String, String, String)}.
 */
public final class FtpObservabilityUtil {

    private static final String CONNECTOR_NAME = "ftp";

    public static final String CONTEXT_CLIENT = "client";
    public static final String CONTEXT_LISTENER = "listener";

    // Operation types (client side).
    public static final String OP_GET = "get";
    public static final String OP_PUT = "put";
    public static final String OP_DELETE = "delete";
    public static final String OP_APPEND = "append";
    public static final String OP_RENAME = "rename";
    public static final String OP_MKDIR = "mkdir";
    public static final String OP_RMDIR = "rmdir";
    public static final String OP_LIST = "list";
    public static final String OP_SIZE = "size";
    public static final String OP_IS_DIRECTORY = "isDirectory";

    // Event types (listener side).
    public static final String EVENT_CHANGE = "change";
    public static final String EVENT_DELETE = "delete";
    public static final String EVENT_ERROR = "error";

    // Error categories.
    public static final String ERR_CONNECTION = "connection";
    public static final String ERR_AUTHENTICATION = "authentication";
    public static final String ERR_FILE_NOT_FOUND = "file_not_found";
    public static final String ERR_FILE_ALREADY_EXISTS = "file_already_exists";
    public static final String ERR_CONTENT_BINDING = "content_binding";
    public static final String ERR_SERVICE_UNAVAILABLE = "service_unavailable";
    public static final String ERR_INVALID_CONFIG = "invalid_config";
    public static final String ERR_CLOSE = "close";
    public static final String ERR_OTHER = "other";

    // FTP-specific tag keys. Standard ones (protocol, peer_address, error)
    // reuse ObservabilityConstants so they line up with module-ballerina-http
    // dashboards out of the box.
    public static final String TAG_CONTEXT = "ftp_context";
    public static final String TAG_OPERATION_TYPE = "operation_type";
    public static final String TAG_EVENT_TYPE = "event_type";
    public static final String TAG_ERROR_TYPE = "error_type";
    public static final String TAG_FILE_PATH = "file.path";
    public static final String TAG_DESTINATION_PATH = "destination.path";

    // ftp_active_connections is the only metric we register. Counters/summaries
    // for ops/events/errors are intentionally NOT created — they would
    // duplicate the runtime-emitted requests_total / response_time_seconds.
    private static final MetricRegistry registry = DefaultMetricRegistry.getInstance();
    private static final String ACTIVE_CONNECTIONS = "ftp_active_connections";
    private static final ConcurrentMap<String, Gauge> activeConnectionsByTags = new ConcurrentHashMap<>();

    private FtpObservabilityUtil() { }

    // ─── Client-side span enrichment ─────────────────────────────────────

    /**
     * Adds FTP-specific tags to the ObserverContext of the current native frame.
     * File path goes on the span only (high cardinality); operation type, protocol,
     * peer address, and context tag also flow to the metric tag set.
     */
    public static void enrichClientSpan(Environment env, String url, String protocol,
                                        String operationType, String filePath) {
        enrichClientSpan(env, url, protocol, operationType, filePath, null);
    }

    /** Two-path variant (rename, move, copy). */
    public static void enrichClientSpan(Environment env, String url, String protocol,
                                        String operationType, String filePath, String destinationPath) {
        if (!ObserveUtils.isObservabilityEnabled()) {
            return;
        }
        ObserverContext ctx = ObserveUtils.getObserverContextOfCurrentFrame(env);
        if (ctx == null) {
            return;
        }
        ctx.addTag(TAG_CONTEXT, CONTEXT_CLIENT);
        if (protocol != null) {
            ctx.addTag(ObservabilityConstants.TAG_KEY_PROTOCOL, protocol);
        }
        if (url != null) {
            ctx.addTag(ObservabilityConstants.TAG_KEY_PEER_ADDRESS, url);
        }
        if (operationType != null) {
            ctx.addTag(TAG_OPERATION_TYPE, operationType);
        }
        // file.path / destination.path are span-only to keep metric cardinality bounded.
        if (ctx.getSpan() != null) {
            if (filePath != null) {
                ctx.getSpan().addTag(TAG_FILE_PATH, filePath);
            }
            if (destinationPath != null) {
                ctx.getSpan().addTag(TAG_DESTINATION_PATH, destinationPath);
            }
        }
    }

    // ─── Listener-side span enrichment ───────────────────────────────────

    /**
     * Returns the FTP-specific tags that should be attached to the ObserverContext
     * for a listener dispatch. The caller (FtpListener / FtpListenerHelper) folds
     * these into the StrandMetadata properties map under
     * {@link ObservabilityConstants#KEY_OBSERVER_CONTEXT} so the runtime can pick
     * them up when it starts the strand frame.
     *
     * <p>Returns {@code null} when observability is disabled, so callers can pass
     * the result directly to {@code new StrandMetadata(..., props)}.
     */
    public static ObserverContext newListenerObserverContext(String url, String protocol,
                                                              String eventType, String filePath) {
        if (!ObserveUtils.isObservabilityEnabled()) {
            return null;
        }
        ObserverContext ctx = new ObserverContext();
        ctx.addTag(TAG_CONTEXT, CONTEXT_LISTENER);
        if (protocol != null) {
            ctx.addTag(ObservabilityConstants.TAG_KEY_PROTOCOL, protocol);
        }
        if (url != null) {
            ctx.addTag(ObservabilityConstants.TAG_KEY_PEER_ADDRESS, url);
        }
        if (eventType != null) {
            ctx.addTag(TAG_EVENT_TYPE, eventType);
        }
        // file.path tagged on the context's future span (set by the runtime) is
        // expensive to inject pre-span — leave it out and let the listener
        // add it to the active span once dispatch starts, if needed.
        if (filePath != null) {
            ctx.addProperty(TAG_FILE_PATH, filePath);
        }
        return ctx;
    }

    // ─── Error tagging ───────────────────────────────────────────────────

    /**
     * Marks the current frame's ObserverContext as an error and attaches an
     * {@code error_type} tag. Call this after a native operation returned a
     * BError, before returning to the Ballerina runtime.
     */
    public static void markError(Environment env, String errorType) {
        if (!ObserveUtils.isObservabilityEnabled()) {
            return;
        }
        ObserverContext ctx = ObserveUtils.getObserverContextOfCurrentFrame(env);
        if (ctx == null) {
            return;
        }
        ctx.addTag(ObservabilityConstants.TAG_KEY_ERROR, ObservabilityConstants.TAG_TRUE_VALUE);
        if (errorType != null) {
            ctx.addTag(TAG_ERROR_TYPE, errorType);
        }
    }

    // ─── ftp_active_connections gauge ────────────────────────────────────

    public static void recordConnectionOpen(String url, String protocol, String context) {
        if (!ObserveUtils.isMetricsEnabled()) {
            return;
        }
        gaugeFor(url, protocol, context).increment();
    }

    public static void recordConnectionClose(String url, String protocol, String context) {
        if (!ObserveUtils.isMetricsEnabled()) {
            return;
        }
        gaugeFor(url, protocol, context).decrement();
    }

    private static Gauge gaugeFor(String url, String protocol, String context) {
        String key = (protocol == null ? "" : protocol) + "|" + (url == null ? "" : url) + "|" + context;
        return activeConnectionsByTags.computeIfAbsent(key, k -> {
            Set<Tag> tags = new HashSet<>();
            tags.add(Tag.of(ObservabilityConstants.TAG_KEY_PROTOCOL, protocol == null ? "" : protocol));
            tags.add(Tag.of(ObservabilityConstants.TAG_KEY_PEER_ADDRESS, url == null ? "" : url));
            tags.add(Tag.of(TAG_CONTEXT, context));
            MetricId id = new MetricId(ACTIVE_CONNECTIONS,
                    "Number of active FTP/FTPS/SFTP connections", tags);
            return registry.gauge(id);
        });
    }

    /** Test-visible: clear the gauge cache between runs. */
    static void resetForTesting() {
        activeConnectionsByTags.clear();
    }
}
