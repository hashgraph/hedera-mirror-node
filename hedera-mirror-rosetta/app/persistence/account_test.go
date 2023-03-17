/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
	"fmt"
	"testing"

	"github.com/ethereum/go-ethereum/common/hexutil"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/db"
	tdomain "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/domain"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
	"github.com/thanhpk/randstr"
)

const (
	account1 = int64(9000) + iota
	treasury
	account2
	account3
	account4
	account5
	account6
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
	accountDeleteTimestamp         = secondSnapshotTimestamp + 180
	account1CreatedTimestamp       = firstSnapshotTimestamp - 100
	account2DeletedTimestamp       = account1CreatedTimestamp - 1
	account2CreatedTimestamp       = account2DeletedTimestamp - 9
	consensusTimestamp             = firstSnapshotTimestamp + 200
	dissociateTimestamp            = consensusTimestamp + 1
	firstSnapshotTimestamp   int64 = 1656693000269913000
	initialAccountBalance    int64 = 12345
	secondSnapshotTimestamp        = consensusTimestamp - 20
	thirdSnapshotTimestamp         = secondSnapshotTimestamp + 200

	// account3, account4, and account5 are for GetAccountAlias tests
	account3CreatedTimestamp = consensusTimestamp + 100
	account4CreatedTimestamp = consensusTimestamp + 110
	account5CreatedTimestamp = consensusTimestamp + 120
	account6CreatedTimestamp = consensusTimestamp + 130
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

	// account3 has ecdsaSecp256k1 alias, account4 has ed25519 alias, account5 has invalid alias
	account3Alias = hexutil.MustDecode("0x3a2103d9a822b91df7850274273a338c152e7bcfa2036b24cd9e3b29d07efd949b387a")
	account4Alias = hexutil.MustDecode("0x12205a081255a92b7c262bc2ea3ab7114b8a815345b3cc40f800b2b40914afecc44e")
	account5Alias = randstr.Bytes(48)
	// alias with invalid public key, the Key message is valid, but it's formed from an invalid 16-byte ED25519 pub key
	account6Alias = hexutil.MustDecode("0x1210815345b3cc40f800b2b40914afecc44e")
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
	accountAlias    []byte
	account3Alias   []byte
	account4Alias   []byte
	account5Alias   []byte
	account6Alias   []byte
}

func (suite *accountRepositorySuite) SetupSuite() {
	suite.accountId = types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(account1))
	suite.accountIdString = suite.accountId.String()
}

func (suite *accountRepositorySuite) SetupTest() {
	suite.integrationTest.SetupTest()
	associatedTokenAccounts := make([]domain.TokenAccount, 0)

	tdomain.NewEntityBuilder(dbClient, account1, account1CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.accountAlias).
		Persist()

	// persist tokens and tokenAccounts
	token1 = tdomain.NewTokenBuilder(dbClient, encodedTokenId1, firstSnapshotTimestamp+1, treasury).Persist()
	ta := tdomain.NewTokenAccountBuilder(dbClient, account1, encodedTokenId1, token1.CreatedTimestamp+2).Persist()
	associatedTokenAccounts = append(associatedTokenAccounts, ta)

	token2 = tdomain.NewTokenBuilder(dbClient, encodedTokenId2, firstSnapshotTimestamp+2, treasury).Persist()
	ta2 := tdomain.NewTokenAccountBuilder(dbClient, account1, encodedTokenId2, token2.CreatedTimestamp+2).
		Historical(true).
		TimestampRange(token2.CreatedTimestamp+2, dissociateTimestamp).
		Persist()
	tdomain.NewTokenAccountBuilderFromExisting(dbClient, ta2).
		Associated(false, dissociateTimestamp).
		Historical(false).
		Persist()

	token3 = tdomain.NewTokenBuilder(dbClient, encodedTokenId3, firstSnapshotTimestamp+3, treasury).
		Type(domain.TokenTypeNonFungibleUnique).
		Persist()
	ta3 := tdomain.NewTokenAccountBuilder(dbClient, account1, encodedTokenId3, token3.CreatedTimestamp+2).
		Historical(true).
		TimestampRange(token3.CreatedTimestamp+2, dissociateTimestamp).
		Persist()
	tdomain.NewTokenAccountBuilderFromExisting(dbClient, ta3).
		Associated(false, dissociateTimestamp).
		Historical(false).
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
		Errata(domain.ErrataTypeInsert).
		Timestamp(firstSnapshotTimestamp + 1).
		Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).
		Amount(12345).
		EntityId(account1).
		Errata(domain.ErrataTypeDelete).
		Timestamp(firstSnapshotTimestamp + 2).
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
			Historical(true).
			TimestampRange(ta.TimestampRange.Lower.Int, accountDeleteTimestamp-1).
			Persist()
		tdomain.NewTokenAccountBuilderFromExisting(dbClient, ta).
			Associated(false, accountDeleteTimestamp-1).
			Persist()
	}

	// accounts for GetAccountAlias tests
	tdomain.NewEntityBuilder(dbClient, account3, account3CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.account3Alias).
		Persist()
	tdomain.NewEntityBuilder(dbClient, account4, account4CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.account4Alias).
		Persist()
	tdomain.NewEntityBuilder(dbClient, account5, account5CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.account5Alias).
		Persist()
	tdomain.NewEntityBuilder(dbClient, account6, account6CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.account6Alias).
		Persist()
}

func (suite *accountRepositorySuite) TestGetAccountAlias() {
	tests := []struct {
		encodedId int64
		expected  string
	}{
		{encodedId: account3, expected: fmt.Sprintf("0.0.%d", account3)},
		{encodedId: account4, expected: fmt.Sprintf("0.0.%d", account4)},
	}

	repo := NewAccountRepository(dbClient)

	for _, tt := range tests {
		name := fmt.Sprintf("%d", tt.encodedId)
		suite.T().Run(name, func(t *testing.T) {
			accountId := types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(tt.encodedId))
			actual, err := repo.GetAccountAlias(defaultContext, accountId)
			assert.Nil(t, err)
			assert.Equal(t, tt.expected, actual.String())
		})
	}
}

func (suite *accountRepositorySuite) TestGetAccountAliasDbConnectionError() {
	// given
	accountId := types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(account3))
	repo := NewAccountRepository(invalidDbClient)

	// when
	actual, err := repo.GetAccountAlias(defaultContext, accountId)

	// then
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), types.AccountId{}, actual)
}

func (suite *accountRepositorySuite) TestGetAccountId() {
	// given
	aliasAccountId, _ := types.NewAccountIdFromAlias(account4Alias, 0, 0)
	repo := NewAccountRepository(dbClient)

	// when
	actual, err := repo.GetAccountId(defaultContext, aliasAccountId)

	// then
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), types.AccountId{}, actual)
}

func (suite *accountRepositorySuite) TestGetAccountIdNumericAccount() {
	// given
	accountId := types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(account1))
	repo := NewAccountRepository(dbClient)

	// when
	actual, err := repo.GetAccountId(defaultContext, accountId)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), accountId, actual)
}

func (suite *accountRepositorySuite) TestGetAccountIdDbConnectionError() {
	// given
	aliasAccountId, _ := types.NewAccountIdFromAlias(account4Alias, 0, 0)
	repo := NewAccountRepository(invalidDbClient)

	// when
	actual, err := repo.GetAccountId(defaultContext, aliasAccountId)

	// then
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), types.AccountId{}, actual)
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
		TimeOffset(-1).
		Persist()
	// extra transfers
	// account balance file time offset is -1, which means the transfer at secondSnapshotTimestamp is not included in
	// the snapshot. The balance response should include the amount in this transfer too.
	tdomain.NewTokenTransferBuilder(dbClient).
		AccountId(account1).
		Amount(10).
		Timestamp(secondSnapshotTimestamp).
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
	publicKey types.PublicKey
}

func (suite *accountRepositoryWithAliasSuite) SetupSuite() {
	suite.accountRepositorySuite.SetupSuite()

	sk, err := hedera.PrivateKeyGenerateEd25519()
	if err != nil {
		panic(err)
	}
	suite.publicKey = types.PublicKey{PublicKey: sk.PublicKey()}
	suite.accountAlias, _, err = suite.publicKey.ToAlias()
	if err != nil {
		panic(err)
	}
	suite.accountId, err = types.NewAccountIdFromAlias(suite.accountAlias, 0, 0)
	if err != nil {
		panic(err)
	}

	suite.account3Alias = account3Alias
	suite.account4Alias = account4Alias
	suite.account5Alias = account5Alias
	suite.account6Alias = account6Alias
}

func (suite *accountRepositoryWithAliasSuite) SetupTest() {
	suite.accountRepositorySuite.SetupTest()

	// add account2 with the same alias but was deleted before account1
	// the entity row with deleted = true in entity table
	tdomain.NewEntityBuilder(dbClient, account2, account2CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.accountAlias).
		Deleted(true).
		ModifiedTimestamp(account2DeletedTimestamp).
		Persist()
	// the historical entry
	tdomain.NewEntityBuilder(dbClient, account2, account2CreatedTimestamp, domain.EntityTypeAccount).
		Alias(suite.accountAlias).
		TimestampRange(account2CreatedTimestamp, account2DeletedTimestamp).
		Historical(true).
		Persist()
}

func (suite *accountRepositoryWithAliasSuite) TestGetAccountAlias() {
	tests := []struct {
		encodedId     int64
		expectedAlias []byte
	}{
		{encodedId: account3, expectedAlias: account3Alias},
		{encodedId: account4, expectedAlias: account4Alias},
	}

	repo := NewAccountRepository(dbClient)

	for _, tt := range tests {
		name := fmt.Sprintf("%d", tt.encodedId)
		suite.T().Run(name, func(t *testing.T) {
			accountId := types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(tt.encodedId))
			actual, err := repo.GetAccountAlias(defaultContext, accountId)
			assert.Nil(t, err)
			assert.Equal(t, tt.expectedAlias, actual.GetAlias())
		})
	}
}

func (suite *accountRepositoryWithAliasSuite) TestGetAccountAliasWithInvalidAlias() {
	tests := []struct {
		encodedId int64
		expected  string
	}{
		{encodedId: account5, expected: fmt.Sprintf("0.0.%d", account5)},
		{encodedId: account6, expected: fmt.Sprintf("0.0.%d", account6)},
	}

	repo := NewAccountRepository(dbClient)

	for _, tt := range tests {
		name := fmt.Sprintf("%d", tt.encodedId)
		suite.T().Run(name, func(t *testing.T) {
			accountId := types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(tt.encodedId))
			actual, err := repo.GetAccountAlias(defaultContext, accountId)
			assert.Nil(t, err)
			assert.Equal(t, tt.expected, actual.String())
		})
	}
}

func (suite *accountRepositoryWithAliasSuite) TestGetAccountId() {
	// given
	aliasAccountId, err := types.NewAccountIdFromAlias(account4Alias, 0, 0)
	assert.NoError(suite.T(), err)
	repo := NewAccountRepository(dbClient)
	expected := types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(account4))

	// when
	actual, rErr := repo.GetAccountId(defaultContext, aliasAccountId)

	// then
	assert.Nil(suite.T(), rErr)
	assert.Equal(suite.T(), expected, actual)
}

func (suite *accountRepositoryWithAliasSuite) TestGetAccountIdDeleted() {
	// given
	tdomain.NewEntityBuilder(dbClient, account4, 1, domain.EntityTypeAccount).
		Deleted(true).
		ModifiedTimestamp(accountDeleteTimestamp).
		Persist()
	aliasAccountId, _ := types.NewAccountIdFromAlias(account4Alias, 0, 0)
	repo := NewAccountRepository(dbClient)

	// when
	actual, rErr := repo.GetAccountId(defaultContext, aliasAccountId)

	// then
	assert.NotNil(suite.T(), rErr)
	assert.Equal(suite.T(), types.AccountId{}, actual)
}

func (suite *accountRepositoryWithAliasSuite) TestRetrieveBalanceAtBlockNoAccountEntity() {
	// whey querying by alias and the account is not found, expect 0 hbar balance returned
	db.ExecSql(dbClient, truncateEntitySql)
	repo := NewAccountRepository(dbClient)

	// when
	actualAmounts, accountIdString, err := repo.RetrieveBalanceAtBlock(
		defaultContext,
		suite.accountId,
		consensusTimestamp,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), types.AmountSlice{&types.HbarAmount{}}, actualAmounts)
	assert.Empty(suite.T(), accountIdString)
}
