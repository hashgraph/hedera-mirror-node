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
	"context"
	"testing"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

var (
	account                     = domain.MustDecodeEntityId(9000)
	account2                    = domain.MustDecodeEntityId(9001)
	treasury                    = domain.MustDecodeEntityId(9500)
	consensusTimestamp    int64 = 200
	snapshotTimestamp     int64 = 100
	cryptoTransferAmounts       = []int64{150, -178}
	defaultContext              = context.Background()
	token1                      = &domain.Token{
		TokenId:           domain.MustDecodeEntityId(1001),
		CreatedTimestamp:  10,
		Decimals:          8,
		InitialSupply:     100000,
		ModifiedTimestamp: 10,
		Name:              "token1",
		SupplyType:        domain.TokenSupplyTypeInfinite,
		Symbol:            "token1",
		TotalSupply:       200000,
		TreasuryAccountId: treasury,
		Type:              domain.TokenTypeFungibleCommon,
	}
	token2 = &domain.Token{
		TokenId:           domain.MustDecodeEntityId(1002),
		CreatedTimestamp:  12,
		Decimals:          9,
		InitialSupply:     200000,
		ModifiedTimestamp: 12,
		Name:              "token2",
		SupplyType:        domain.TokenSupplyTypeInfinite,
		Symbol:            "token2",
		TotalSupply:       800000,
		TreasuryAccountId: treasury,
		Type:              domain.TokenTypeFungibleCommon,
	}
	token3 = &domain.Token{
		TokenId:           domain.MustDecodeEntityId(1003),
		CreatedTimestamp:  15,
		Decimals:          0,
		InitialSupply:     0,
		ModifiedTimestamp: 15,
		Name:              "token3",
		SupplyType:        domain.TokenSupplyTypeFinite,
		Symbol:            "token3",
		TotalSupply:       500,
		TreasuryAccountId: treasury,
		Type:              domain.TokenTypeNonFungibleUnique,
	}
	token1TransferAmounts      = []int64{10, -5}
	token2TransferAmounts      = []int64{20, -7}
	snapshotAccountBalanceFile = &domain.AccountBalanceFile{
		ConsensusTimestamp: snapshotTimestamp,
		Count:              100,
		LoadStart:          1600,
		LoadEnd:            1650,
		FileHash:           "filehash",
		Name:               "account balance file 1",
		NodeAccountId:      domain.MustDecodeEntityId(3),
	}
	initialTokenBalances = []domain.TokenBalance{
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
		{
			AccountId:          account,
			ConsensusTimestamp: snapshotTimestamp,
			Balance:            2,
			TokenId:            token3.TokenId,
		},
	}
	initialAccountBalance = &domain.AccountBalance{
		ConsensusTimestamp: snapshotTimestamp,
		Balance:            12345,
		AccountId:          account,
	}
	// the last crypto transfer is after consensusTimestamp
	cryptoTransfers = []domain.CryptoTransfer{
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
			ConsensusTimestamp: consensusTimestamp + 1,
		},
	}
	// crypto transfers at or before snapshot timestamp
	cryptoTransfersLTESnapshot = []domain.CryptoTransfer{
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
	// the last transfer of each token is after consensusTimestamp
	tokenTransfers = []domain.TokenTransfer{
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
			ConsensusTimestamp: consensusTimestamp + 1,
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
			ConsensusTimestamp: consensusTimestamp + 1,
			TokenId:            token2.TokenId,
		},
	}
	// token transfers at or before snapshot timestamp
	tokenTransfersLTESnapshot = []*domain.TokenTransfer{
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
	// the net change for account is 2 (received 3, and sent 1)
	nftTransfers = []domain.NftTransfer{
		{
			ConsensusTimestamp: snapshotTimestamp + 1,
			ReceiverAccountId:  &account,
			SenderAccountId:    &treasury,
			SerialNumber:       3,
			TokenId:            token3.TokenId,
		},
		{
			ConsensusTimestamp: snapshotTimestamp + 2,
			ReceiverAccountId:  &account,
			SenderAccountId:    &treasury,
			SerialNumber:       4,
			TokenId:            token3.TokenId,
		},
		{
			ConsensusTimestamp: snapshotTimestamp + 3,
			ReceiverAccountId:  &account,
			SenderAccountId:    &treasury,
			SerialNumber:       5,
			TokenId:            token3.TokenId,
		},
		{
			ConsensusTimestamp: snapshotTimestamp + 4,
			ReceiverAccountId:  &treasury,
			SenderAccountId:    &account,
			SerialNumber:       5,
			TokenId:            token3.TokenId,
		},
	}
	// nft transfers at or before snapshot timestamp
	nftTransfersLTESnapshot = []domain.NftTransfer{
		{
			ConsensusTimestamp: snapshotTimestamp - 2,
			ReceiverAccountId:  &account,
			SenderAccountId:    &treasury,
			SerialNumber:       1,
			TokenId:            token3.TokenId,
		},
		{
			ConsensusTimestamp: snapshotTimestamp - 2,
			ReceiverAccountId:  &account,
			SenderAccountId:    &treasury,
			SerialNumber:       2,
			TokenId:            token3.TokenId,
		},
	}
	nextRecordFile = &domain.RecordFile{
		ConsensusStart: consensusTimestamp + 1,
		ConsensusEnd:   consensusTimestamp + 10,
		Count:          5,
		FileHash:       "filehash",
		Hash:           "hash",
		Index:          5,
		Name:           "next_record_file.rcd",
		NodeAccountID:  domain.MustDecodeEntityId(3),
		PrevHash:       "prevhash",
		Version:        5,
	}
)

// run the suite
func TestAccountRepositorySuite(t *testing.T) {
	suite.Run(t, new(accountRepositorySuite))
}

type accountRepositorySuite struct {
	integrationTest
	suite.Suite
}

func (suite *accountRepositorySuite) SetupTest() {
	suite.integrationTest.SetupTest()
	db.CreateDbRecords(dbClient, snapshotAccountBalanceFile)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlock() {
	// given
	db.CreateDbRecords(dbClient, token1, token2, token3)
	db.CreateDbRecords(dbClient, initialAccountBalance, initialTokenBalances)
	// transfers before or at the snapshot timestamp should not affect balance calculation
	db.CreateDbRecords(dbClient, cryptoTransfersLTESnapshot, tokenTransfersLTESnapshot, nftTransfersLTESnapshot)
	db.CreateDbRecords(dbClient, cryptoTransfers, tokenTransfers, nftTransfers)

	repo := NewAccountRepository(dbClient)

	hbarAmount := &types.HbarAmount{Value: initialAccountBalance.Balance + sum(cryptoTransferAmounts)}
	token1Amount := types.NewTokenAmount(*token1, initialTokenBalances[0].Balance+sum(token1TransferAmounts))
	token2Amount := types.NewTokenAmount(*token2, initialTokenBalances[1].Balance+sum(token2TransferAmounts))
	token3Amount := types.NewTokenAmount(*token3, initialTokenBalances[2].Balance+2)

	expected := []types.Amount{hbarAmount, token1Amount, token2Amount, token3Amount}

	// when
	actual, err := repo.RetrieveBalanceAtBlock(defaultContext, account.EncodedId, consensusTimestamp)

	// then
	assert.Nil(suite.T(), err)
	assert.ElementsMatch(suite.T(), expected, actual)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockNoTokenEntity() {
	// given
	db.CreateDbRecords(dbClient, initialAccountBalance, initialTokenBalances)
	// transfers before or at the snapshot timestamp should not affect balance calculation
	db.CreateDbRecords(dbClient, cryptoTransfersLTESnapshot, tokenTransfersLTESnapshot)
	db.CreateDbRecords(dbClient, cryptoTransfers, tokenTransfers)

	repo := NewAccountRepository(dbClient)

	// no token entities, so only hbar balance
	hbarAmount := &types.HbarAmount{Value: initialAccountBalance.Balance + sum(cryptoTransferAmounts)}
	expected := []types.Amount{hbarAmount}

	// when
	actual, err := repo.RetrieveBalanceAtBlock(defaultContext, account.EncodedId, consensusTimestamp)

	// then
	assert.Nil(suite.T(), err)
	assert.ElementsMatch(suite.T(), expected, actual)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockNoInitialBalance() {
	// given
	db.CreateDbRecords(dbClient, token1, token2, token3)
	db.CreateDbRecords(dbClient, cryptoTransfers, tokenTransfers, nftTransfers)

	repo := NewAccountRepository(dbClient)

	hbarAmount := &types.HbarAmount{Value: sum(cryptoTransferAmounts)}
	token1Amount := types.NewTokenAmount(*token1, sum(token1TransferAmounts))
	token2Amount := types.NewTokenAmount(*token2, sum(token2TransferAmounts))
	token3Amount := types.NewTokenAmount(*token3, 2)
	expected := []types.Amount{hbarAmount, token1Amount, token2Amount, token3Amount}

	// when
	actual, err := repo.RetrieveBalanceAtBlock(defaultContext, account.EncodedId, consensusTimestamp)

	// then
	assert.Nil(suite.T(), err)
	assert.ElementsMatch(suite.T(), expected, actual)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockNoAccountBalanceFile() {
	// given
	db.ExecSql(dbClient, truncateAccountBalanceFileSql)
	repo := NewAccountRepository(dbClient)

	// when
	actual, err := repo.RetrieveBalanceAtBlock(defaultContext, account.EncodedId, consensusTimestamp)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockDbConnectionError() {
	// given
	repo := NewAccountRepository(invalidDbClient)

	// when
	actual, err := repo.RetrieveBalanceAtBlock(defaultContext, account.EncodedId, consensusTimestamp)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *accountRepositorySuite) TestRetrieveEverOwnedTokensByBlockAfter() {
	// given
	currentBlockEnd := nextRecordFile.ConsensusStart - 1
	nextBlockStart := nextRecordFile.ConsensusStart
	nextBlockEnd := nextRecordFile.ConsensusEnd
	db.CreateDbRecords(dbClient, nextRecordFile, token1, token2, token3)
	db.CreateDbRecords(
		dbClient,
		getTokenAccount(account, true, currentBlockEnd-5, currentBlockEnd-5, token1.TokenId),
		getTokenAccount(account, false, currentBlockEnd-5, currentBlockEnd-4, token1.TokenId),
		getTokenAccount(account, true, nextBlockStart+1, nextBlockStart+1, token2.TokenId),
		getTokenAccount(account, true, nextBlockEnd+1, nextBlockEnd+1, token3.TokenId),
		getTokenAccount(account2, true, currentBlockEnd-6, currentBlockEnd-6, token3.TokenId),
	)
	expected := []domain.Token{
		{
			Decimals: token1.Decimals,
			TokenId:  token1.TokenId,
			Type:     domain.TokenTypeFungibleCommon,
		},
		{
			Decimals: token2.Decimals,
			TokenId:  token2.TokenId,
			Type:     domain.TokenTypeFungibleCommon,
		},
	}
	repo := NewAccountRepository(dbClient)

	// when
	actual, err := repo.RetrieveEverOwnedTokensByBlockAfter(defaultContext, account.EncodedId, currentBlockEnd)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expected, actual)
}

func (suite *accountRepositorySuite) TestRetrieveEverOwnedTokensByBlockAfterNoTokenEntity() {
	// given
	currentBlockEnd := nextRecordFile.ConsensusStart - 1
	nextBlockStart := nextRecordFile.ConsensusStart
	nextBlockEnd := nextRecordFile.ConsensusEnd
	db.CreateDbRecords(dbClient, nextRecordFile)
	db.CreateDbRecords(
		dbClient,
		getTokenAccount(account, true, currentBlockEnd-5, currentBlockEnd-5, token1.TokenId),
		getTokenAccount(account, false, currentBlockEnd-5, currentBlockEnd-4, token1.TokenId),
		getTokenAccount(account, true, nextBlockStart+1, nextBlockStart+1, token2.TokenId),
		getTokenAccount(account, true, nextBlockEnd+1, nextBlockEnd+1, token3.TokenId),
		getTokenAccount(account2, true, currentBlockEnd-6, currentBlockEnd-6, token3.TokenId),
	)
	repo := NewAccountRepository(dbClient)

	// when
	actual, err := repo.RetrieveEverOwnedTokensByBlockAfter(defaultContext, account.EncodedId, currentBlockEnd)

	// then
	assert.Nil(suite.T(), err)
	assert.Empty(suite.T(), actual)
}

func (suite *accountRepositorySuite) TestRetrieveEverOwnedTokensByBlockAfterDbConnectionError() {
	// given
	repo := NewAccountRepository(invalidDbClient)

	// when
	actual, err := repo.RetrieveEverOwnedTokensByBlockAfter(defaultContext, account.EncodedId, consensusEnd)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func TestGetUpdatedTokenAmounts(t *testing.T) {
	token3EntityId := domain.EntityId{EncodedId: 2007, EntityNum: 2007}
	tests := []struct {
		name           string
		tokenAmountMap map[int64]*types.TokenAmount
		tokenValues    []*types.TokenAmount
		expected       []types.Amount
	}{
		{
			"empty tokenAmountMap and tokenValues",
			map[int64]*types.TokenAmount{},
			[]*types.TokenAmount{},
			[]types.Amount{},
		},
		{
			"empty tokenValues",
			map[int64]*types.TokenAmount{
				token1.TokenId.EncodedId: {Decimals: token1.Decimals, TokenId: token1.TokenId, Value: 200},
			},
			[]*types.TokenAmount{},
			[]types.Amount{
				&types.TokenAmount{Decimals: token1.Decimals, TokenId: token1.TokenId, Value: 200},
			},
		},
		{
			"empty tokenAmountMap",
			map[int64]*types.TokenAmount{},
			[]*types.TokenAmount{
				{Decimals: token1.Decimals, TokenId: token1.TokenId, Value: 200},
			},
			[]types.Amount{
				&types.TokenAmount{Decimals: token1.Decimals, TokenId: token1.TokenId, Value: 200},
			},
		},
		{
			"non-empty tokenAmountMap and tokenValues",
			map[int64]*types.TokenAmount{
				token1.TokenId.EncodedId: {Decimals: token1.Decimals, TokenId: token1.TokenId, Value: 200},
				token2.TokenId.EncodedId: {Decimals: token2.Decimals, TokenId: token2.TokenId, Value: 220},
			},
			[]*types.TokenAmount{
				{Decimals: token1.Decimals, TokenId: token1.TokenId, Value: -20},
				{Decimals: token2.Decimals, TokenId: token2.TokenId, Value: 30},
				{Decimals: 12, TokenId: token3EntityId, Value: 70},
			},
			[]types.Amount{
				&types.TokenAmount{Decimals: token1.Decimals, TokenId: token1.TokenId, Value: 180},
				&types.TokenAmount{Decimals: token2.Decimals, TokenId: token2.TokenId, Value: 250},
				&types.TokenAmount{Decimals: 12, TokenId: token3EntityId, Value: 70},
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual := getUpdatedTokenAmounts(tt.tokenAmountMap, tt.tokenValues)
			assert.ElementsMatch(t, tt.expected, actual)
		})
	}
}

func getTokenAccount(
	accountId domain.EntityId,
	associated bool,
	createdTimestamp int64,
	modifiedTimestamp int64,
	tokenId domain.EntityId,
) *domain.TokenAccount {
	return &domain.TokenAccount{
		AccountId:         accountId,
		Associated:        associated,
		CreatedTimestamp:  createdTimestamp,
		ModifiedTimestamp: modifiedTimestamp,
		TokenId:           tokenId,
	}
}

func sum(amounts []int64) int64 {
	var value int64
	for _, amount := range amounts {
		value += amount
	}

	return value
}
