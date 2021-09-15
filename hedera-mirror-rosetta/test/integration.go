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

package test

import (
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/db"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

type IntegrationTest struct {
	DbResource      db.DbResource
	InvalidDbClient *gorm.DB
}

func (it *IntegrationTest) CleanupDb() {
	db.CleanupDb(it.DbResource.GetDb())
}

func (it *IntegrationTest) Setup() {
	it.DbResource = db.SetupDb()

	config := it.DbResource.GetDbConfig()
	config.Password = "bad_password"
	it.InvalidDbClient, _ = gorm.Open(postgres.Open(config.GetDsn()), &gorm.Config{Logger: logger.Discard})
}

func (it IntegrationTest) TearDown() {
	db.TearDownDb(it.DbResource)
}
