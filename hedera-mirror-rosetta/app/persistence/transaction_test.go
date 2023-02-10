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
	"encoding/hex"
	"testing"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/tools"
	tdomain "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
	"github.com/thanhpk/randstr"
)

const (
	consensusStart int64 = 1000
	consensusEnd   int64 = 1100
	resultSuccess        = "SUCCESS"
)

var (
	firstEntityId               = domain.MustDecodeEntityId(12345)
	secondEntityId              = domain.MustDecodeEntityId(54321)
	thirdEntityId               = domain.MustDecodeEntityId(54350)
	newEntityId                 = domain.MustDecodeEntityId(55000)
	feeCollectorEntityId        = domain.MustDecodeEntityId(98)
	firstAccountId              = types.NewAccountIdFromEntityId(firstEntityId)
	secondAccountId             = types.NewAccountIdFromEntityId(secondEntityId)
	newAccountId                = types.NewAccountIdFromEntityId(newEntityId)
	nodeAccountId               = types.NewAccountIdFromEntityId(nodeEntityId)
	feeCollectorAccountId       = types.NewAccountIdFromEntityId(feeCollectorEntityId)
	tokenId1                    = domain.MustDecodeEntityId(25636)
	tokenId2                    = domain.MustDecodeEntityId(26700)
	tokenId3                    = domain.MustDecodeEntityId(26750) // nft
	tokenDecimals         int64 = 10
	tokenInitialSupply    int64 = 50000
)

func TestCategorizeHbarTransfers(t *testing.T) {
	emptyHbarTransfers := []hbarTransfer{}
	tests := []struct {
		name                           string
		hbarTransfers                  []hbarTransfer
		nonFeeTransfers                []hbarTransfer
		stakingRewardPayouts           []hbarTransfer
		expectedFeeHbarTransfers       []hbarTransfer
		expectedNonFeeTransfers        []hbarTransfer
		expectedStakingRewardTransfers []hbarTransfer
	}{
		{
			name:                           "empty",
			expectedFeeHbarTransfers:       emptyHbarTransfers,
			expectedNonFeeTransfers:        emptyHbarTransfers,
			expectedStakingRewardTransfers: emptyHbarTransfers,
		},
		{
			name: "empty non fee transfers",
			hbarTransfers: []hbarTransfer{
				{firstEntityId, -65},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			expectedFeeHbarTransfers: []hbarTransfer{
				{firstEntityId, -65},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			expectedNonFeeTransfers:        emptyHbarTransfers,
			expectedStakingRewardTransfers: emptyHbarTransfers,
		},
		{
			name: "simple transfer lists",
			hbarTransfers: []hbarTransfer{
				{firstEntityId, -165},
				{secondEntityId, 100},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			nonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100},
				{secondEntityId, 100},
			},
			expectedFeeHbarTransfers: []hbarTransfer{
				{firstEntityId, -65},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			expectedNonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100},
				{secondEntityId, 100},
			},
			expectedStakingRewardTransfers: emptyHbarTransfers,
		},
		{
			name: "non fee transfer not in transaction record",
			hbarTransfers: []hbarTransfer{
				{firstEntityId, -100499210447},
				{secondEntityId, 99999999958},
				{nodeEntityId, 2558345},
				{feeCollectorEntityId, 496652144},
			},
			nonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100000000000},
				{thirdEntityId, 100000000000},
			},
			expectedFeeHbarTransfers: []hbarTransfer{
				{firstEntityId, -499210447},
				{secondEntityId, 99999999958},
				{nodeEntityId, 2558345},
				{feeCollectorEntityId, 496652144},
			},
			expectedNonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100000000000},
			},
			expectedStakingRewardTransfers: emptyHbarTransfers,
		},
		{
			name: "staking reward payout",
			hbarTransfers: []hbarTransfer{
				{firstEntityId, -165},
				{secondEntityId, 200},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
				{stakingRewardAccountId, -100},
			},
			nonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100},
				{secondEntityId, 100},
			},
			stakingRewardPayouts: []hbarTransfer{{secondEntityId, 100}},
			expectedFeeHbarTransfers: []hbarTransfer{
				{firstEntityId, -65},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			expectedNonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100},
				{secondEntityId, 100},
			},
			expectedStakingRewardTransfers: []hbarTransfer{
				{secondEntityId, 100},
				{stakingRewardAccountId, -100},
			},
		},
		{
			name: "staking reward donation",
			hbarTransfers: []hbarTransfer{
				{firstEntityId, -165},
				{secondEntityId, 100},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			nonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100},
				{secondEntityId, 100},
				{firstEntityId, -200}, // firstEntityId donates the exact amount of his pending reward
				{stakingRewardAccountId, 200},
			},
			stakingRewardPayouts: []hbarTransfer{{firstEntityId, 200}},
			expectedFeeHbarTransfers: []hbarTransfer{
				{firstEntityId, -65},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			expectedNonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100},
				{secondEntityId, 100},
				{firstEntityId, -200},
				{stakingRewardAccountId, 200},
			},
			expectedStakingRewardTransfers: []hbarTransfer{
				{firstEntityId, 200},
				{stakingRewardAccountId, -200},
			},
		},
		{
			name: "partial staking reward donation",
			hbarTransfers: []hbarTransfer{
				{firstEntityId, -105},
				{secondEntityId, 100},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
				{stakingRewardAccountId, -60},
			},
			nonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100},
				{secondEntityId, 100},
				{firstEntityId, -140}, // firstEntityId donates part of his pending reward
				{stakingRewardAccountId, 140},
			},
			stakingRewardPayouts: []hbarTransfer{{firstEntityId, 200}},
			expectedFeeHbarTransfers: []hbarTransfer{
				{firstEntityId, -65},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			expectedNonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100},
				{secondEntityId, 100},
				{firstEntityId, -140},
				{stakingRewardAccountId, 140},
			},
			expectedStakingRewardTransfers: []hbarTransfer{
				{firstEntityId, 200},
				{stakingRewardAccountId, -200},
			},
		},
		{
			name: "staking reward donation more than pending",
			hbarTransfers: []hbarTransfer{
				{firstEntityId, -215},
				{secondEntityId, 100},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
				{stakingRewardAccountId, 50},
			},
			nonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100},
				{secondEntityId, 100},
				{firstEntityId, -250}, // firstEntityId donates more than his pending reward
				{stakingRewardAccountId, 250},
			},
			stakingRewardPayouts: []hbarTransfer{{firstEntityId, 200}},
			expectedFeeHbarTransfers: []hbarTransfer{
				{firstEntityId, -65},
				{nodeEntityId, 15},
				{feeCollectorEntityId, 50},
			},
			expectedNonFeeTransfers: []hbarTransfer{
				{firstEntityId, -100},
				{secondEntityId, 100},
				{firstEntityId, -250},
				{stakingRewardAccountId, 250},
			},
			expectedStakingRewardTransfers: []hbarTransfer{
				{firstEntityId, 200},
				{stakingRewardAccountId, -200},
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			feeHbarTransfers, adjustedNonFeeTransfers, stakingRewardTransfers := categorizeHbarTransfers(
				tt.hbarTransfers,
				tt.nonFeeTransfers,
				tt.stakingRewardPayouts,
			)
			assert.Equal(t, tt.expectedFeeHbarTransfers, feeHbarTransfers)
			assert.Equal(t, tt.expectedNonFeeTransfers, adjustedNonFeeTransfers)
			assert.Equal(t, tt.expectedStakingRewardTransfers, stakingRewardTransfers)
		})
	}
}

func TestGeneralOperationStatus(t *testing.T) {
	assert.Equal(t, "GENERAL_ERROR", types.GetTransactionResult(400))
}

func TestTransactionGetHashString(t *testing.T) {
	tx := transaction{Hash: []byte{1, 2, 3, 0xaa, 0xff}}
	assert.Equal(t, "0x010203aaff", tx.getHashString())
}

func TestHbarTransferGetAccount(t *testing.T) {
	hbarTransfer := hbarTransfer{AccountId: firstEntityId}
	assert.Equal(t, firstEntityId, hbarTransfer.getAccountId())
}

func TestHbarTransferGetAmount(t *testing.T) {
	hbarTransfer := hbarTransfer{Amount: 10}
	assert.Equal(t, &types.HbarAmount{Value: 10}, hbarTransfer.getAmount())
}

func TestSingleNftTransferGetAccount(t *testing.T) {
	singleNftTransfer := singleNftTransfer{accountId: firstEntityId}
	assert.Equal(t, firstEntityId, singleNftTransfer.getAccountId())
}

func TestSingleNftTransferGetAmount(t *testing.T) {
	tests := []struct {
		name     string
		receiver bool
		amount   int64
	}{
		{"receiver", true, 1},
		{"sender", false, -1},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			singleNftTransfer := singleNftTransfer{
				accountId:    firstEntityId,
				receiver:     tt.receiver,
				serialNumber: 1,
				tokenId:      tokenId1,
			}

			expected := &types.TokenAmount{
				SerialNumbers: []int64{1},
				TokenId:       tokenId1,
				Type:          domain.TokenTypeNonFungibleUnique,
				Value:         tt.amount,
			}

			assert.Equal(t, expected, singleNftTransfer.getAmount())
		})
	}
}

func TestTokenTransferGetAccount(t *testing.T) {
	tokenTransfer := tokenTransfer{AccountId: firstEntityId}
	assert.Equal(t, firstEntityId, tokenTransfer.getAccountId())
}

func TestTokenTransferGetAmount(t *testing.T) {
	tokenTransfer := tokenTransfer{Amount: 10, Decimals: 3, TokenId: tokenId1, Type: domain.TokenTypeFungibleCommon}
	assert.Equal(t, getFungibleTokenAmount(10, 3, tokenId1), tokenTransfer.getAmount())
}

func assertOperationIndexes(t *testing.T, operations types.OperationSlice) {
	makeRange := func(len int) []int64 {
		result := make([]int64, len)
		for i := range result {
			result[i] = int64(i)
		}
		return result
	}

	expected := makeRange(len(operations))
	actual := make([]int64, len(operations))
	for i := range operations {
		actual[i] = operations[i].Index
		// side effect, clear operation's index
		operations[i].Index = 0
	}

	assert.Equal(t, expected, actual)
}

func assertTransactions(t *testing.T, expected, actual []*types.Transaction) {
	getTransactionMap := func(transactions []*types.Transaction) map[string]*types.Transaction {
		result := make(map[string]*types.Transaction)
		for _, tx := range transactions {
			result[tx.Hash] = tx
		}
		return result
	}

	assert.Len(t, actual, len(expected))

	for _, tx := range actual {
		// assert the 0-based, unique, contiguous operations indexes
		assertOperationIndexes(t, tx.Operations)
	}

	actualTransactionMap := getTransactionMap(actual)
	expectedTransactionMap := getTransactionMap(expected)

	assert.Len(t, actualTransactionMap, len(expectedTransactionMap))

	for txHash, actualTx := range actualTransactionMap {
		assert.Contains(t, expectedTransactionMap, txHash)
		expectedTx := expectedTransactionMap[txHash]
		assert.Equal(t, expectedTx.EntityId, actualTx.EntityId)
		assert.Equal(t, expectedTx.Memo, actualTx.Memo)
		assert.ElementsMatch(t, expectedTx.Operations, actualTx.Operations)
	}
}

func TestTransactionRepositorySuite(t *testing.T) {
	suite.Run(t, new(transactionRepositorySuite))
}

type transactionRepositorySuite struct {
	integrationTest
	suite.Suite
}

func (suite *transactionRepositorySuite) TestNewTransactionRepository() {
	t := NewTransactionRepository(dbClient)
	assert.NotNil(suite.T(), t)
}

func (suite *transactionRepositorySuite) TestFindBetween() {
	// given
	expected := suite.setupDb(true)
	t := NewTransactionRepository(dbClient)

	// when
	actual, err := t.FindBetween(defaultContext, consensusStart, consensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assertTransactions(suite.T(), expected, actual)
}

func (suite *transactionRepositorySuite) TestFindBetweenTokenCreatedAtOrBeforeGenesisTimestamp() {
	// given
	// token1 created at genesisTimestamp - 1 and token2 created at genesisTimestamp
	genesisTimestamp := int64(100)
	tdomain.NewAccountBalanceFileBuilder(dbClient, genesisTimestamp).Persist()
	tdomain.NewTokenBuilder(dbClient, encodedTokenId1, genesisTimestamp-1, treasury).Persist()
	tdomain.NewTokenBuilder(dbClient, encodedTokenId2, genesisTimestamp, treasury).Persist()

	transaction := tdomain.NewTransactionBuilder(dbClient, treasury, genesisTimestamp+10).Persist()
	// add token transfers, should not be included in Transaction.Operations
	transferTimestamp := transaction.ConsensusTimestamp
	tdomain.NewTokenTransferBuilder(dbClient).
		AccountId(account1).
		Amount(10).
		TokenId(encodedTokenId1).
		Timestamp(transferTimestamp).
		Persist()
	tdomain.NewTokenTransferBuilder(dbClient).
		AccountId(treasury).
		Amount(-10).
		TokenId(encodedTokenId1).
		Timestamp(transferTimestamp).
		Persist()
	tdomain.NewTokenTransferBuilder(dbClient).
		AccountId(account1).
		Amount(10).
		TokenId(encodedTokenId2).
		Timestamp(transferTimestamp).
		Persist()
	tdomain.NewTokenTransferBuilder(dbClient).
		AccountId(treasury).
		Amount(-10).
		TokenId(encodedTokenId2).
		Timestamp(transferTimestamp).
		Persist()
	// add crypto transfers
	tdomain.NewCryptoTransferBuilder(dbClient).Amount(-20).EntityId(treasury).Timestamp(transferTimestamp).Persist()
	tdomain.NewCryptoTransferBuilder(dbClient).Amount(20).EntityId(3).Timestamp(transferTimestamp).Persist()

	expected := []*types.Transaction{
		{
			Hash: tools.SafeAddHexPrefix(hex.EncodeToString(transaction.TransactionHash)),
			Memo: []byte{},
			Operations: types.OperationSlice{
				{
					AccountId: types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(3)),
					Amount:    &types.HbarAmount{Value: 20},
					Type:      types.OperationTypeFee,
					Status:    resultSuccess,
				},
				{
					AccountId: types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(treasury)),
					Amount:    &types.HbarAmount{Value: -20},
					Index:     1,
					Type:      types.OperationTypeFee,
					Status:    resultSuccess,
				},
			},
		},
	}
	t := NewTransactionRepository(dbClient)

	// when
	actual, err := t.FindBetween(defaultContext, transaction.ConsensusTimestamp, transaction.ConsensusTimestamp)

	// then
	assert.Nil(suite.T(), err)
	assert.ElementsMatch(suite.T(), expected, actual)
}

func (suite *transactionRepositorySuite) TestFindBetweenHavingDisappearingTokenTransfer() {
	// given
	// the disappearing token/nft transfers are in the corresponding db table
	genesisTimestamp := int64(100)
	tdomain.NewAccountBalanceFileBuilder(dbClient, genesisTimestamp).Persist()

	token1 := tdomain.NewTokenBuilder(dbClient, encodedTokenId1, genesisTimestamp+1, treasury).Persist()
	tdomain.NewEntityBuilderFromToken(dbClient, token1).Deleted(true).ModifiedAfter(10).Persist()

	token2 := tdomain.NewTokenBuilder(dbClient, encodedTokenId2, genesisTimestamp+2, treasury).
		Type(domain.TokenTypeNonFungibleUnique).
		Persist()
	tdomain.NewEntityBuilderFromToken(dbClient, token2).Deleted(true).ModifiedAfter(10).Persist()

	// token accounts
	dissociateTimestamp := genesisTimestamp + 53
	tdomain.NewTokenAccountBuilder(dbClient, account1, encodedTokenId1, token1.CreatedTimestamp+1).
		Associated(false, dissociateTimestamp).
		Persist()
	tdomain.NewTokenAccountBuilder(dbClient, account1, encodedTokenId2, token2.CreatedTimestamp+1).
		Associated(false, dissociateTimestamp).
		Persist()

	// token1 received
	tdomain.NewTokenTransferBuilder(dbClient).
		AccountId(account1).
		Amount(10).
		TokenId(encodedTokenId1).
		Timestamp(token1.CreatedTimestamp + 10).
		Persist()
	// token1 disappearing transfer at dissociation
	tdomain.NewTokenTransferBuilder(dbClient).
		AccountId(account1).
		Amount(-10).
		TokenId(encodedTokenId1).
		Timestamp(dissociateTimestamp).
		Persist()
	// token2 disappearing transfers at dissociation
	tdomain.NewNftTransferBuilder(dbClient).
		SenderAccountId(account1).
		TokenId(encodedTokenId2).
		SerialNumber(1).
		Timestamp(dissociateTimestamp).
		Persist()
	tdomain.NewNftTransferBuilder(dbClient).
		SenderAccountId(account1).
		TokenId(encodedTokenId2).
		SerialNumber(2).
		Timestamp(dissociateTimestamp).
		Persist()

	// nft marked as deleted at dissociate timestamp
	tdomain.NewNftBuilder(dbClient, encodedTokenId2, 1, token2.CreatedTimestamp+10).
		AccountId(account1).
		Deleted(true).
		ModifiedTimestamp(dissociateTimestamp).
		Persist()
	tdomain.NewNftBuilder(dbClient, encodedTokenId2, 2, token2.CreatedTimestamp+10).
		AccountId(account1).
		Deleted(true).
		ModifiedTimestamp(dissociateTimestamp).
		Persist()

	// the dissociate transaction
	transaction := tdomain.NewTransactionBuilder(dbClient, account1, dissociateTimestamp-10).
		ConsensusTimestamp(dissociateTimestamp).
		EntityId(account1).
		Type(domain.TransactionTypeTokenDissociate).
		Persist()

	account1EntityId := domain.MustDecodeEntityId(account1)
	account1Id := types.NewAccountIdFromEntityId(account1EntityId)
	expected := []*types.Transaction{
		{
			EntityId: &account1EntityId,
			Hash:     tools.SafeAddHexPrefix(hex.EncodeToString(transaction.TransactionHash)),
			Memo:     []byte{},
			Operations: types.OperationSlice{
				{
					AccountId: account1Id,
					Amount:    types.NewTokenAmount(token1, -10),
					Type:      types.OperationTypeTokenDissociate,
					Status:    resultSuccess,
				},
				{
					AccountId: account1Id,
					Amount:    types.NewTokenAmount(token2, -1).SetSerialNumbers([]int64{1}),
					Index:     1,
					Type:      types.OperationTypeTokenDissociate,
					Status:    resultSuccess,
				},
				{
					AccountId: account1Id,
					Amount:    types.NewTokenAmount(token2, -1).SetSerialNumbers([]int64{2}),
					Index:     2,
					Type:      types.OperationTypeTokenDissociate,
					Status:    resultSuccess,
				},
			},
		},
	}
	t := NewTransactionRepository(dbClient)

	// when
	actual, err := t.FindBetween(defaultContext, dissociateTimestamp, dissociateTimestamp)

	// then
	assert.Nil(suite.T(), err)
	assert.ElementsMatch(suite.T(), expected, actual)
}

func (suite *transactionRepositorySuite) TestFindBetweenMissingDisappearingTokenTransfer() {
	// given
	// the disappearing token/nft transfers are missing
	genesisTimestamp := int64(100)
	tdomain.NewAccountBalanceFileBuilder(dbClient, genesisTimestamp).Persist()

	token1 := tdomain.NewTokenBuilder(dbClient, encodedTokenId1, genesisTimestamp+1, treasury).Persist()
	tdomain.NewEntityBuilderFromToken(dbClient, token1).Deleted(true).ModifiedAfter(100).Persist()

	token2 := tdomain.NewTokenBuilder(dbClient, encodedTokenId2, genesisTimestamp+2, treasury).
		Type(domain.TokenTypeNonFungibleUnique).
		Persist()
	entity2 := tdomain.NewEntityBuilderFromToken(dbClient, token2).Deleted(true).ModifiedAfter(100).Persist()

	// token accounts
	dissociateTimestamp := entity2.GetModifiedTimestamp() + 100
	tdomain.NewTokenAccountBuilder(dbClient, account1, encodedTokenId1, token1.CreatedTimestamp+1).
		Associated(false, dissociateTimestamp).
		Persist()
	tdomain.NewTokenAccountBuilder(dbClient, account1, encodedTokenId2, token2.CreatedTimestamp+1).
		Associated(false, dissociateTimestamp).
		Persist()

	// token1 received
	tdomain.NewTokenTransferBuilder(dbClient).
		AccountId(account1).
		Amount(10).
		TokenId(encodedTokenId1).
		Timestamp(token1.CreatedTimestamp + 10).
		Persist()

	// nft owned by account1 are not deleted
	tdomain.NewNftBuilder(dbClient, encodedTokenId2, 1, token2.CreatedTimestamp+10).
		AccountId(account1).
		Persist()
	tdomain.NewNftBuilder(dbClient, encodedTokenId2, 2, token2.CreatedTimestamp+10).
		AccountId(account1).
		Deleted(false).
		Persist()

	// the dissociate transaction
	transaction := tdomain.NewTransactionBuilder(dbClient, account1, dissociateTimestamp-10).
		ConsensusTimestamp(dissociateTimestamp).
		EntityId(account1).
		Type(domain.TransactionTypeTokenDissociate).
		Persist()

	account1EntityId := domain.MustDecodeEntityId(account1)
	account1Id := types.NewAccountIdFromEntityId(account1EntityId)
	expected := []*types.Transaction{
		{
			EntityId: &account1EntityId,
			Hash:     tools.SafeAddHexPrefix(hex.EncodeToString(transaction.TransactionHash)),
			Memo:     []byte{},
			Operations: types.OperationSlice{
				{
					AccountId: account1Id,
					Amount:    types.NewTokenAmount(token1, -10),
					Type:      types.OperationTypeTokenDissociate,
					Status:    resultSuccess,
				},
				{
					AccountId: account1Id,
					Amount:    types.NewTokenAmount(token2, -1).SetSerialNumbers([]int64{1}),
					Index:     1,
					Type:      types.OperationTypeTokenDissociate,
					Status:    resultSuccess,
				},
				{
					AccountId: account1Id,
					Amount:    types.NewTokenAmount(token2, -1).SetSerialNumbers([]int64{2}),
					Index:     2,
					Type:      types.OperationTypeTokenDissociate,
					Status:    resultSuccess,
				},
			},
		},
	}
	t := NewTransactionRepository(dbClient)

	// when
	actual, err := t.FindBetween(defaultContext, dissociateTimestamp, dissociateTimestamp)

	// then
	assert.Nil(suite.T(), err)
	assert.ElementsMatch(suite.T(), expected, actual)
}

func (suite *transactionRepositorySuite) TestFindBetweenNoTokenEntity() {
	// given
	expected := suite.setupDb(false)
	t := NewTransactionRepository(dbClient)

	// when
	actual, err := t.FindBetween(defaultContext, consensusStart, consensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assertTransactions(suite.T(), expected, actual)
}

func (suite *transactionRepositorySuite) TestFindBetweenThrowsWhenStartAfterEnd() {
	// given
	t := NewTransactionRepository(dbClient)

	// when
	actual, err := t.FindBetween(defaultContext, consensusStart, consensusStart-1)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestFindBetweenDbConnectionError() {
	// given
	t := NewTransactionRepository(invalidDbClient)

	// when
	actual, err := t.FindBetween(defaultContext, consensusStart, consensusEnd)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestFindByHashInBlock() {
	// given
	expected := suite.setupDb(true)
	t := NewTransactionRepository(dbClient)

	// when
	actual, err := t.FindByHashInBlock(defaultContext, expected[0].Hash, consensusStart, consensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assertTransactions(suite.T(), []*types.Transaction{expected[0]}, []*types.Transaction{actual})
}

func (suite *transactionRepositorySuite) TestFindByHashInBlockNoTokenEntity() {
	// given
	expected := suite.setupDb(false)
	t := NewTransactionRepository(dbClient)

	// when
	actual, err := t.FindByHashInBlock(defaultContext, expected[1].Hash, consensusStart, consensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assertTransactions(suite.T(), []*types.Transaction{expected[1]}, []*types.Transaction{actual})
}

func (suite *transactionRepositorySuite) TestFindByHashInBlockThrowsInvalidHash() {
	// given
	t := NewTransactionRepository(dbClient)

	// when
	actual, err := t.FindByHashInBlock(defaultContext, "invalid hash", consensusStart, consensusEnd)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestFindByHashInBlockThrowsNotFound() {
	// given
	t := NewTransactionRepository(dbClient)

	// when
	actual, err := t.FindByHashInBlock(defaultContext, "0x123456", consensusStart, consensusEnd)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestFindByHashInBlockDbConnectionError() {
	// given
	t := NewTransactionRepository(invalidDbClient)

	// when
	actual, err := t.FindByHashInBlock(defaultContext, "0x123456", consensusStart, consensusEnd)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) setupDb(createTokenEntity bool) []*types.Transaction {
	var consensusTimestamp, validStartNs int64

	tick := func(nanos int64) {
		consensusTimestamp += nanos
		validStartNs += nanos
	}

	// create account balance file
	genesisTimestamp := consensusStart - 200
	tdomain.NewAccountBalanceFileBuilder(dbClient, genesisTimestamp).Persist()

	// successful crypto transfer transaction
	consensusTimestamp = consensusStart + 1
	validStartNs = consensusStart - 10
	errataTypeInsert := domain.ErrataTypeInsert
	cryptoTransfers := []domain.CryptoTransfer{
		{Amount: -150, ConsensusTimestamp: consensusTimestamp, EntityId: firstEntityId,
			Errata: &errataTypeInsert, PayerAccountId: firstEntityId},
		{Amount: 135, ConsensusTimestamp: consensusTimestamp, EntityId: secondEntityId,
			Errata: &errataTypeInsert, PayerAccountId: firstEntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: feeCollectorEntityId,
			PayerAccountId: firstEntityId},
	}
	nonFeeTransfers := []domain.NonFeeTransfer{
		{Amount: -135, ConsensusTimestamp: consensusTimestamp, EntityId: &firstEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 135, ConsensusTimestamp: consensusTimestamp, EntityId: &secondEntityId,
			PayerAccountId: firstEntityId},
	}
	addTransaction(dbClient, consensusTimestamp, nil, &nodeEntityId, firstEntityId, 22,
		[]byte{0x1, 0x2, 0x3}, domain.TransactionTypeCryptoTransfer, validStartNs, cryptoTransfers, nonFeeTransfers,
		nil, nil, []byte("simple transfer"))

	// duplicate transaction
	consensusTimestamp += 1
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -15, ConsensusTimestamp: consensusTimestamp, EntityId: firstEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: feeCollectorEntityId,
			PayerAccountId: firstEntityId},
	}
	addTransaction(dbClient, consensusTimestamp, nil, &nodeEntityId, firstEntityId, 11,
		[]byte{0x1, 0x2, 0x3}, domain.TransactionTypeCryptoTransfer, validStartNs, cryptoTransfers, nil, nil, nil,
		[]byte("simple transfer"))
	operationType := types.OperationTypeCryptoTransfer
	operations1 := types.OperationSlice{
		{AccountId: firstAccountId, Amount: &types.HbarAmount{Value: -135}, Type: operationType, Status: resultSuccess},
		{AccountId: secondAccountId, Amount: &types.HbarAmount{Value: 135}, Type: operationType, Status: resultSuccess},
		{AccountId: firstAccountId, Amount: &types.HbarAmount{Value: -15}, Type: types.OperationTypeFee,
			Status: resultSuccess},
		{AccountId: nodeAccountId, Amount: &types.HbarAmount{Value: 5}, Type: types.OperationTypeFee,
			Status: resultSuccess},
		{AccountId: feeCollectorAccountId, Amount: &types.HbarAmount{Value: 10}, Type: types.OperationTypeFee,
			Status: resultSuccess},
		{AccountId: firstAccountId, Amount: &types.HbarAmount{Value: -15}, Type: types.OperationTypeFee,
			Status: resultSuccess},
		{AccountId: nodeAccountId, Amount: &types.HbarAmount{Value: 5}, Type: types.OperationTypeFee,
			Status: resultSuccess},
		{AccountId: feeCollectorAccountId, Amount: &types.HbarAmount{Value: 10}, Type: types.OperationTypeFee,
			Status: resultSuccess},
	}
	expectedTransaction1 := &types.Transaction{
		Hash:       "0x010203",
		Memo:       []byte("simple transfer"),
		Operations: operations1,
	}

	// a successful crypto transfer + token transfer transaction
	tick(1)
	if createTokenEntity {
		tdomain.NewTokenBuilder(dbClient, tokenId1.EncodedId, genesisTimestamp+2, feeCollectorEntityId.EncodedId).
			Decimals(tokenDecimals).
			InitialSupply(tokenInitialSupply).
			Persist()
	}

	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -230, ConsensusTimestamp: consensusTimestamp, EntityId: firstEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 215, ConsensusTimestamp: consensusTimestamp, EntityId: secondEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: feeCollectorEntityId,
			PayerAccountId: firstEntityId},
	}
	nonFeeTransfers = []domain.NonFeeTransfer{
		{Amount: -215, ConsensusTimestamp: consensusTimestamp, EntityId: &firstEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 215, ConsensusTimestamp: consensusTimestamp, EntityId: &secondEntityId,
			PayerAccountId: firstEntityId},
	}
	tokenTransfers := []domain.TokenTransfer{
		{AccountId: firstEntityId, Amount: -160, ConsensusTimestamp: consensusTimestamp, TokenId: tokenId1,
			PayerAccountId: firstEntityId},
		{AccountId: secondEntityId, Amount: 160, ConsensusTimestamp: consensusTimestamp, TokenId: tokenId1,
			PayerAccountId: firstEntityId},
	}
	addTransaction(dbClient, consensusTimestamp, nil, &nodeEntityId, firstEntityId, 22,
		[]byte{0xa, 0xb, 0xc}, domain.TransactionTypeCryptoTransfer, validStartNs, cryptoTransfers, nonFeeTransfers,
		tokenTransfers, nil, []byte{})
	operations2 := types.OperationSlice{
		{AccountId: firstAccountId, Amount: &types.HbarAmount{Value: -215}, Type: operationType, Status: resultSuccess},
		{AccountId: secondAccountId, Amount: &types.HbarAmount{Value: 215}, Type: operationType, Status: resultSuccess},
		{AccountId: firstAccountId, Amount: &types.HbarAmount{Value: -15}, Type: types.OperationTypeFee,
			Status: resultSuccess},
		{AccountId: nodeAccountId, Amount: &types.HbarAmount{Value: 5}, Type: types.OperationTypeFee,
			Status: resultSuccess},
		{AccountId: feeCollectorAccountId, Amount: &types.HbarAmount{Value: 10}, Type: types.OperationTypeFee,
			Status: resultSuccess},
	}
	if createTokenEntity {
		operations2 = append(
			operations2,
			types.Operation{
				AccountId: firstAccountId,
				Amount:    getFungibleTokenAmount(-160, tokenDecimals, tokenId1),
				Type:      operationType,
				Status:    resultSuccess,
			},
			types.Operation{
				AccountId: secondAccountId,
				Amount:    getFungibleTokenAmount(160, tokenDecimals, tokenId1),
				Type:      operationType,
				Status:    resultSuccess,
			},
		)
	}
	expectedTransaction2 := &types.Transaction{Hash: "0x0a0b0c", Memo: []byte{}, Operations: operations2}

	tdomain.NewTokenBuilder(dbClient, tokenId2.EncodedId, genesisTimestamp+3, firstEntityId.EncodedId).
		Decimals(tokenDecimals).
		InitialSupply(tokenInitialSupply).
		Persist()

	// token create transaction
	tick(1)
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -15, ConsensusTimestamp: consensusTimestamp, EntityId: firstEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: feeCollectorEntityId,
			PayerAccountId: firstEntityId},
	}
	tokenTransfers = []domain.TokenTransfer{
		{AccountId: firstEntityId, Amount: tokenInitialSupply, ConsensusTimestamp: consensusTimestamp,
			TokenId: tokenId2, PayerAccountId: firstEntityId},
	}
	addTransaction(dbClient, consensusTimestamp, &tokenId2, &nodeEntityId, firstEntityId, 22,
		[]byte{0xaa, 0xcc, 0xdd}, domain.TransactionTypeTokenCreation, validStartNs, cryptoTransfers, nil,
		tokenTransfers, nil, nil)
	metadata := map[string]interface{}{
		"currency": &rTypes.Currency{
			Symbol:   tokenId2.String(),
			Decimals: int32(tokenDecimals),
			Metadata: map[string]interface{}{types.MetadataKeyType: domain.TokenTypeFungibleCommon},
		},
		"freeze_default": false,
		"initial_supply": tokenInitialSupply,
	}
	operationType = types.OperationTypeTokenCreate
	expectedTransaction3 := &types.Transaction{
		EntityId: &tokenId2,
		Hash:     "0xaaccdd",
		Memo:     []byte{},
		Operations: types.OperationSlice{
			{AccountId: firstAccountId, Amount: &types.HbarAmount{Value: -15}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: nodeAccountId, Amount: &types.HbarAmount{Value: 5}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: feeCollectorAccountId, Amount: &types.HbarAmount{Value: 10}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: firstAccountId, Type: operationType, Status: resultSuccess, Metadata: metadata},
			{
				AccountId: firstAccountId,
				Amount:    getFungibleTokenAmount(tokenInitialSupply, tokenDecimals, tokenId2),
				Type:      operationType,
				Status:    resultSuccess,
			},
		},
	}

	// nft create
	tdomain.NewTokenBuilder(dbClient, tokenId3.EncodedId, genesisTimestamp+4, firstEntityId.EncodedId).
		Type(domain.TokenTypeNonFungibleUnique).
		Persist()
	tick(1)
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -15, ConsensusTimestamp: consensusTimestamp, EntityId: firstEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: feeCollectorEntityId,
			PayerAccountId: firstEntityId},
	}
	addTransaction(dbClient, consensusTimestamp, &tokenId3, &nodeEntityId, firstEntityId, 22,
		[]byte{0xaa, 0x11, 0x22}, domain.TransactionTypeTokenCreation, validStartNs, cryptoTransfers, nil, nil, nil,
		nil)
	metadata = map[string]interface{}{
		"currency": &rTypes.Currency{
			Symbol:   tokenId3.String(),
			Metadata: map[string]interface{}{types.MetadataKeyType: domain.TokenTypeNonFungibleUnique},
		},
		"freeze_default": false,
		"initial_supply": int64(0),
	}
	expectedTransaction4 := &types.Transaction{
		EntityId: &tokenId3,
		Hash:     "0xaa1122",
		Memo:     []byte{},
		Operations: types.OperationSlice{
			{AccountId: firstAccountId, Amount: &types.HbarAmount{Value: -15}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: nodeAccountId, Amount: &types.HbarAmount{Value: 5}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: feeCollectorAccountId, Amount: &types.HbarAmount{Value: 10}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: firstAccountId, Type: operationType, Status: resultSuccess, Metadata: metadata},
		},
	}

	// nft mint
	tick(1)
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -15, ConsensusTimestamp: consensusTimestamp, EntityId: firstEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: feeCollectorEntityId,
			PayerAccountId: firstEntityId},
	}
	nftTransfers := []domain.NftTransfer{
		{consensusTimestamp, firstEntityId, &firstEntityId, nil, 1, tokenId3},
		{consensusTimestamp, firstEntityId, &firstEntityId, nil, 2, tokenId3},
		{consensusTimestamp, firstEntityId, &firstEntityId, nil, 3, tokenId3},
		{consensusTimestamp, firstEntityId, &firstEntityId, nil, 4, tokenId3},
	}
	addTransaction(dbClient, consensusTimestamp, &tokenId3, &nodeEntityId, firstEntityId, 22,
		[]byte{0xaa, 0x11, 0x33}, domain.TransactionTypeTokenMint, validStartNs, cryptoTransfers, nil, nil,
		nftTransfers, nil)
	operationType = types.OperationTypeTokenMint
	expectedTransaction5 := &types.Transaction{
		EntityId: &tokenId3,
		Hash:     "0xaa1133",
		Memo:     []byte{},
		Operations: types.OperationSlice{
			{AccountId: firstAccountId, Amount: &types.HbarAmount{Value: -15}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: nodeAccountId, Amount: &types.HbarAmount{Value: 5}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: feeCollectorAccountId, Amount: &types.HbarAmount{Value: 10}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: firstAccountId, Amount: getNftTokenAmount(1, 1, tokenId3), Type: operationType,
				Status: resultSuccess},
			{AccountId: firstAccountId, Amount: getNftTokenAmount(1, 2, tokenId3), Type: operationType,
				Status: resultSuccess},
			{AccountId: firstAccountId, Amount: getNftTokenAmount(1, 3, tokenId3), Type: operationType,
				Status: resultSuccess},
			{AccountId: firstAccountId, Amount: getNftTokenAmount(1, 4, tokenId3), Type: operationType,
				Status: resultSuccess},
		},
	}

	// nft transfer
	tick(1)
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -15, ConsensusTimestamp: consensusTimestamp, EntityId: firstEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: feeCollectorEntityId,
			PayerAccountId: firstEntityId},
	}
	nftTransfers = []domain.NftTransfer{
		{consensusTimestamp, firstEntityId, &secondEntityId, &firstEntityId, 1, tokenId3},
	}
	addTransaction(dbClient, consensusTimestamp, nil, &nodeEntityId, firstEntityId,
		22, []byte{0xaa, 0x11, 0x66}, domain.TransactionTypeCryptoTransfer, validStartNs, cryptoTransfers, nil,
		nil, nftTransfers, nil)
	operationType = types.OperationTypeCryptoTransfer
	expectedTransaction6 := &types.Transaction{
		Hash: "0xaa1166",
		Memo: []byte{},
		Operations: types.OperationSlice{
			{AccountId: firstAccountId, Amount: &types.HbarAmount{Value: -15}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: nodeAccountId, Amount: &types.HbarAmount{Value: 5}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: feeCollectorAccountId, Amount: &types.HbarAmount{Value: 10}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: firstAccountId, Amount: getNftTokenAmount(-1, 1, tokenId3), Type: operationType,
				Status: resultSuccess},
			{AccountId: secondAccountId, Amount: getNftTokenAmount(1, 1, tokenId3), Type: operationType,
				Status: resultSuccess},
		},
	}

	// a failed crypto transfer due to insufficient account balance, the spurious transfers are marked as 'DELETE'
	tick(1)
	errataTypeDelete := domain.ErrataTypeDelete
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -120, ConsensusTimestamp: consensusTimestamp, EntityId: firstEntityId, PayerAccountId: firstEntityId},
		{Amount: 100, ConsensusTimestamp: consensusTimestamp, EntityId: feeCollectorEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 20, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId,
			PayerAccountId: firstEntityId},
		{Amount: -1000000, ConsensusTimestamp: consensusTimestamp, EntityId: firstEntityId,
			Errata: &errataTypeDelete, PayerAccountId: firstEntityId},
		{Amount: 1000000, ConsensusTimestamp: consensusTimestamp, EntityId: secondEntityId,
			Errata: &errataTypeDelete, PayerAccountId: firstEntityId},
	}
	transactionHash := randstr.Bytes(6)
	addTransaction(dbClient, consensusTimestamp, nil, &nodeEntityId, firstEntityId, 28,
		transactionHash, domain.TransactionTypeCryptoTransfer, validStartNs, cryptoTransfers, nil, nil, nil, nil)
	operationType = types.OperationTypeCryptoTransfer
	expectedTransaction7 := &types.Transaction{
		Hash: tools.SafeAddHexPrefix(hex.EncodeToString(transactionHash)),
		Memo: []byte{},
		Operations: types.OperationSlice{
			{AccountId: firstAccountId, Amount: &types.HbarAmount{Value: -120}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: feeCollectorAccountId, Amount: &types.HbarAmount{Value: 100}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: nodeAccountId, Amount: &types.HbarAmount{Value: 20}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: firstAccountId, Amount: &types.HbarAmount{}, Type: types.OperationTypeCryptoTransfer,
				Status: types.GetTransactionResult(28)},
		},
	}

	// crypto create transaction
	tick(1)
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -620, ConsensusTimestamp: consensusTimestamp, EntityId: firstEntityId, PayerAccountId: firstEntityId},
		{Amount: 500, ConsensusTimestamp: consensusTimestamp, EntityId: newEntityId, PayerAccountId: firstEntityId},
		{Amount: 100, ConsensusTimestamp: consensusTimestamp, EntityId: feeCollectorEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 20, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId, PayerAccountId: firstEntityId},
	}
	nonFeeTransfers = []domain.NonFeeTransfer{
		{Amount: -500, ConsensusTimestamp: consensusTimestamp, EntityId: &firstEntityId, PayerAccountId: firstEntityId},
		{Amount: -500, ConsensusTimestamp: consensusTimestamp, PayerAccountId: firstEntityId}, // with nil entity id
		{Amount: 500, ConsensusTimestamp: consensusTimestamp, EntityId: &newEntityId, PayerAccountId: firstEntityId},
	}
	transactionHash = randstr.Bytes(6)
	addTransaction(dbClient, consensusTimestamp, &newEntityId, &nodeEntityId, firstEntityId, 22, transactionHash,
		domain.TransactionTypeCryptoCreateAccount, validStartNs, cryptoTransfers, nonFeeTransfers, nil, nil, nil)

	operationType = types.OperationTypeCryptoCreateAccount
	expectedTransaction8 := &types.Transaction{
		EntityId: &newEntityId,
		Hash:     tools.SafeAddHexPrefix(hex.EncodeToString(transactionHash)),
		Memo:     []byte{},
		Operations: types.OperationSlice{
			{AccountId: firstAccountId, Amount: &types.HbarAmount{Value: -500},
				Type: types.OperationTypeCryptoCreateAccount, Status: resultSuccess},
			{AccountId: newAccountId, Amount: &types.HbarAmount{Value: 500},
				Type: types.OperationTypeCryptoCreateAccount, Status: resultSuccess},
			{AccountId: firstAccountId, Amount: &types.HbarAmount{Value: -120}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: feeCollectorAccountId, Amount: &types.HbarAmount{Value: 100}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: nodeAccountId, Amount: &types.HbarAmount{Value: 20}, Type: types.OperationTypeFee,
				Status: resultSuccess},
		},
	}

	// crypto transfer transaction with staking reward payout
	tick(1)
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -520, ConsensusTimestamp: consensusTimestamp, EntityId: firstEntityId, PayerAccountId: firstEntityId},
		{Amount: 500, ConsensusTimestamp: consensusTimestamp, EntityId: newEntityId, PayerAccountId: firstEntityId},
		{Amount: 100, ConsensusTimestamp: consensusTimestamp, EntityId: feeCollectorEntityId,
			PayerAccountId: firstEntityId},
		{Amount: 20, ConsensusTimestamp: consensusTimestamp, EntityId: nodeEntityId, PayerAccountId: firstEntityId},
		{Amount: -100, ConsensusTimestamp: consensusTimestamp, EntityId: stakingRewardAccountId,
			PayerAccountId: firstEntityId},
	}
	nonFeeTransfers = []domain.NonFeeTransfer{
		{Amount: -500, ConsensusTimestamp: consensusTimestamp, EntityId: &firstEntityId, PayerAccountId: firstEntityId},
		{Amount: 500, ConsensusTimestamp: consensusTimestamp, EntityId: &newEntityId, PayerAccountId: firstEntityId},
	}
	transactionHash = randstr.Bytes(6)
	addTransaction(dbClient, consensusTimestamp, nil, &nodeEntityId, firstEntityId, 22, transactionHash,
		domain.TransactionTypeCryptoTransfer, validStartNs, cryptoTransfers, nonFeeTransfers, nil, nil, nil)
	tdomain.NewStakingRewardTransferBuilder(dbClient).
		AccountId(firstEntityId.EncodedId).
		Amount(100).
		ConsensusTimestamp(consensusTimestamp).
		PayerAccountId(firstEntityId.EncodedId).
		Persist()

	operationType = types.OperationTypeCryptoTransfer
	expectedTransaction9 := &types.Transaction{
		Hash: tools.SafeAddHexPrefix(hex.EncodeToString(transactionHash)),
		Memo: []byte{},
		Operations: types.OperationSlice{
			{AccountId: firstAccountId, Amount: &types.HbarAmount{Value: -500}, Type: operationType,
				Status: resultSuccess},
			{AccountId: newAccountId, Amount: &types.HbarAmount{Value: 500}, Type: operationType,
				Status: resultSuccess},
			{AccountId: firstAccountId, Amount: &types.HbarAmount{Value: -120}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: feeCollectorAccountId, Amount: &types.HbarAmount{Value: 100}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: nodeAccountId, Amount: &types.HbarAmount{Value: 20}, Type: types.OperationTypeFee,
				Status: resultSuccess},
			{AccountId: firstAccountId, Amount: &types.HbarAmount{Value: 100}, Type: types.OperationTypeCryptoTransfer,
				Status: resultSuccess},
			{AccountId: types.NewAccountIdFromEntityId(stakingRewardAccountId), Amount: &types.HbarAmount{Value: -100},
				Type: types.OperationTypeCryptoTransfer, Status: resultSuccess},
		},
	}

	return []*types.Transaction{
		expectedTransaction1, expectedTransaction2, expectedTransaction3, expectedTransaction4,
		expectedTransaction5, expectedTransaction6, expectedTransaction7, expectedTransaction8,
		expectedTransaction9,
	}
}

func getFungibleTokenAmount(amount, decimals int64, tokenId domain.EntityId) *types.TokenAmount {
	return &types.TokenAmount{
		Decimals: decimals,
		TokenId:  tokenId,
		Type:     domain.TokenTypeFungibleCommon,
		Value:    amount,
	}
}

func getNftTokenAmount(amount, serialNumber int64, tokenId domain.EntityId) *types.TokenAmount {
	return &types.TokenAmount{
		SerialNumbers: []int64{serialNumber},
		TokenId:       tokenId,
		Type:          domain.TokenTypeNonFungibleUnique,
		Value:         amount,
	}
}
