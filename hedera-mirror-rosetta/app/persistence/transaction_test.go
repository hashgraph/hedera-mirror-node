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
	"testing"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

const (
	consensusStart int64 = 1000
	consensusEnd   int64 = 1100
	resultSuccess        = "SUCCESS"
)

var (
	firstAccount, _          = types.NewAccountFromEncodedID(12345)
	secondAccount, _         = types.NewAccountFromEncodedID(54321)
	nodeAccount, _           = types.NewAccountFromEncodedID(3)
	treasuryAccount, _       = types.NewAccountFromEncodedID(98)
	tokenId1                 = domain.MustDecodeEntityId(25636)
	tokenId2                 = domain.MustDecodeEntityId(26700)
	tokenId3                 = domain.MustDecodeEntityId(26750) // nft
	tokenDecimals      int64 = 10
	tokenInitialSupply int64 = 50000
)

func TestShouldSuccessReturnTransactionTypesTableName(t *testing.T) {
	assert.Equal(t, tableNameTransactionTypes, transactionType{}.TableName())
}

func TestShouldSuccessReturnTransactionResultsTableName(t *testing.T) {
	assert.Equal(t, tableNameTransactionResults, transactionResult{}.TableName())
}

func TestTransactionGetHashString(t *testing.T) {
	tx := transaction{Hash: []byte{1, 2, 3, 0xaa, 0xff}}
	assert.Equal(t, "0x010203aaff", tx.getHashString())
}

func TestHbarTransferGetAccount(t *testing.T) {
	hbarTransfer := hbarTransfer{AccountId: firstAccount.EntityId}
	assert.Equal(t, firstAccount.EntityId, hbarTransfer.getAccountId())
}

func TestHbarTransferGetAmount(t *testing.T) {
	hbarTransfer := hbarTransfer{Amount: 10}
	assert.Equal(t, &types.HbarAmount{Value: 10}, hbarTransfer.getAmount())
}

func TestSingleNftTransferGetAccount(t *testing.T) {
	singleNftTransfer := singleNftTransfer{accountId: firstAccount.EntityId}
	assert.Equal(t, firstAccount.EntityId, singleNftTransfer.getAccountId())
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
				accountId:    firstAccount.EntityId,
				receiver:     tt.receiver,
				serialNumber: 1,
				tokenId:      tokenId,
			}

			expected := &types.TokenAmount{
				SerialNumbers: []int64{1},
				TokenId:       tokenId,
				Type:          domain.TokenTypeNonFungibleUnique,
				Value:         tt.amount,
			}

			assert.Equal(t, expected, singleNftTransfer.getAmount())
		})
	}
}

func TestTokenTransferGetAccount(t *testing.T) {
	tokenTransfer := tokenTransfer{AccountId: firstAccount.EntityId}
	assert.Equal(t, firstAccount.EntityId, tokenTransfer.getAccountId())
}

func TestTokenTransferGetAmount(t *testing.T) {
	tokenTransfer := tokenTransfer{Amount: 10, Decimals: 3, TokenId: tokenId1, Type: domain.TokenTypeFungibleCommon}
	assert.Equal(t, getFungibleTokenAmount(10, 3, tokenId1), tokenTransfer.getAmount())
}

// func TestTokenGetAmount(t *testing.T) {
// 	token := token{Decimals: 5, TokenId: tokenId2}
// 	assert.Equal(t, &types.TokenAmount{Decimals: 5, TokenId: tokenId2}, token.getAmount())
// }

func TestShouldFailConstructAccount(t *testing.T) {
	data := int64(-1)
	expected := errors.ErrInternalServerError

	result, err := constructAccount(data)

	assert.Equal(t, types.Account{}, result)
	assert.Equal(t, expected, err)
}

func TestShouldSuccessConstructAccount(t *testing.T) {
	// given
	data := int64(5)
	expected, _ := types.NewAccountFromEncodedID(5)

	// when
	result, err := constructAccount(data)

	// then
	assert.Nil(t, err)
	assert.Equal(t, expected, result)
}

func assertOperationIndexes(t *testing.T, operations []*types.Operation) {
	makeRange := func(len int) []int64 {
		result := make([]int64, len)
		for i := range result {
			result[i] = int64(i)
		}
		return result
	}

	expected := makeRange(len(operations))
	actual := make([]int64, len(operations))
	for i, operation := range operations {
		actual[i] = operation.Index
		// side effect, clear operation's index
		operation.Index = 0
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

func (suite *transactionRepositorySuite) TestTypes() {
	t := NewTransactionRepository(dbClient)
	actual, err := t.Types(defaultContext)
	assert.Nil(suite.T(), err)
	assert.NotEmpty(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestTypesDbConnectionError() {
	t := NewTransactionRepository(invalidDbClient)
	actual, err := t.Types(defaultContext)
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestResults() {
	t := NewTransactionRepository(dbClient)
	actual, err := t.Results(defaultContext)
	assert.Nil(suite.T(), err)
	assert.NotEmpty(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestResultsDbConnectionError() {
	t := NewTransactionRepository(invalidDbClient)
	actual, err := t.Results(defaultContext)
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestTypesAsArray() {
	t := NewTransactionRepository(dbClient)
	actual, err := t.TypesAsArray(defaultContext)
	assert.Nil(suite.T(), err)
	assert.NotEmpty(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestTypesAsArrayDbConnectionError() {
	t := NewTransactionRepository(invalidDbClient)
	actual, err := t.TypesAsArray(defaultContext)
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
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

	// successful crypto transfer transaction
	consensusTimestamp = consensusStart + 1
	validStartNs = consensusStart - 10
	cryptoTransfers := []domain.CryptoTransfer{
		{Amount: -150, ConsensusTimestamp: consensusTimestamp, EntityId: firstAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 135, ConsensusTimestamp: consensusTimestamp, EntityId: secondAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: treasuryAccount.EntityId, PayerAccountId: firstAccount.EntityId},
	}
	nonFeeTransfers := []domain.NonFeeTransfer{
		{Amount: -135, ConsensusTimestamp: consensusTimestamp, EntityId: firstAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 135, ConsensusTimestamp: consensusTimestamp, EntityId: secondAccount.EntityId, PayerAccountId: firstAccount.EntityId},
	}
	addTransaction(dbClient, consensusTimestamp, nil, &nodeAccount.EntityId, firstAccount.EntityId, 22,
		[]byte{0x1, 0x2, 0x3}, domain.TransactionTypeCryptoTransfer, validStartNs, cryptoTransfers, nonFeeTransfers,
		nil, nil)

	// duplicate transaction
	consensusTimestamp += 1
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -15, ConsensusTimestamp: consensusTimestamp, EntityId: firstAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: treasuryAccount.EntityId, PayerAccountId: firstAccount.EntityId},
	}
	addTransaction(dbClient, consensusTimestamp, nil, &nodeAccount.EntityId, firstAccount.EntityId, 11,
		[]byte{0x1, 0x2, 0x3}, domain.TransactionTypeCryptoTransfer, validStartNs, cryptoTransfers, nil, nil, nil)
	operationType := types.OperationTypeCryptoTransfer
	operations1 := []*types.Operation{
		{Account: firstAccount, Amount: &types.HbarAmount{Value: -135}, Type: operationType, Status: resultSuccess},
		{Account: secondAccount, Amount: &types.HbarAmount{Value: 135}, Type: operationType, Status: resultSuccess},
		{Account: firstAccount, Amount: &types.HbarAmount{Value: -15}, Type: operationType, Status: resultSuccess},
		{Account: nodeAccount, Amount: &types.HbarAmount{Value: 5}, Type: operationType, Status: resultSuccess},
		{Account: treasuryAccount, Amount: &types.HbarAmount{Value: 10}, Type: operationType, Status: resultSuccess},
		{Account: firstAccount, Amount: &types.HbarAmount{Value: -15}, Type: operationType, Status: resultSuccess},
		{Account: nodeAccount, Amount: &types.HbarAmount{Value: 5}, Type: operationType, Status: resultSuccess},
		{Account: treasuryAccount, Amount: &types.HbarAmount{Value: 10}, Type: operationType, Status: resultSuccess},
	}
	expectedTransaction1 := &types.Transaction{Hash: "0x010203", Operations: operations1}

	// a successful crypto transfer + token transfer transaction
	tick(1)
	if createTokenEntity {
		token1 = &domain.Token{
			TokenId:           tokenId1,
			Decimals:          tokenDecimals,
			InitialSupply:     tokenInitialSupply,
			SupplyType:        domain.TokenSupplyTypeInfinite,
			TreasuryAccountId: treasuryAccount.EntityId,
			Type:              domain.TokenTypeFungibleCommon,
		}
		db.CreateDbRecords(dbClient, token1)
	}

	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -230, ConsensusTimestamp: consensusTimestamp, EntityId: firstAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 215, ConsensusTimestamp: consensusTimestamp, EntityId: secondAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: treasuryAccount.EntityId, PayerAccountId: firstAccount.EntityId},
	}
	nonFeeTransfers = []domain.NonFeeTransfer{
		{Amount: -215, ConsensusTimestamp: consensusTimestamp, EntityId: firstAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 215, ConsensusTimestamp: consensusTimestamp, EntityId: secondAccount.EntityId, PayerAccountId: firstAccount.EntityId},
	}
	tokenTransfers := []domain.TokenTransfer{
		{AccountId: firstAccount.EntityId, Amount: -160, ConsensusTimestamp: consensusTimestamp, TokenId: tokenId1, PayerAccountId: firstAccount.EntityId},
		{AccountId: secondAccount.EntityId, Amount: 160, ConsensusTimestamp: consensusTimestamp, TokenId: tokenId1, PayerAccountId: firstAccount.EntityId},
	}
	addTransaction(dbClient, consensusTimestamp, nil, &nodeAccount.EntityId, firstAccount.EntityId, 22,
		[]byte{0xa, 0xb, 0xc}, domain.TransactionTypeCryptoTransfer, validStartNs, cryptoTransfers, nonFeeTransfers,
		tokenTransfers, nil)
	operations2 := []*types.Operation{
		{Account: firstAccount, Amount: &types.HbarAmount{Value: -215}, Type: operationType, Status: resultSuccess},
		{Account: secondAccount, Amount: &types.HbarAmount{Value: 215}, Type: operationType, Status: resultSuccess},
		{Account: firstAccount, Amount: &types.HbarAmount{Value: -15}, Type: operationType, Status: resultSuccess},
		{Account: nodeAccount, Amount: &types.HbarAmount{Value: 5}, Type: operationType, Status: resultSuccess},
		{Account: treasuryAccount, Amount: &types.HbarAmount{Value: 10}, Type: operationType, Status: resultSuccess},
	}
	if createTokenEntity {
		operations2 = append(
			operations2,
			&types.Operation{
				Account: firstAccount,
				Amount:  getFungibleTokenAmount(-160, tokenDecimals, tokenId1),
				Type:    operationType,
				Status:  resultSuccess,
			},
			&types.Operation{
				Account: secondAccount,
				Amount:  getFungibleTokenAmount(160, tokenDecimals, tokenId1),
				Type:    operationType,
				Status:  resultSuccess,
			},
		)
	}
	expectedTransaction2 := &types.Transaction{Hash: "0x0a0b0c", Operations: operations2}

	// token create transaction
	token2 := &domain.Token{
		TokenId:           tokenId2,
		Decimals:          tokenDecimals,
		InitialSupply:     tokenInitialSupply,
		SupplyType:        domain.TokenSupplyTypeInfinite,
		TreasuryAccountId: firstAccount.EntityId,
		Type:              domain.TokenTypeFungibleCommon,
	}
	db.CreateDbRecords(dbClient, token2)
	// add tokencreate transaction
	tick(1)
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -15, ConsensusTimestamp: consensusTimestamp, EntityId: firstAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: treasuryAccount.EntityId, PayerAccountId: firstAccount.EntityId},
	}
	tokenTransfers = []domain.TokenTransfer{
		{AccountId: firstAccount.EntityId, Amount: tokenInitialSupply, ConsensusTimestamp: consensusTimestamp, TokenId: tokenId2, PayerAccountId: firstAccount.EntityId},
	}
	addTransaction(dbClient, consensusTimestamp, &tokenId2, &nodeAccount.EntityId, firstAccount.EntityId, 22,
		[]byte{0xaa, 0xcc, 0xdd}, domain.TransactionTypeTokenCreation, validStartNs, cryptoTransfers, nil,
		tokenTransfers, nil)
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
		Hash: "0xaaccdd",
		Operations: []*types.Operation{
			{Account: firstAccount, Amount: &types.HbarAmount{Value: -15}, Type: operationType, Status: resultSuccess},
			{Account: nodeAccount, Amount: &types.HbarAmount{Value: 5}, Type: operationType, Status: resultSuccess},
			{Account: treasuryAccount, Amount: &types.HbarAmount{Value: 10}, Type: operationType, Status: resultSuccess},
			{Account: firstAccount, Type: operationType, Status: resultSuccess, Metadata: metadata},
			{
				Account: firstAccount,
				Amount:  getFungibleTokenAmount(tokenInitialSupply, tokenDecimals, tokenId2),
				Type:    operationType,
				Status:  resultSuccess,
			},
		},
	}

	// nft create
	token3 := &domain.Token{
		TokenId:           tokenId3,
		SupplyType:        domain.TokenSupplyTypeFinite,
		TreasuryAccountId: firstAccount.EntityId,
		Type:              domain.TokenTypeNonFungibleUnique,
	}
	db.CreateDbRecords(dbClient, token3)
	// add tokencreate transaction
	tick(1)
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -15, ConsensusTimestamp: consensusTimestamp, EntityId: firstAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: treasuryAccount.EntityId, PayerAccountId: firstAccount.EntityId},
	}
	addTransaction(dbClient, consensusTimestamp, &tokenId3, &nodeAccount.EntityId, firstAccount.EntityId, 22,
		[]byte{0xaa, 0x11, 0x22}, domain.TransactionTypeTokenCreation, validStartNs, cryptoTransfers, nil, nil, nil)
	metadata = map[string]interface{}{
		"currency": &rTypes.Currency{
			Symbol:   tokenId3.String(),
			Metadata: map[string]interface{}{types.MetadataKeyType: domain.TokenTypeNonFungibleUnique},
		},
		"freeze_default": false,
		"initial_supply": int64(0),
	}
	expectedTransaction4 := &types.Transaction{
		Hash: "0xaa1122",
		Operations: []*types.Operation{
			{Account: firstAccount, Amount: &types.HbarAmount{Value: -15}, Type: operationType, Status: resultSuccess},
			{Account: nodeAccount, Amount: &types.HbarAmount{Value: 5}, Type: operationType, Status: resultSuccess},
			{Account: treasuryAccount, Amount: &types.HbarAmount{Value: 10}, Type: operationType, Status: resultSuccess},
			{Account: firstAccount, Type: operationType, Status: resultSuccess, Metadata: metadata},
		},
	}

	// nft mint
	tick(1)
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -15, ConsensusTimestamp: consensusTimestamp, EntityId: firstAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: treasuryAccount.EntityId, PayerAccountId: firstAccount.EntityId},
	}
	nftTransfers := []domain.NftTransfer{
		{consensusTimestamp, firstAccount.EntityId, &firstAccount.EntityId, nil, 1, tokenId3},
		{consensusTimestamp, firstAccount.EntityId, &firstAccount.EntityId, nil, 2, tokenId3},
		{consensusTimestamp, firstAccount.EntityId, &firstAccount.EntityId, nil, 3, tokenId3},
		{consensusTimestamp, firstAccount.EntityId, &firstAccount.EntityId, nil, 4, tokenId3},
	}
	addTransaction(dbClient, consensusTimestamp, &tokenId3, &nodeAccount.EntityId, firstAccount.EntityId, 22,
		[]byte{0xaa, 0x11, 0x33}, domain.TransactionTypeTokenMint, validStartNs, cryptoTransfers, nil, nil,
		nftTransfers)
	operationType = types.OperationTypeTokenMint
	expectedTransaction5 := &types.Transaction{
		Hash: "0xaa1133",
		Operations: []*types.Operation{
			{Account: firstAccount, Amount: &types.HbarAmount{Value: -15}, Type: operationType, Status: resultSuccess},
			{Account: nodeAccount, Amount: &types.HbarAmount{Value: 5}, Type: operationType, Status: resultSuccess},
			{Account: treasuryAccount, Amount: &types.HbarAmount{Value: 10}, Type: operationType, Status: resultSuccess},
			{Account: firstAccount, Amount: getNftTokenAmount(1, 1, tokenId3), Type: operationType, Status: resultSuccess},
			{Account: firstAccount, Amount: getNftTokenAmount(1, 2, tokenId3), Type: operationType, Status: resultSuccess},
			{Account: firstAccount, Amount: getNftTokenAmount(1, 3, tokenId3), Type: operationType, Status: resultSuccess},
			{Account: firstAccount, Amount: getNftTokenAmount(1, 4, tokenId3), Type: operationType, Status: resultSuccess},
		},
	}

	// nft transfer
	tick(1)
	cryptoTransfers = []domain.CryptoTransfer{
		{Amount: -15, ConsensusTimestamp: consensusTimestamp, EntityId: firstAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeAccount.EntityId, PayerAccountId: firstAccount.EntityId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: treasuryAccount.EntityId, PayerAccountId: firstAccount.EntityId},
	}
	nftTransfers = []domain.NftTransfer{
		{consensusTimestamp, firstAccount.EntityId, &secondAccount.EntityId, &firstAccount.EntityId, 1, tokenId3},
	}
	addTransaction(dbClient, consensusTimestamp, &firstAccount.EntityId, &nodeAccount.EntityId, firstAccount.EntityId,
		22, []byte{0xaa, 0x11, 0x66}, domain.TransactionTypeCryptoTransfer, validStartNs, cryptoTransfers, nil,
		nil, nftTransfers)
	operationType = types.OperationTypeCryptoTransfer
	expectedTransaction6 := &types.Transaction{
		Hash: "0xaa1166",
		Operations: []*types.Operation{
			{Account: firstAccount, Amount: &types.HbarAmount{Value: -15}, Type: operationType, Status: resultSuccess},
			{Account: nodeAccount, Amount: &types.HbarAmount{Value: 5}, Type: operationType, Status: resultSuccess},
			{Account: treasuryAccount, Amount: &types.HbarAmount{Value: 10}, Type: operationType, Status: resultSuccess},
			{Account: firstAccount, Amount: getNftTokenAmount(-1, 1, tokenId3), Type: operationType, Status: resultSuccess},
			{Account: secondAccount, Amount: getNftTokenAmount(1, 1, tokenId3), Type: operationType, Status: resultSuccess},
		},
	}

	return []*types.Transaction{
		expectedTransaction1, expectedTransaction2, expectedTransaction3,
		expectedTransaction4, expectedTransaction5, expectedTransaction6,
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
