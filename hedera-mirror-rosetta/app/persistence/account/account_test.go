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

package account

import (
	"testing"

	entityid "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	dbTypes "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

var (
	account               int64 = 9000
	accountString               = "0.0.9000"
	account2              int64 = 9001
	consensusEnd          int64 = 200
	snapshotTimestamp     int64 = 100
	cryptoTransferAmounts       = []int64{150, -178}
	token1                      = &dbTypes.Token{
		TokenId:           1001,
		CreatedTimestamp:  10,
		Decimals:          8,
		InitialSupply:     100000,
		ModifiedTimestamp: 10,
		Name:              "token1",
		Symbol:            "token1",
		TotalSupply:       200000,
	}
	token2 = &dbTypes.Token{
		TokenId:           1002,
		CreatedTimestamp:  12,
		Decimals:          9,
		InitialSupply:     200000,
		ModifiedTimestamp: 12,
		Name:              "token2",
		Symbol:            "token2",
		TotalSupply:       800000,
	}
	token1EntityId = entityid.EntityId{
		EntityNum: token1.TokenId,
		EncodedId: token1.TokenId,
	}
	token2EntityId = entityid.EntityId{
		EntityNum: token2.TokenId,
		EncodedId: token2.TokenId,
	}
	token1TransferAmounts      = []int64{10, -5}
	token2TransferAmounts      = []int64{20, -7}
	snapshotAccountBalanceFile = &accountBalanceFile{
		ConsensusTimestamp: snapshotTimestamp,
		Count:              100,
		LoadStart:          1600,
		LoadEnd:            1650,
		FileHash:           "filehash",
		Name:               "account balance file 1",
		NodeAccountId:      3,
	}
	initialTokenBalances = []*tokenBalance{
		{
			AccountId:          account,
			ConsensusTimestamp: snapshotTimestamp,
			Balance:            112,
			TokenId:            token1.TokenId,
		},
		{
			AccountId:          account,
			ConsensusTimestamp: snapshotTimestamp,
			Balance:            280,
			TokenId:            token2.TokenId,
		},
	}
	initialAccountBalance = &accountBalance{
		ConsensusTimestamp: snapshotTimestamp,
		Balance:            12345,
		AccountId:          account,
	}
	// the last crypto transfer is after consensusEnd
	cryptoTransfers = []*dbTypes.CryptoTransfer{
		{
			EntityId:           account,
			Amount:             cryptoTransferAmounts[0],
			ConsensusTimestamp: snapshotTimestamp + 1,
		},
		{
			EntityId:           account,
			Amount:             cryptoTransferAmounts[1],
			ConsensusTimestamp: snapshotTimestamp + 5,
		},
		{
			EntityId:           account,
			Amount:             155,
			ConsensusTimestamp: consensusEnd + 1,
		},
	}
	// crypto transfers at or before snapshot timestamp
	cryptoTransfersLTESnapshot = []*dbTypes.CryptoTransfer{
		{
			EntityId:           account,
			Amount:             110,
			ConsensusTimestamp: snapshotTimestamp - 1,
		},
		{
			EntityId:           account,
			Amount:             170,
			ConsensusTimestamp: snapshotTimestamp,
		},
	}
	// the last transfer of each token is after consensusEnd
	tokenTransfers = []*tokenTransfer{
		{
			AccountId:          account,
			Amount:             token1TransferAmounts[0],
			ConsensusTimestamp: snapshotTimestamp + 2,
			TokenId:            token1.TokenId,
		},
		{
			AccountId:          account,
			Amount:             token1TransferAmounts[1],
			ConsensusTimestamp: snapshotTimestamp + 4,
			TokenId:            token1.TokenId,
		},
		{
			AccountId:          account,
			Amount:             153,
			ConsensusTimestamp: consensusEnd + 1,
			TokenId:            token1.TokenId,
		},
		{
			AccountId:          account,
			Amount:             token2TransferAmounts[0],
			ConsensusTimestamp: snapshotTimestamp + 5,
			TokenId:            token2.TokenId,
		},
		{
			AccountId:          account,
			Amount:             token2TransferAmounts[1],
			ConsensusTimestamp: snapshotTimestamp + 8,
			TokenId:            token2.TokenId,
		},
		{
			AccountId:          account,
			Amount:             157,
			ConsensusTimestamp: consensusEnd + 1,
			TokenId:            token2.TokenId,
		},
	}
	// token transfers at or before snapshot timestamp
	tokenTransfersLTESnapshot = []*tokenTransfer{
		{
			AccountId:          account,
			Amount:             17,
			ConsensusTimestamp: snapshotTimestamp - 1,
			TokenId:            token1.TokenId,
		},
		{
			AccountId:          account,
			Amount:             -2,
			ConsensusTimestamp: snapshotTimestamp,
			TokenId:            token1.TokenId,
		},
		{
			AccountId:          account,
			Amount:             25,
			ConsensusTimestamp: snapshotTimestamp - 1,
			TokenId:            token2.TokenId,
		},
		{
			AccountId:          account,
			Amount:             -9,
			ConsensusTimestamp: snapshotTimestamp,
			TokenId:            token2.TokenId,
		},
	}
	tokenTransfersInNextBlock = []*tokenTransfer{
		{
			AccountId:          account,
			Amount:             5,
			ConsensusTimestamp: consensusEnd + 1,
			TokenId:            token1.TokenId,
		},
		{
			AccountId:          account2,
			Amount:             8,
			ConsensusTimestamp: consensusEnd + 2,
			TokenId:            token1.TokenId,
		},
	}
	nextRecordFile = &dbTypes.RecordFile{
		ConsensusStart: consensusEnd + 1,
		ConsensusEnd:   consensusEnd + 10,
		Count:          5,
		FileHash:       "filehash",
		Hash:           "hash",
		Index:          5,
		Name:           "next_record_file.rcd",
		PrevHash:       "prevhash",
		Version:        5,
	}
)

type accountBalance struct {
	AccountId          int64 `gorm:"primaryKey"`
	Balance            int64
	ConsensusTimestamp int64 `gorm:"primaryKey"`
}

func (accountBalance) TableName() string {
	return "account_balance"
}

type accountBalanceFile struct {
	ConsensusTimestamp int64 `gorm:"primaryKey"`
	Count              int64 `gorm:"not null"`
	LoadStart          int64 `gorm:"not null"`
	LoadEnd            int64 `gorm:"not null"`
	FileHash           string
	Name               string `gorm:"not null"`
	NodeAccountId      int64  `gorm:"not null"`
	Bytes              []byte
}

func (accountBalanceFile) TableName() string {
	return "account_balance_file"
}

type tokenBalance struct {
	AccountId          int64
	Balance            int64
	ConsensusTimestamp int64
	TokenId            int64
}

func (tokenBalance) TableName() string {
	return "token_balance"
}

type tokenTransfer struct {
	AccountId          int64
	Amount             int64
	ConsensusTimestamp int64
	TokenId            int64
}

func (tokenTransfer) TableName() string {
	return "token_transfer"
}

// run the suite
func TestAccountRepositorySuite(t *testing.T) {
	suite.Run(t, new(accountRepositorySuite))
}

type accountRepositorySuite struct {
	test.IntegrationTest
	suite.Suite
}

func (suite *accountRepositorySuite) SetupSuite() {
	suite.Setup()
}

func (suite *accountRepositorySuite) TearDownSuite() {
	suite.TearDown()
}

func (suite *accountRepositorySuite) SetupTest() {
	suite.CleanupDb()
	db.CreateDbRecords(suite.DbResource.GetGormDb(), snapshotAccountBalanceFile)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlock() {
	// given
	dbClient := suite.DbResource.GetGormDb()
	db.CreateDbRecords(dbClient, token1, token2)
	db.CreateDbRecords(dbClient, initialAccountBalance, initialTokenBalances)
	// transfers before or at the snapshot timestamp should not affect balance calculation
	db.CreateDbRecords(dbClient, cryptoTransfersLTESnapshot, tokenTransfersLTESnapshot)
	db.CreateDbRecords(dbClient, cryptoTransfers, tokenTransfers)

	repo := NewAccountRepository(dbClient)

	hbarAmount := &types.HbarAmount{Value: initialAccountBalance.Balance + sum(cryptoTransferAmounts)}
	token1Amount := &types.TokenAmount{
		TokenId:  token1EntityId,
		Decimals: token1.Decimals,
		Value:    initialTokenBalances[0].Balance + sum(token1TransferAmounts),
	}
	token2Amount := &types.TokenAmount{
		TokenId:  token2EntityId,
		Decimals: token2.Decimals,
		Value:    initialTokenBalances[1].Balance + sum(token2TransferAmounts),
	}

	expected := []types.Amount{hbarAmount, token1Amount, token2Amount}

	// when
	actual, err := repo.RetrieveBalanceAtBlock(accountString, consensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assert.ElementsMatch(suite.T(), expected, actual)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockNoTokenEntity() {
	// given
	dbClient := suite.DbResource.GetGormDb()
	db.CreateDbRecords(dbClient, initialAccountBalance, initialTokenBalances)
	// transfers before or at the snapshot timestamp should not affect balance calculation
	db.CreateDbRecords(dbClient, cryptoTransfersLTESnapshot, tokenTransfersLTESnapshot)
	db.CreateDbRecords(dbClient, cryptoTransfers, tokenTransfers)

	repo := NewAccountRepository(dbClient)

	// no token entities, so only hbar balance
	hbarAmount := &types.HbarAmount{Value: initialAccountBalance.Balance + sum(cryptoTransferAmounts)}
	expected := []types.Amount{hbarAmount}

	// when
	actual, err := repo.RetrieveBalanceAtBlock(accountString, consensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assert.ElementsMatch(suite.T(), expected, actual)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockNoInitialBalance() {
	// given
	dbClient := suite.DbResource.GetGormDb()
	db.CreateDbRecords(dbClient, token1, token2)
	db.CreateDbRecords(dbClient, cryptoTransfers, tokenTransfers)

	repo := NewAccountRepository(dbClient)

	hbarAmount := &types.HbarAmount{Value: sum(cryptoTransferAmounts)}
	token1Amount := &types.TokenAmount{
		TokenId:  token1EntityId,
		Decimals: token1.Decimals,
		Value:    sum(token1TransferAmounts),
	}
	token2Amount := &types.TokenAmount{
		TokenId:  token2EntityId,
		Decimals: token2.Decimals,
		Value:    sum(token2TransferAmounts),
	}
	expected := []types.Amount{hbarAmount, token1Amount, token2Amount}

	// when
	actual, err := repo.RetrieveBalanceAtBlock(accountString, consensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assert.ElementsMatch(suite.T(), expected, actual)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockNoAccountBalanceFile() {
	// given
	dbClient := suite.DbResource.GetGormDb()
	db.ExecSql(dbClient, "truncate account_balance_file")
	repo := NewAccountRepository(dbClient)

	// when
	actual, err := repo.RetrieveBalanceAtBlock(accountString, consensusEnd)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockInvalidAccountIdStr() {
	// given
	dbClient := suite.DbResource.GetGormDb()
	repo := NewAccountRepository(dbClient)

	// when
	actual, err := repo.RetrieveBalanceAtBlock("a", consensusEnd)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockDbConnectionError() {
	// given
	repo := NewAccountRepository(suite.InvalidDbClient)

	// when
	actual, err := repo.RetrieveBalanceAtBlock(accountString, consensusEnd)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *accountRepositorySuite) TestRetrieveTransferredTokensInBlockAfter() {
	// given
	dbClient := suite.DbResource.GetGormDb()
	db.CreateDbRecords(dbClient, token1, token2)
	db.CreateDbRecords(dbClient, initialAccountBalance, initialTokenBalances[0])
	// add the next record file, and a token transfer in the record file
	db.CreateDbRecords(dbClient, cryptoTransfers, nextRecordFile, tokenTransfersInNextBlock)

	repo := NewAccountRepository(dbClient)

	expected := []types.Token{{
		TokenId:  token1EntityId,
		Decimals: uint32(token1.Decimals),
	}}

	// when
	actual, err := repo.RetrieveTransferredTokensInBlockAfter(accountString, consensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assert.ElementsMatch(suite.T(), expected, actual)
}

func (suite *accountRepositorySuite) TestRetrieveTransferredTokensInBlockAfterNoTransferredTokens() {
	// given
	dbClient := suite.DbResource.GetGormDb()
	db.CreateDbRecords(dbClient, token1, token2)
	db.CreateDbRecords(dbClient, initialAccountBalance, initialTokenBalances[0])
	// add the next record file, and a token transfer in the record file
	db.CreateDbRecords(dbClient, cryptoTransfers, nextRecordFile)

	repo := NewAccountRepository(dbClient)

	// when
	actual, err := repo.RetrieveTransferredTokensInBlockAfter(accountString, consensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assert.Empty(suite.T(), actual)
}

func (suite *accountRepositorySuite) TestRetrieveTransferredTokensInBlockAfterNoNextBlock() {
	// given
	dbClient := suite.DbResource.GetGormDb()
	db.CreateDbRecords(dbClient, token1, token2)
	db.CreateDbRecords(dbClient, initialAccountBalance, initialTokenBalances[0])
	// add the next record file, and a token transfer in the record file
	db.CreateDbRecords(dbClient, cryptoTransfers)

	repo := NewAccountRepository(dbClient)

	// when
	actual, err := repo.RetrieveTransferredTokensInBlockAfter(accountString, consensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assert.Empty(suite.T(), actual)
}

func (suite *accountRepositorySuite) TestRetrieveTransferredTokensInBlockAfterInvalidAccountIdStr() {
	// given
	dbClient := suite.DbResource.GetGormDb()
	repo := NewAccountRepository(dbClient)

	// when
	actual, err := repo.RetrieveTransferredTokensInBlockAfter("a", consensusEnd)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
}

func (suite *accountRepositorySuite) TestRetrieveTransferredTokensInBlockAfterDbConnectionError() {
	// given
	repo := NewAccountRepository(suite.InvalidDbClient)

	// when
	actual, err := repo.RetrieveTransferredTokensInBlockAfter(accountString, consensusEnd)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func sum(amounts []int64) int64 {
	var value int64
	for _, amount := range amounts {
		value += amount
	}

	return value
}
