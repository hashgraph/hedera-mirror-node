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
	"net/http"

	"github.com/coinbase/rosetta-sdk-go/server"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/weaveworks/common/middleware"
)

const (
	application = "hedera-mirror-rosetta"
	metricsPath = "/metrics"
)

var (
	sizeBuckets = []float64{512, 1024, 10 * 1024, 25 * 1024, 50 * 1024}

	requestBytesHistogram = prometheus.NewHistogramVec(prometheus.HistogramOpts{
		Name:    "hedera_mirror_rosetta_request_bytes",
		Buckets: sizeBuckets,
		Help:    "Size (in bytes) of messages received in the request.",
	}, []string{"method", "route"})

	requestDurationHistogram = prometheus.NewHistogramVec(prometheus.HistogramOpts{
		Name:    "hedera_mirror_rosetta_request_duration",
		Buckets: []float64{.1, .25, .5, 1, 2.5, 5},
		Help:    "Time (in seconds) spent serving HTTP requests.",
	}, []string{"method", "route", "status_code", "ws"})

	requestInflightGauge = prometheus.NewGaugeVec(prometheus.GaugeOpts{
		Name: "hedera_mirror_rosetta_request_inflight",
		Help: "Current number of inflight HTTP requests.",
	}, []string{"method", "route"})

	responseBytesHistogram = prometheus.NewHistogramVec(prometheus.HistogramOpts{
		Name:    "hedera_mirror_rosetta_response_bytes",
		Buckets: sizeBuckets,
		Help:    "Size (in bytes) of messages sent in response.",
	}, []string{"method", "route"})
)

func init() {
	register := prometheus.WrapRegistererWith(prometheus.Labels{"application": application}, prometheus.DefaultRegisterer)
	register.MustRegister(requestBytesHistogram)
	register.MustRegister(requestDurationHistogram)
	register.MustRegister(requestInflightGauge)
	register.MustRegister(responseBytesHistogram)
}

// metricsController holds data used to serve metric requests
type metricsController struct {
}

// NewMetricsController constructs a new MetricsController object
func NewMetricsController() server.Router {
	return &metricsController{}
}

// Routes returns the metrics controller routes
func (c *metricsController) Routes() server.Routes {
	return server.Routes{
		{
			"metrics",
			"GET",
			metricsPath,
			promhttp.Handler().ServeHTTP,
		},
	}
}

// MetricsMiddleware instruments HTTP requests with request metrics
func MetricsMiddleware(next http.Handler) http.Handler {
	return middleware.Instrument{
		Duration:         requestDurationHistogram,
		InflightRequests: requestInflightGauge,
		RequestBodySize:  requestBytesHistogram,
		ResponseBodySize: responseBytesHistogram,
		RouteMatcher:     next.(middleware.RouteMatcher),
	}.Wrap(next)
}
