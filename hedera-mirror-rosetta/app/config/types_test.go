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

package config

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestDbGetDsn(t *testing.T) {
	db := Db{
		Host:     "127.0.0.1",
		Name:     "mirror_node",
		Password: "mirror_user_pass",
		Pool: Pool{
			MaxIdleConnections: 20,
			MaxLifetime:        2000,
			MaxOpenConnections: 30,
		},
		Port:     5432,
		Username: "mirror_user",
	}
	expected := "host=127.0.0.1 port=5432 user=mirror_user dbname=mirror_node password=mirror_user_pass sslmode=disable"

	assert.Equal(t, expected, db.GetDsn())
}
