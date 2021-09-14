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
	"time"

	"github.com/coinbase/rosetta-sdk-go/server"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/types"
	"github.com/hellofresh/health-go/v4"
	"github.com/hellofresh/health-go/v4/checks/postgres"
)

const (
	livenessPath  = "/health/liveness"
	readinessPath = "/health/readiness"
)

// healthController holds data used to response to health probes
type healthController struct {
	livenessHealth  *health.Health
	readinessHealth *health.Health
}

// NewHealthController creates a new HealthController object
func NewHealthController(dbConfig types.Db) (server.Router, error) {
	livenessHealth, err := health.New()
	if err != nil {
		return nil, err
	}

	readinessHealth, err := health.New(health.WithChecks(health.Config{
		Name:      "postgresql",
		Timeout:   time.Second * 10,
		SkipOnErr: false,
		Check:     postgres.New(postgres.Config{DSN: dbConfig.GetDsn()}),
	}))

	if err != nil {
		return nil, err
	}

	return &healthController{
		livenessHealth:  livenessHealth,
		readinessHealth: readinessHealth,
	}, nil
}

// Routes returns the Health controller routes
func (c *healthController) Routes() server.Routes {
	return server.Routes{
		{
			"liveness",
			"GET",
			livenessPath,
			c.livenessHealth.HandlerFunc,
		},
		{
			"readiness",
			"GET",
			readinessPath,
			c.readinessHealth.HandlerFunc,
		},
	}
}
