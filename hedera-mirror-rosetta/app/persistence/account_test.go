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
	"github.com/jackc/pgtype"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

var (
	account                       = domain.MustDecodeEntityId(9000)
	account2                      = domain.MustDecodeEntityId(9001)
	treasury                      = domain.MustDecodeEntityId(9500)
	payer                         = domain.MustDecodeEntityId(2002)
	accountDeleteTimestamp  int64 = 280
	consensusTimestamp      int64 = 200
	firstSnapshotTimestamp  int64 = 100
	secondSnapshotTimestamp int64 = 300
	cryptoTransferAmounts         = []int64{150, -178}
	defaultContext                = context.Background()
	token1                        = &domain.Token{
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
	token4 = &domain.Token{
		TokenId:           domain.MustDecodeEntityId(1004),
		CreatedTimestamp:  16,
		Decimals:          0,
		InitialSupply:     0,
		ModifiedTimestamp: 16,
		Name:              "token4",
		SupplyType:        domain.TokenSupplyTypeFinite,
		Symbol:            "token4",
		TotalSupply:       500,
		TreasuryAccountId: treasury,
		Type:              domain.TokenTypeNonFungibleUnique,
	}
	token1TransferAmounts   = []int64{10, -5, 153}
	token2TransferAmounts   = []int64{20, -7, 157}
	firstAccountBalanceFile = &domain.AccountBalanceFile{
		ConsensusTimestamp: firstSnapshotTimestamp,
		Count:              100,
		LoadStart:          1600,
		LoadEnd:            1650,
		FileHash:           "filehash1",
		Name:               "account balance file 1",
		NodeAccountId:      domain.MustDecodeEntityId(3),
	}
	secondAccountBalanceFile = &domain.AccountBalanceFile{
		ConsensusTimestamp: secondSnapshotTimestamp,
		Count:              100,
		LoadStart:          3600,
		LoadEnd:            3650,
		FileHash:           "filehash2",
		Name:               "account balance file 2",
		NodeAccountId:      domain.MustDecodeEntityId(3),
	}
	initialTokenBalances = []domain.TokenBalance{
		{
			AccountId:          account,
			ConsensusTimestamp: firstSnapshotTimestamp,
			Balance:            112,
			TokenId:            token1.TokenId,
		},
		{
			AccountId:          account,
			ConsensusTimestamp: firstSnapshotTimestamp,
			Balance:            280,
			TokenId:            token2.TokenId,
		},
		{
			AccountId:          account,
			ConsensusTimestamp: firstSnapshotTimestamp,
			Balance:            2,
			TokenId:            token3.TokenId,
		},
	}
	initialAccountBalance = &domain.AccountBalance{
		ConsensusTimestamp: firstSnapshotTimestamp,
		Balance:            12345,
		AccountId:          account,
	}
	// the third crypto transfer is after consensusTimestamp, the last transfer happens when account is deleted, brings
	// account's hbar balance to 0
	cryptoTransfers = []domain.CryptoTransfer{
		{
			EntityId:           account,
			Amount:             cryptoTransferAmounts[0],
			ConsensusTimestamp: firstSnapshotTimestamp + 1,
			PayerAccountId:     payer,
		},
		{
			EntityId:           account,
			Amount:             cryptoTransferAmounts[1],
			ConsensusTimestamp: firstSnapshotTimestamp + 5,
			PayerAccountId:     payer,
		},
		{
			EntityId:           account,
			Amount:             155,
			ConsensusTimestamp: consensusTimestamp + 1,
			PayerAccountId:     payer,
		},
		{
			EntityId:           account,
			Amount:             -(initialAccountBalance.Balance + sum(cryptoTransferAmounts) + 155),
			ConsensusTimestamp: accountDeleteTimestamp,
			PayerAccountId:     account2,
		},
	}
	// crypto transfers at or before snapshot timestamp
	cryptoTransfersLTESnapshot = []domain.CryptoTransfer{
		{
			EntityId:           account,
			Amount:             110,
			ConsensusTimestamp: firstSnapshotTimestamp - 1,
			PayerAccountId:     payer,
		},
		{
			EntityId:           account,
			Amount:             170,
			ConsensusTimestamp: firstSnapshotTimestamp,
			PayerAccountId:     payer,
		},
	}
	// the last transfer of each token is after consensusTimestamp
	tokenTransfers = []domain.TokenTransfer{
		{
			AccountId:          account,
			Amount:             token1TransferAmounts[0],
			ConsensusTimestamp: firstSnapshotTimestamp + 2,
			PayerAccountId:     payer,
			TokenId:            token1.TokenId,
		},
		{
			AccountId:          account,
			Amount:             token1TransferAmounts[1],
			ConsensusTimestamp: firstSnapshotTimestamp + 4,
			PayerAccountId:     payer,
			TokenId:            token1.TokenId,
		},
		{
			AccountId:          account,
			Amount:             token1TransferAmounts[2],
			ConsensusTimestamp: consensusTimestamp + 1,
			PayerAccountId:     payer,
			TokenId:            token1.TokenId,
		},
		{
			AccountId:          account,
			Amount:             token2TransferAmounts[0],
			ConsensusTimestamp: firstSnapshotTimestamp + 5,
			PayerAccountId:     payer,
			TokenId:            token2.TokenId,
		},
		{
			AccountId:          account,
			Amount:             token2TransferAmounts[1],
			ConsensusTimestamp: firstSnapshotTimestamp + 8,
			PayerAccountId:     payer,
			TokenId:            token2.TokenId,
		},
		{
			AccountId:          account,
			Amount:             token2TransferAmounts[2],
			ConsensusTimestamp: consensusTimestamp + 1,
			PayerAccountId:     payer,
			TokenId:            token2.TokenId,
		},
	}
	// token transfers at or before the first snapshot timestamp
	tokenTransfersLTESnapshot = []*domain.TokenTransfer{
		{
			AccountId:          account,
			Amount:             17,
			ConsensusTimestamp: firstSnapshotTimestamp - 1,
			PayerAccountId:     payer,
			TokenId:            token1.TokenId,
		},
		{
			AccountId:          account,
			Amount:             -2,
			ConsensusTimestamp: firstSnapshotTimestamp,
			PayerAccountId:     payer,
			TokenId:            token1.TokenId,
		},
		{
			AccountId:          account,
			Amount:             25,
			ConsensusTimestamp: firstSnapshotTimestamp - 1,
			PayerAccountId:     payer,
			TokenId:            token2.TokenId,
		},
		{
			AccountId:          account,
			Amount:             -9,
			ConsensusTimestamp: firstSnapshotTimestamp,
			PayerAccountId:     payer,
			TokenId:            token2.TokenId,
		},
	}
	// the net change for account is 2 (received 3 [5, 4, 3], and sent 1 [5])
	nftTransfers = []domain.NftTransfer{
		{
			ConsensusTimestamp: firstSnapshotTimestamp + 1,
			PayerAccountId:     payer,
			ReceiverAccountId:  &account,
			SenderAccountId:    &treasury,
			SerialNumber:       3,
			TokenId:            token3.TokenId,
		},
		{
			ConsensusTimestamp: firstSnapshotTimestamp + 2,
			PayerAccountId:     payer,
			ReceiverAccountId:  &account,
			SenderAccountId:    &treasury,
			SerialNumber:       4,
			TokenId:            token3.TokenId,
		},
		{
			ConsensusTimestamp: firstSnapshotTimestamp + 3,
			PayerAccountId:     payer,
			ReceiverAccountId:  &account,
			SenderAccountId:    &treasury,
			SerialNumber:       5,
			TokenId:            token3.TokenId,
		},
		{
			ConsensusTimestamp: firstSnapshotTimestamp + 4,
			PayerAccountId:     payer,
			ReceiverAccountId:  &treasury,
			SenderAccountId:    &account,
			SerialNumber:       5,
			TokenId:            token3.TokenId,
		},
		// nft transfers for token4 between account2 and treasury which should not affect query for account
		{
			ConsensusTimestamp: firstSnapshotTimestamp + 5,
			PayerAccountId:     payer,
			ReceiverAccountId:  &treasury,
			SerialNumber:       1,
			TokenId:            token4.TokenId,
		},
		{
			ConsensusTimestamp: firstSnapshotTimestamp + 5,
			PayerAccountId:     payer,
			ReceiverAccountId:  &treasury,
			SerialNumber:       2,
			TokenId:            token4.TokenId,
		},
		{
			ConsensusTimestamp: firstSnapshotTimestamp + 5,
			PayerAccountId:     payer,
			ReceiverAccountId:  &treasury,
			SerialNumber:       3,
			TokenId:            token4.TokenId,
		},
		{
			ConsensusTimestamp: firstSnapshotTimestamp + 5,
			PayerAccountId:     payer,
			ReceiverAccountId:  &treasury,
			SerialNumber:       4,
			TokenId:            token4.TokenId,
		},
		{
			ConsensusTimestamp: firstSnapshotTimestamp + 5,
			PayerAccountId:     payer,
			ReceiverAccountId:  &treasury,
			SerialNumber:       5,
			TokenId:            token4.TokenId,
		},
		{
			ConsensusTimestamp: firstSnapshotTimestamp + 6,
			PayerAccountId:     payer,
			ReceiverAccountId:  &account2,
			SenderAccountId:    &treasury,
			SerialNumber:       1,
			TokenId:            token4.TokenId,
		},
		{
			ConsensusTimestamp: firstSnapshotTimestamp + 6,
			PayerAccountId:     payer,
			ReceiverAccountId:  &account2,
			SenderAccountId:    &treasury,
			SerialNumber:       2,
			TokenId:            token4.TokenId,
		},
		{
			ConsensusTimestamp: firstSnapshotTimestamp + 7,
			PayerAccountId:     payer,
			ReceiverAccountId:  &treasury,
			SenderAccountId:    &account2,
			SerialNumber:       1,
			TokenId:            token4.TokenId,
		},
	}
	// nft transfers at or before snapshot timestamp
	nftTransfersLTESnapshot = []domain.NftTransfer{
		{
			ConsensusTimestamp: firstSnapshotTimestamp - 2,
			PayerAccountId:     payer,
			ReceiverAccountId:  &account,
			SenderAccountId:    &treasury,
			SerialNumber:       1,
			TokenId:            token3.TokenId,
		},
		{
			ConsensusTimestamp: firstSnapshotTimestamp - 2,
			PayerAccountId:     payer,
			ReceiverAccountId:  &account,
			SenderAccountId:    &treasury,
			SerialNumber:       2,
			TokenId:            token3.TokenId,
		},
	}
	recordFile = &domain.RecordFile{
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
	db.CreateDbRecords(dbClient, firstAccountBalanceFile, secondAccountBalanceFile)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlock() {
	// given
	db.CreateDbRecords(dbClient, getAccountEntity(account, false, 1), token1, token2, token3)
	db.CreateDbRecords(dbClient, initialAccountBalance, initialTokenBalances)
	// transfers before or at the snapshot timestamp should not affect balance calculation
	db.CreateDbRecords(dbClient, cryptoTransfersLTESnapshot, tokenTransfersLTESnapshot, nftTransfersLTESnapshot)
	db.CreateDbRecords(dbClient, cryptoTransfers, tokenTransfers, nftTransfers)

	repo := NewAccountRepository(dbClient)

	hbarAmount := &types.HbarAmount{Value: initialAccountBalance.Balance + sum(cryptoTransferAmounts)}
	token1Amount := types.NewTokenAmount(*token1, initialTokenBalances[0].Balance+sum(token1TransferAmounts[:2]))
	token2Amount := types.NewTokenAmount(*token2, initialTokenBalances[1].Balance+sum(token2TransferAmounts[:2]))
	token3Amount := types.NewTokenAmount(*token3, initialTokenBalances[2].Balance+2).
		SetSerialNumbers([]int64{1, 2, 3, 4})

	expected := []types.Amount{hbarAmount, token1Amount, token2Amount, token3Amount}

	// when
	// account is deleted, however when querying its balance before when it's deleted, it should work as if the account
	// wasn't deleted
	actual, err := repo.RetrieveBalanceAtBlock(defaultContext, account.EncodedId, consensusTimestamp)

	// then
	assert.Nil(suite.T(), err)
	assert.ElementsMatch(suite.T(), expected, actual)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockForDeletedAccount() {
	// given
	expected := suite.setupTestForDeletedAccount()
	repo := NewAccountRepository(dbClient)

	// when
	// account is deleted before the second account balance file, so there is no balance info in the file. querying the
	// account balance for a timestamp after the second account balance file should then return the balance at the time
	// the account is deleted
	actual, err := repo.RetrieveBalanceAtBlock(defaultContext, account.EncodedId, secondSnapshotTimestamp+10)

	// then
	assert.Nil(suite.T(), err)
	assert.ElementsMatch(suite.T(), expected, actual)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockAtAccountDeletionTime() {
	// given
	expected := suite.setupTestForDeletedAccount()
	repo := NewAccountRepository(dbClient)

	// when
	actual, err := repo.RetrieveBalanceAtBlock(defaultContext, account.EncodedId, accountDeleteTimestamp)

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
	// account is not deleted
	db.CreateDbRecords(dbClient, getAccountEntity(account, false, 1), token1, token2, token3)
	db.CreateDbRecords(dbClient, cryptoTransfers, tokenTransfers, nftTransfers)

	repo := NewAccountRepository(dbClient)

	hbarAmount := &types.HbarAmount{Value: sum(cryptoTransferAmounts)}
	token1Amount := types.NewTokenAmount(*token1, sum(token1TransferAmounts[:2]))
	token2Amount := types.NewTokenAmount(*token2, sum(token2TransferAmounts[:2]))
	token3Amount := types.NewTokenAmount(*token3, 2).SetSerialNumbers([]int64{3, 4})
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

func (suite *accountRepositorySuite) TestRetrieveEverOwnedTokensByBlock() {
	// given
	db.CreateDbRecords(dbClient, recordFile, token1, token2, token3)
	suite.createTokenAssociations()
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
	actual, err := repo.RetrieveEverOwnedTokensByBlock(defaultContext, account.EncodedId, recordFile.ConsensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), expected, actual)
}

func (suite *accountRepositorySuite) TestRetrieveEverOwnedTokensByBlockNoTokenEntity() {
	// given
	db.CreateDbRecords(dbClient, recordFile)
	suite.createTokenAssociations()
	repo := NewAccountRepository(dbClient)

	// when
	actual, err := repo.RetrieveEverOwnedTokensByBlock(defaultContext, account.EncodedId, recordFile.ConsensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assert.Empty(suite.T(), actual)
}

func (suite *accountRepositorySuite) TestRetrieveEverOwnedTokensByBlockDbConnectionError() {
	// given
	repo := NewAccountRepository(invalidDbClient)

	// when
	actual, err := repo.RetrieveEverOwnedTokensByBlock(defaultContext, account.EncodedId, consensusEnd)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *accountRepositorySuite) createTokenAssociations() {
	currentBlockStart := recordFile.ConsensusStart
	currentBlockEnd := recordFile.ConsensusEnd
	nextBlockStart := currentBlockEnd + 1
	db.CreateDbRecords(
		dbClient,
		// account1's token associations
		getTokenAccount(account, true, currentBlockStart-1, currentBlockStart-1, token1.TokenId),
		getTokenAccount(account, false, currentBlockStart-1, currentBlockStart, token1.TokenId),
		getTokenAccount(account, true, currentBlockEnd, currentBlockEnd, token2.TokenId),
		getTokenAccount(account, false, currentBlockEnd, nextBlockStart, token2.TokenId),
		getTokenAccount(account, true, nextBlockStart+1, nextBlockStart+1, token3.TokenId),
		// account2's token associations
		getTokenAccount(account2, true, currentBlockEnd-6, currentBlockEnd-6, token3.TokenId),
		getTokenAccount(account2, true, currentBlockEnd-5, currentBlockEnd-5, token4.TokenId),
	)
}

func (suite *accountRepositorySuite) setupTestForDeletedAccount() []types.Amount {
	db.CreateDbRecords(dbClient, getAccountEntity(account, true, accountDeleteTimestamp), token1, token2, token3)
	db.CreateDbRecords(dbClient, initialAccountBalance, initialTokenBalances)
	// transfers before or at the snapshot timestamp should not affect balance calculation
	db.CreateDbRecords(dbClient, cryptoTransfersLTESnapshot, tokenTransfersLTESnapshot, nftTransfersLTESnapshot)
	db.CreateDbRecords(dbClient, cryptoTransfers, tokenTransfers, nftTransfers)

	token1Amount := types.NewTokenAmount(*token1, initialTokenBalances[0].Balance+sum(token1TransferAmounts))
	token2Amount := types.NewTokenAmount(*token2, initialTokenBalances[1].Balance+sum(token2TransferAmounts))
	token3Amount := types.NewTokenAmount(*token3, initialTokenBalances[2].Balance+2).
		SetSerialNumbers([]int64{1, 2, 3, 4})

	return []types.Amount{&types.HbarAmount{}, token1Amount, token2Amount, token3Amount}
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

func getAccountEntity(id domain.EntityId, deleted bool, modifiedTimestamp int64) *domain.Entity {
	return &domain.Entity{
		Deleted: &deleted,
		Id:      id,
		Memo:    "test account",
		Num:     id.EntityNum,
		Realm:   id.RealmNum,
		Shard:   id.ShardNum,
		Type:    "ACCOUNT",
		TimestampRange: pgtype.Int8range{
			Lower:     pgtype.Int8{Int: modifiedTimestamp, Status: pgtype.Present},
			Upper:     pgtype.Int8{Status: pgtype.Null},
			LowerType: pgtype.Inclusive,
			UpperType: pgtype.Unbounded,
			Status:    pgtype.Present,
		},
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
