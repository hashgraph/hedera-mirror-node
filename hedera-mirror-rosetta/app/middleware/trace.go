/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

package middleware

import (
	"fmt"
	log "github.com/sirupsen/logrus"
	"net"
	"net/http"
	"time"
)

const (
	xForwardedForHeader = "X-Forwarded-For"
	xRealIpHeader       = "X-Real-IP"
)

// tracingResponseWriter wraps a regular ResponseWriter in order to store the HTTP status code
type tracingResponseWriter struct {
	http.ResponseWriter
	statusCode int
}

func newTracingResponseWriter(w http.ResponseWriter) *tracingResponseWriter {
	return &tracingResponseWriter{w, http.StatusOK}
}

func (lrw *tracingResponseWriter) WriteHeader(code int) {
	lrw.statusCode = code
	lrw.ResponseWriter.WriteHeader(code)
}

// TracingMiddleware traces requests to the log
func TracingMiddleware(inner http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		clientIpAddress := getClientIpAddress(r)
		path := r.URL.RequestURI()
		loggingResponseWriter := newTracingResponseWriter(w)

		inner.ServeHTTP(loggingResponseWriter, r)

		message := fmt.Sprintf("%s %s %s (%d) in %s",
			clientIpAddress, r.Method, path, loggingResponseWriter.statusCode, time.Since(start))

		if isInternal(path) {
			log.Debug(message)
		} else {
			log.Info(message)
		}
	})
}

func isInternal(path string) bool {
	return path == metricsPath || path == livenessPath || path == readinessPath
}

func getClientIpAddress(r *http.Request) string {
	ipAddress := r.Header.Get(xRealIpHeader)

	if len(ipAddress) == 0 {
		ipAddress = r.Header.Get(xForwardedForHeader)
	}

	if len(ipAddress) == 0 {
		ipAddress, _, _ = net.SplitHostPort(r.RemoteAddr)
	}

	return ipAddress
}
