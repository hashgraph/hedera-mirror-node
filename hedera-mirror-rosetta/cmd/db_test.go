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

package main

import (
	"testing"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

// run the suite
func TestDbSuite(t *testing.T) {
	suite.Run(t, new(dbSuite))
}

type dbSuite struct {
	test.IntegrationTest
	suite.Suite
}

func (suite *dbSuite) SetupSuite() {
	suite.Setup()
}

func (suite *dbSuite) TearDownSuite() {
	suite.TearDown()
}

func (suite *dbSuite) TestConnectToDb() {
	client := connectToDb(suite.DbResource.GetDbConfig())
	err := client.Exec("select 1").Error
	assert.Nil(suite.T(), err)
}

func (suite *dbSuite) TestConnectToDbInvalidPassword() {
	dbConfig := suite.DbResource.GetDbConfig()
	dbConfig.Password = "bad_password_dab"
	client := connectToDb(dbConfig)
	err := client.Exec("select 1").Error
	assert.NotNil(suite.T(), err)
}
