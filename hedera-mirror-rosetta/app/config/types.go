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
	"fmt"

	"github.com/hashgraph/hedera-sdk-go/v2"
)

type Config struct {
	Hedera Hedera
}

type Hedera struct {
	Mirror Mirror
}

type Mirror struct {
	Rosetta Rosetta
}

type Rosetta struct {
	Db          Db
	Log         Log
	Network     string
	Nodes       NodeMap
	NodeVersion string
	Online      bool
	Port        uint16
	Realm       string
	Shard       string
}

type Db struct {
	Host             string
	Name             string
	Password         string
	Pool             Pool
	Port             uint16
	StatementTimeout uint
	Username         string
}

func (db Db) GetDsn() string {
	return fmt.Sprintf(
		"host=%s port=%d user=%s dbname=%s password=%s sslmode=disable",
		db.Host,
		db.Port,
		db.Username,
		db.Name,
		db.Password,
	)
}

type Log struct {
	Level string
}

type NodeMap map[string]hedera.AccountID

type Pool struct {
	MaxIdleConnections int
	MaxLifetime        int
	MaxOpenConnections int
}
