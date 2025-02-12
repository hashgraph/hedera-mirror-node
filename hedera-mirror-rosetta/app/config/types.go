/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
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
 */

package config

import (
	"fmt"
	"time"

	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
)

const EntityCacheKey = "entity"

type Config struct {
	Cache               map[string]Cache
	Db                  Db
	Feature             Feature
	Http                Http
	Log                 Log
	Network             string
	NodeRefreshInterval time.Duration `yaml:"nodeRefreshInterval"`
	NodeVersion         string        `yaml:"nodeVersion"`
	Nodes               NodeMap
	Online              bool
	Port                uint16
	Realm               int64
	Response            Response
	Shard               int64
	ShutdownTimeout     time.Duration `yaml:"shutdownTimeout"`
}

type Cache struct {
	MaxSize int `yaml:"maxSize"`
}

type Db struct {
	Host             string
	Name             string
	Password         string
	Pool             Pool
	Port             uint16
	StatementTimeout int `yaml:"statementTimeout"`
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

type Feature struct {
	SubNetworkIdentifier bool `yaml:"subNetworkIdentifier"`
}

type Http struct {
	IdleTimeout       time.Duration `yaml:"idleTimeout"`
	ReadTimeout       time.Duration `yaml:"readTimeout"`
	ReadHeaderTimeout time.Duration `yaml:"readHeaderTimeout"`
	WriteTimeout      time.Duration `yaml:"writeTimeout"`
}

type Log struct {
	Level string
}

type NodeMap map[string]hiero.AccountID

type Pool struct {
	MaxIdleConnections int `yaml:"maxIdleConnections"`
	MaxLifetime        int `yaml:"maxLifetime"`
	MaxOpenConnections int `yaml:"maxOpenConnections"`
}

type Response struct {
	MaxTransactionsInBlock int `yaml:"maxTransactionsInBlock"`
}
