/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
	tdomain "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/domain"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

const (
	account1 = int64(9000) + iota
	treasury
	account2
)

const (
	encodedTokenId1 = int64(1000) + iota
	encodedTokenId2
	encodedTokenId3
	encodedTokenId4
	encodedTokenId5
	encodedTokenId6
)

const (
	accountDeleteTimestamp   int64 = 280
	consensusTimestamp       int64 = 200
	dissociateTimestamp            = consensusTimestamp + 1
	account1CreatedTimestamp int64 = 50
	account2DeletedTimestamp       = account1CreatedTimestamp - 1
	account2CreatedTimestamp       = account2DeletedTimestamp - 9
	firstSnapshotTimestamp   int64 = 100
	initialAccountBalance    int64 = 12345
	secondSnapshotTimestamp        = consensusTimestamp - 20
	thirdSnapshotTimestamp   int64 = 400
)

var (
	cryptoTransferAmounts = []int64{150, -178}
	defaultContext        = context.Background()
	token1TransferAmounts = []int64{10, -5, 153}
	token2TransferAmounts = []int64{20, -7}
	token3ReceivedSerials = []int64{3, 4, 5}
	token3SentSerials     = []int64{5}

	token1 domain.Token
	token2 domain.Token
	token3 domain.Token
	token4 domain.Token
)

// run the suite
func TestAccountRepositorySuite(t *testing.T) {
	suite.Run(t, new(accountRepositorySuite))
}

type accountRepositorySuite struct {
	integrationTest
	suite.Suite
	accountId       types.AccountId
	accountIdString string
}

func (suite *accountRepositorySuite) SetupSuite() {
	suite.accountId = types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(account1))
	suite.accountIdString = suite.accountId.String()
}

func (suite *accountRepositorySuite) SetupTest() {
	suite.setupTest(nil)
}

func (suite *accountRepositorySuite) setupTest(accountNetworkAlias []byte) {
	suite.integrationTest.SetupTest()
	associatedTokenAccounts := make([]domain.TokenAccount, 0)

	accountBuilder := tdomain.NewEntityBuilder(dbClient, account1, account1CreatedTimestamp, domain.EntityTypeAccount)
	if accountNetworkAlias != nil {
		accountBuilder.Alias(accountNetworkAlias)
	}
	accountBuilder.Persist()

	// persist tokens and tokenAccounts
	token1 = tdomain.NewTokenBuilder(dbClient, encodedTokenId1, firstSnapshotTimestamp+1, treasury).Persist()
	ta := tdomain.NewTokenAccountBuilder(dbClient, account1, encodedTokenId1, token1.CreatedTimestamp+2).Persist()
	associatedTokenAccounts = append(associatedTokenAccounts, ta)

	token2 = tdomain.NewTokenBuilder(dbClient, encodedTokenId2, firstSnapshotTimestamp+2, treasury).Persist()
	ta2 := tdomain.NewTokenAccountBuilder(dbClient, account1, encodedTokenId2, token2.CreatedTimestamp+2).Persist()
	tdomain.NewTokenAccountBuilderFromExisting(dbClient, ta2).
		Associated(false, dissociateTimestamp).
		Persist()

	token3 = tdomain.NewTokenBuilder(dbClient, encodedTokenId3, firstSnapshotTimestamp+3, treasury).
		Type(domain.TokenTypeNonFungibleUnique).
		Persist()
	ta3 := tdomain.NewTokenAccountBuilder(dbClient, account1, encodedTokenId3, token3.CreatedTimestamp+2).Persist()
	tdomain.NewTokenAccountBuilderFromExisting(dbClient, ta3).
		Associated(false, dissociateTimestamp).
		Persist()

	// account1's token4 balance is always 0
	token4 = tdomain.NewTokenBuilder(dbClient, encodedTokenId4, firstSnapshotTimestamp+4, treasury).
		Type(domain.TokenTypeNonFungibleUnique).
		Persist()
	ta = tdomain.NewTokenAccountBuilder(dbClient, account1, encodedTokenId4, token4.CreatedTimestamp+2).Persist()
	associatedTokenAccounts = append(associatedTokenAccounts, ta)

	// token5 is created before firstSnapshotTimestamp
	tdomain.NewTokenBuilder(dbClient, encodedTokenId5, firstSnapshotTimestamp-1, treasury).Persist()
	ta = tdomain.NewTokenAccountBuilder(dbClient, account1, encodedTokenId5, firstSnapshotTimestamp+2).Persist()
	associatedTokenAccounts = append(associatedTokenAccounts, ta)

	// token6 is created at firstSnapshotTimestamp
	tdomain.NewTokenBuilder(dbClient, encodedTokenId6, firstSnapshotTimestamp, treasury).
		Type(domain.TokenTypeNonFungibleUnique).
		Persist()
	ta = tdomain.NewTokenAccountBuilder(dbClient, account1, encodedTokenId6, firstSnapshotTimestamp+3).Persist()
	associatedTokenAccounts = append(associatedTokenAccounts, ta)

	// account balance files
	tdomain.NewAccountBalanceFileBuilder(dbClient, firstSnapshotTimestamp).
		AddAccountBalance(account1, initialAccountBalance).
		AddTokenBalance(account1, encodedTokenId5, 12).
		AddTokenBalance(account1, encodedTokenId6, 5).
		Persist()
	tdomain.NewAccountBalanceFileBuilder(dbClient, thirdSnapshotTimestamp).Persist()

	// crypto transfers happened at <= first snapshot timestamp
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(110).
		EntityId(account1).
		Timestamp(firstSnapshotTimestamp - 1).
		Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(170).
		EntityId(account1).
		Timestamp(firstSnapshotTimestamp).
		Persist()

	// crypto transfers happened at > first snapshot timestamp
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(cryptoTransferAmounts[0]).
		EntityId(account1).
		Timestamp(firstSnapshotTimestamp + 1).
		Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(cryptoTransferAmounts[1]).
		EntityId(account1).
		Timestamp(firstSnapshotTimestamp + 5).
		Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(155).
		EntityId(account1).
		Timestamp(dissociateTimestamp + 1).
		Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(-(initialAccountBalance + sum(cryptoTransferAmounts) + 155)).
		EntityId(account1).
		Timestamp(accountDeleteTimestamp).
		Persist()

	// token transfers happened at <= first snapshot timestamp
	tdomain.NewTokenTransferBuilder(dbClient).
		Amount(17).
		AccountId(account1).
		Timestamp(firstSnapshotTimestamp - 1).
		TokenId(encodedTokenId5).
		Persist()
	tdomain.NewTokenTransferBuilder(dbClient).
		Amount(-2).
		AccountId(account1).
		Timestamp(firstSnapshotTimestamp).
		TokenId(encodedTokenId5).
		Persist()

	// token transfers happened at > first snapshot timestamp
	// token1 transfers, the last transfer happened at after dissociate timestamp
	transferTimestamps := []int64{token1.CreatedTimestamp + 2, token1.CreatedTimestamp + 3, dissociateTimestamp + 1}
	for i, amount := range token1TransferAmounts {
		tdomain.NewTokenTransferBuilder(dbClient).
			AccountId(account1).
			Amount(amount).
			Timestamp(transferTimestamps[i]).
			TokenId(encodedTokenId1).
			Persist()
	}
	// token2 transfers
	transferTimestamps = []int64{token2.CreatedTimestamp + 2, token2.CreatedTimestamp + 3}
	for i, amount := range token2TransferAmounts {
		tdomain.NewTokenTransferBuilder(dbClient).
			AccountId(account1).
			Amount(amount).
			Timestamp(transferTimestamps[i]).
			TokenId(encodedTokenId2).
			Persist()
	}
	// token5 transfer will not affect balance since token5 is created before first snapshot
	tdomain.NewTokenTransferBuilder(dbClient).AccountId(account1).
		Amount(100).
		Timestamp(firstSnapshotTimestamp + 10).
		TokenId(encodedTokenId5).
		Persist()

	// nft transfers happened at <= first snapshot timestamp
	tdomain.NewNftTransferBuilder(dbClient).
		SenderAccountId(treasury).
		ReceiverAccountId(account1).
		SerialNumber(1).
		Timestamp(firstSnapshotTimestamp - 1).
		TokenId(encodedTokenId6).
		Persist()
	tdomain.NewNftTransferBuilder(dbClient).
		SenderAccountId(treasury).
		ReceiverAccountId(account1).
		SerialNumber(2).
		Timestamp(firstSnapshotTimestamp).
		TokenId(encodedTokenId6).
		Persist()

	// nft transfers happened at > first snapshot timestamp
	// the net change for account is 2 (received 3 [3, 4, 5], and sent 1 [5])
	transferTimestamp := token3.CreatedTimestamp + 3
	for _, serial := range token3ReceivedSerials {
		tdomain.NewNftTransferBuilder(dbClient).
			ReceiverAccountId(account1).
			SenderAccountId(treasury).
			SerialNumber(serial).
			Timestamp(transferTimestamp).
			TokenId(encodedTokenId3).
			Persist()
		transferTimestamp++
	}
	// add a self transfer
	tdomain.NewNftTransferBuilder(dbClient).
		ReceiverAccountId(account1).
		SenderAccountId(account1).
		SerialNumber(token3ReceivedSerials[0]).
		Timestamp(transferTimestamp).
		TokenId(encodedTokenId3).
		Persist()
	transferTimestamp++
	// transfer serial 5 from account1 to treasury
	for _, serial := range token3SentSerials {
		tdomain.NewNftTransferBuilder(dbClient).
			ReceiverAccountId(treasury).
			SenderAccountId(account1).
			SerialNumber(serial).
			Timestamp(transferTimestamp).
			TokenId(encodedTokenId3).
			Persist()
		transferTimestamp++
	}
	// token6 transfer will not affect balance since token6 is created before first snapshot
	tdomain.NewNftTransferBuilder(dbClient).
		ReceiverAccountId(account1).
		SenderAccountId(treasury).
		SerialNumber(1).
		Timestamp(firstSnapshotTimestamp + 10).
		TokenId(encodedTokenId6).
		Persist()

	// dissociate other tokens before account delete timestamp
	for _, ta := range associatedTokenAccounts {
		tdomain.NewTokenAccountBuilderFromExisting(dbClient, ta).
			Associated(false, accountDeleteTimestamp-1).
			Persist()
	}
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlock() {
	// given
	// tokens created at or before first account balance snapshot will not show up in account balance response
	// transfers before or at the snapshot timestamp should not affect balance calculation
	accountId := suite.accountId
	repo := NewAccountRepository(dbClient)

	hbarAmount := &types.HbarAmount{Value: initialAccountBalance + sum(cryptoTransferAmounts)}
	token1Amount := types.NewTokenAmount(token1, sum(token1TransferAmounts[:2]))
	token2Amount := types.NewTokenAmount(token2, sum(token2TransferAmounts[:2]))
	token3Amount := types.NewTokenAmount(token3, 2)
	token4Amount := types.NewTokenAmount(token4, 0)
	expectedAmounts := types.AmountSlice{hbarAmount, token1Amount, token2Amount, token3Amount, token4Amount}

	// when
	// query
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(defaultContext, accountId, consensusTimestamp)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.ElementsMatch(suite.T(), expectedAmounts, actualAmounts)

	// when
	// query at dissociateTimestamp, balances for token2 and token3 should be 0
	actualAmounts, accountIdString, err = repo.RetrieveBalanceAtBlock(defaultContext, accountId, dissociateTimestamp)
	token2Amount = types.NewTokenAmount(token2, 0)
	token3Amount = types.NewTokenAmount(token3, 0)
	expectedAmounts = types.AmountSlice{hbarAmount, token1Amount, token2Amount, token3Amount, token4Amount}

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.ElementsMatch(suite.T(), expectedAmounts, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockAfterSecondSnapshot() {
	// given
	// remove any transfers in db. with the balance info in the second snapshot, this test verifies the account balance
	// is computed with additional transfers applied on top of the snapshot
	accountId := suite.accountId
	db.ExecSql(dbClient, truncateCryptoTransferFileSql, truncateNftTransferSql, truncateTokenTransferSql)
	tdomain.NewAccountBalanceFileBuilder(dbClient, secondSnapshotTimestamp).
		AddAccountBalance(account1, initialAccountBalance+sum(cryptoTransferAmounts)).
		AddTokenBalance(account1, encodedTokenId1, sum(token1TransferAmounts[:2])).
		AddTokenBalance(account1, encodedTokenId2, sum(token2TransferAmounts[:2])).
		AddTokenBalance(account1, encodedTokenId3, 2).
		AddTokenBalance(account1, encodedTokenId4, 0).
		Persist()
	// extra transfers
	tdomain.NewTokenTransferBuilder(dbClient).
		AccountId(account1).
		Amount(10).
		Timestamp(secondSnapshotTimestamp + 2).
		TokenId(encodedTokenId1).
		Persist()
	tdomain.NewTokenTransferBuilder(dbClient).
		AccountId(account1).
		Amount(12).
		Timestamp(secondSnapshotTimestamp + 3).
		TokenId(encodedTokenId2).
		Persist()
	tdomain.NewNftTransferBuilder(dbClient).
		ReceiverAccountId(account1).
		SenderAccountId(treasury).
		SerialNumber(6).
		Timestamp(secondSnapshotTimestamp + 4).
		TokenId(encodedTokenId3).
		Persist()
	// add a self transfer
	tdomain.NewNftTransferBuilder(dbClient).
		ReceiverAccountId(account1).
		SenderAccountId(account1).
		SerialNumber(6).
		Timestamp(secondSnapshotTimestamp + 5).
		TokenId(encodedTokenId3).
		Persist()
	hbarAmount := &types.HbarAmount{Value: initialAccountBalance + sum(cryptoTransferAmounts)}
	token1Amount := types.NewTokenAmount(token1, sum(token1TransferAmounts[:2])+10)
	token2Amount := types.NewTokenAmount(token2, sum(token2TransferAmounts[:2])+12)
	token3Amount := types.NewTokenAmount(token3, 3)
	token4Amount := types.NewTokenAmount(token4, 0)
	expectedAmount := types.AmountSlice{hbarAmount, token1Amount, token2Amount, token3Amount, token4Amount}
	repo := NewAccountRepository(dbClient)

	// when
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		accountId,
		secondSnapshotTimestamp+6,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.ElementsMatch(suite.T(), expectedAmount, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockForDeletedAccount() {
	// given
	accountId := suite.accountId
	tdomain.NewEntityBuilder(dbClient, account1, 1, domain.EntityTypeAccount).
		Deleted(true).
		ModifiedTimestamp(accountDeleteTimestamp).
		Persist()
	expectedAmounts := types.AmountSlice{
		&types.HbarAmount{},
		types.NewTokenAmount(token1, 0),
		types.NewTokenAmount(token2, 0),
		types.NewTokenAmount(token3, 0),
		types.NewTokenAmount(token4, 0),
	}
	repo := NewAccountRepository(dbClient)

	// when
	// account is deleted before the third account balance file, so there is no balance info in the file. querying the
	// account balance for a timestamp after the third account balance file should then return the balance at the time
	// the account is deleted
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		accountId,
		thirdSnapshotTimestamp+10,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.ElementsMatch(suite.T(), expectedAmounts, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockAtAccountDeletionTime() {
	// given
	accountId := suite.accountId
	tdomain.NewEntityBuilder(dbClient, account1, 1, domain.EntityTypeAccount).
		Deleted(true).
		ModifiedTimestamp(accountDeleteTimestamp).
		Persist()
	expectedAmounts := types.AmountSlice{
		&types.HbarAmount{},
		types.NewTokenAmount(token1, 0),
		types.NewTokenAmount(token2, 0),
		types.NewTokenAmount(token3, 0),
		types.NewTokenAmount(token4, 0),
	}
	repo := NewAccountRepository(dbClient)

	// when
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		accountId,
		accountDeleteTimestamp,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.ElementsMatch(suite.T(), expectedAmounts, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockNoAccountEntity() {
	// given
	accountId := suite.accountId
	db.ExecSql(dbClient, truncateEntitySql)
	hbarAmount := &types.HbarAmount{Value: initialAccountBalance + sum(cryptoTransferAmounts)}
	token1Amount := types.NewTokenAmount(token1, sum(token1TransferAmounts[:2]))
	token2Amount := types.NewTokenAmount(token2, sum(token2TransferAmounts[:2]))
	token3Amount := types.NewTokenAmount(token3, 2)
	token4Amount := types.NewTokenAmount(token4, 0)
	expectedAmounts := types.AmountSlice{hbarAmount, token1Amount, token2Amount, token3Amount, token4Amount}
	repo := NewAccountRepository(dbClient)

	// when
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		accountId,
		consensusTimestamp,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Empty(suite.T(), accountIdString)
	assert.ElementsMatch(suite.T(), expectedAmounts, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockNoTokenEntity() {
	// given
	accountId := suite.accountId
	db.ExecSql(dbClient, truncateTokenSql)
	repo := NewAccountRepository(dbClient)

	// no token entities, so only hbar balance
	hbarAmount := &types.HbarAmount{Value: initialAccountBalance + sum(cryptoTransferAmounts)}
	expectedAmounts := types.AmountSlice{hbarAmount}

	// when
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		accountId,
		consensusTimestamp,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.ElementsMatch(suite.T(), expectedAmounts, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockNoInitialBalance() {
	// given
	accountId := suite.accountId
	db.ExecSql(dbClient, truncateAccountBalanceSql, truncateTokenBalanceSql)

	hbarAmount := &types.HbarAmount{Value: sum(cryptoTransferAmounts)}
	token1Amount := types.NewTokenAmount(token1, sum(token1TransferAmounts[:2]))
	token2Amount := types.NewTokenAmount(token2, sum(token2TransferAmounts[:2]))
	token3Amount := types.NewTokenAmount(token3, 2)
	token4Amount := types.NewTokenAmount(token4, 0)
	expectedAmounts := types.AmountSlice{hbarAmount, token1Amount, token2Amount, token3Amount, token4Amount}

	repo := NewAccountRepository(dbClient)

	// when
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		accountId,
		consensusTimestamp,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), suite.accountIdString, accountIdString)
	assert.ElementsMatch(suite.T(), expectedAmounts, actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockNoAccountBalanceFile() {
	// given
	db.ExecSql(dbClient, truncateAccountBalanceFileSql)
	accountId := suite.accountId
	repo := NewAccountRepository(dbClient)

	// when
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		accountId,
		consensusTimestamp,
	)

	// then
	assert.NotNil(suite.T(), err)
	assert.Empty(suite.T(), accountIdString)
	assert.Nil(suite.T(), actualAmounts)
}

func (suite *accountRepositorySuite) TestRetrieveBalanceAtBlockDbConnectionError() {
	// given
	accountId := suite.accountId
	repo := NewAccountRepository(invalidDbClient)

	// when
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		accountId,
		consensusTimestamp,
	)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Empty(suite.T(), accountIdString)
	assert.Nil(suite.T(), actualAmounts)
}

func sum(amounts []int64) int64 {
	var value int64
	for _, amount := range amounts {
		value += amount
	}

	return value
}

// run the suite
func TestAccountRepositoryWithAliasSuite(t *testing.T) {
	suite.Run(t, new(accountRepositoryWithAliasSuite))
}

type accountRepositoryWithAliasSuite struct {
	accountRepositorySuite
	networkAlias []byte
	publicKey    types.PublicKey
}

func (suite *accountRepositoryWithAliasSuite) SetupSuite() {
	suite.accountRepositorySuite.SetupSuite()

	sk, err := hedera.PrivateKeyGenerateEd25519()
	if err != nil {
		panic(err)
	}
	suite.publicKey = types.PublicKey{PublicKey: sk.PublicKey()}
	suite.networkAlias, err = suite.publicKey.ToAlias()
	if err != nil {
		panic(err)
	}
	suite.accountId, err = types.NewAccountIdFromAlias(suite.publicKey.BytesRaw(), 0, 0)
	if err != nil {
		panic(err)
	}
}

func (suite *accountRepositoryWithAliasSuite) SetupTest() {
	suite.setupTest(suite.networkAlias)

	// add account2 with the same alias but was deleted before account1
	// the entity row with deleted = true in entity table
	tdomain.NewEntityBuilder(dbClient, account2, account2CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.networkAlias).
		Deleted(true).
		ModifiedTimestamp(account2DeletedTimestamp).
		Persist()
	// the historical entry
	tdomain.NewEntityBuilder(dbClient, account2, account2CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.networkAlias).
		TimestampRange(account2CreatedTimestamp, account2DeletedTimestamp).
		Historical(true).
		Persist()
}

func (suite *accountRepositoryWithAliasSuite) TestRetrieveBalanceAtBlockNoAccountEntity() {
	// whey querying by alias and the account is not found, error is returned since without the alias to shard.realm.num
	// mapping, no balance info for the account can be retrieved
	// given
	db.ExecSql(dbClient, truncateEntitySql)
	repo := NewAccountRepository(dbClient)

	// when
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		suite.accountId,
		consensusTimestamp,
	)

	// then
	assert.NotNil(suite.T(), err)
	assert.Empty(suite.T(), accountIdString)
	assert.Nil(suite.T(), actualAmounts)
}
