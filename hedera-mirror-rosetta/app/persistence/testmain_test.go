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

package persistence

import (
	"os"
	"testing"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/db"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	tdb "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/db"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

const (
	truncateAccountBalanceFileSql = "truncate account_balance_file"
	truncateRecordFileSql         = "truncate record_file"
)

var (
	dbResource      tdb.DbResource
	dbClient        interfaces.DbClient
	invalidDbClient interfaces.DbClient
)

type integrationTest struct{}

func (*integrationTest) SetupTest() {
	tdb.CleanupDb(dbResource.GetDb())
}

func addTransaction(
	dbClient interfaces.DbClient,
	consensusNs int64,
	entityId *domain.EntityId,
	nodeAccountId *domain.EntityId,
	payerAccountId domain.EntityId,
	result int16,
	transactionHash []byte,
	transactionType int16,
	validStartNs int64,
	cryptoTransfers []domain.CryptoTransfer,
	nonFeeTransfers []domain.NonFeeTransfer,
	tokenTransfers []domain.TokenTransfer,
	nftTransfers []domain.NftTransfer,
) {
	tx := &domain.Transaction{
		ConsensusNs:          consensusNs,
		ChargedTxFee:         17,
		EntityId:             entityId,
		NodeAccountId:        nodeAccountId,
		PayerAccountId:       payerAccountId,
		Result:               result,
		TransactionHash:      transactionHash,
		Type:                 transactionType,
		ValidDurationSeconds: 120,
		ValidStartNs:         validStartNs,
	}
	tdb.CreateDbRecords(dbClient, tx)

	if len(cryptoTransfers) != 0 {
		tdb.CreateDbRecords(dbClient, cryptoTransfers)
	}

	if len(nonFeeTransfers) != 0 {
		tdb.CreateDbRecords(dbClient, nonFeeTransfers)
	}

	if len(tokenTransfers) != 0 {
		tdb.CreateDbRecords(dbClient, tokenTransfers)
	}

	if len(nftTransfers) != 0 {
		tdb.CreateDbRecords(dbClient, nftTransfers)
	}
}

func setup() {
	dbResource = tdb.SetupDb(true)
	dbClient = db.NewDbClient(dbResource.GetGormDb(), 0)

	config := dbResource.GetDbConfig()
	config.Password = "bad_password"
	invalid, _ := gorm.Open(postgres.Open(config.GetDsn()), &gorm.Config{Logger: logger.Discard})
	invalidDbClient = db.NewDbClient(invalid, 0)
}

func teardown() {
	tdb.TearDownDb(dbResource)
}

func TestMain(m *testing.M) {
	code := 0

	setup()
	defer func() {
		teardown()
		os.Exit(code)
	}()

	code = m.Run()
}
