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

package transaction

import (
	"testing"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	entityid "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	dbTypes "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/domain"
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
	tokenId1, _              = entityid.Decode(25636)
	tokenId2, _              = entityid.Decode(26700)
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
	hbarTransfer := hbarTransfer{AccountId: entityid.EntityId{EntityNum: 1, EncodedId: 1}}
	assert.Equal(t, types.Account{EntityId: entityid.EntityId{EntityNum: 1, EncodedId: 1}}, hbarTransfer.getAccount())
}

func TestHbarTransferGetAmount(t *testing.T) {
	hbarTransfer := hbarTransfer{Amount: 10}
	assert.Equal(t, &types.HbarAmount{Value: 10}, hbarTransfer.getAmount())
}

func TestTokenTransferGetAccount(t *testing.T) {
	tokenTransfer := tokenTransfer{AccountId: entityid.EntityId{EntityNum: 1, EncodedId: 1}}
	assert.Equal(t, types.Account{EntityId: entityid.EntityId{EntityNum: 1, EncodedId: 1}}, tokenTransfer.getAccount())
}

func TestTokenTransferGetAmount(t *testing.T) {
	tokenId := entityid.EntityId{EntityNum: 123, EncodedId: 123}
	tokenTransfer := tokenTransfer{Amount: 10, Decimals: 3, TokenId: tokenId}
	assert.Equal(t, &types.TokenAmount{Decimals: 3, Value: 10, TokenId: tokenId}, tokenTransfer.getAmount())
}

func TestTokenGetAmount(t *testing.T) {
	tokenId := entityid.EntityId{EntityNum: 123, EncodedId: 123}
	token := token{Decimals: 5, TokenId: tokenId}
	assert.Equal(t, &types.TokenAmount{Decimals: 5, TokenId: tokenId}, token.getAmount())
}

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
		assert.ElementsMatch(t, actualTx.Operations, expectedTx.Operations)
	}
}

func TestTransactionRepositorySuite(t *testing.T) {
	suite.Run(t, new(transactionRepositorySuite))
}

type transactionRepositorySuite struct {
	test.IntegrationTest
	suite.Suite
}

func (suite *transactionRepositorySuite) SetupSuite() {
	suite.Setup()
}

func (suite *transactionRepositorySuite) TearDownSuite() {
	suite.TearDown()
}

func (suite *transactionRepositorySuite) SetupTest() {
	suite.CleanupDb()
}

func (suite *transactionRepositorySuite) TestNewTransactionRepository() {
	t := NewTransactionRepository(suite.DbResource.GetGormDb())
	assert.NotNil(suite.T(), t)
}

func (suite *transactionRepositorySuite) TestTypes() {
	t := NewTransactionRepository(suite.DbResource.GetGormDb())
	actual, err := t.Types()
	assert.Nil(suite.T(), err)
	assert.NotEmpty(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestTypesDbConnectionError() {
	t := NewTransactionRepository(suite.InvalidDbClient)
	actual, err := t.Types()
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestResults() {
	t := NewTransactionRepository(suite.DbResource.GetGormDb())
	actual, err := t.Results()
	assert.Nil(suite.T(), err)
	assert.NotEmpty(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestResultsDbConnectionError() {
	t := NewTransactionRepository(suite.InvalidDbClient)
	actual, err := t.Results()
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestTypesAsArray() {
	t := NewTransactionRepository(suite.DbResource.GetGormDb())
	actual, err := t.TypesAsArray()
	assert.Nil(suite.T(), err)
	assert.NotEmpty(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestTypesAsArrayDbConnectionError() {
	t := NewTransactionRepository(suite.InvalidDbClient)
	actual, err := t.TypesAsArray()
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestFindBetween() {
	// given
	expected := suite.setupDb(true)
	t := NewTransactionRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := t.FindBetween(consensusStart, consensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assertTransactions(suite.T(), expected, actual)
}

func (suite *transactionRepositorySuite) TestFindBetweenNoTokenEntity() {
	// given
	expected := suite.setupDb(false)
	t := NewTransactionRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := t.FindBetween(consensusStart, consensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assertTransactions(suite.T(), expected, actual)
}

func (suite *transactionRepositorySuite) TestFindBetweenThrowsWhenStartAfterEnd() {
	// given
	t := NewTransactionRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := t.FindBetween(consensusStart, consensusStart-1)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestFindBetweenDbConnectionError() {
	// given
	t := NewTransactionRepository(suite.InvalidDbClient)

	// when
	actual, err := t.FindBetween(consensusStart, consensusEnd)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestFindByHashInBlock() {
	// given
	expected := suite.setupDb(true)
	t := NewTransactionRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := t.FindByHashInBlock(expected[0].Hash, consensusStart, consensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assertTransactions(suite.T(), []*types.Transaction{expected[0]}, []*types.Transaction{actual})
}

func (suite *transactionRepositorySuite) TestFindByHashInBlockNoTokenEntity() {
	// given
	expected := suite.setupDb(false)
	t := NewTransactionRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := t.FindByHashInBlock(expected[1].Hash, consensusStart, consensusEnd)

	// then
	assert.Nil(suite.T(), err)
	assertTransactions(suite.T(), []*types.Transaction{expected[1]}, []*types.Transaction{actual})
}

func (suite *transactionRepositorySuite) TestFindByHashInBlockThrowsInvalidHash() {
	// given
	t := NewTransactionRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := t.FindByHashInBlock("invalid hash", consensusStart, consensusEnd)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestFindByHashInBlockThrowsNotFound() {
	// given
	t := NewTransactionRepository(suite.DbResource.GetGormDb())

	// when
	actual, err := t.FindByHashInBlock("0x123456", consensusStart, consensusEnd)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) TestFindByHashInBlockDbConnectionError() {
	// given
	t := NewTransactionRepository(suite.InvalidDbClient)

	// when
	actual, err := t.FindByHashInBlock("0x123456", consensusStart, consensusEnd)

	// then
	assert.Equal(suite.T(), errors.ErrDatabaseError, err)
	assert.Nil(suite.T(), actual)
}

func (suite *transactionRepositorySuite) setupDb(createTokenEntity bool) []*types.Transaction {
	dbClient := suite.DbResource.GetGormDb()

	// successful crypto transfer transaction
	consensusTimestamp := consensusStart + 1
	validStartNs := consensusStart - 10
	cryptoTransfers := []dbTypes.CryptoTransfer{
		{Amount: -150, ConsensusTimestamp: consensusTimestamp, EntityId: firstAccount.EncodedId},
		{Amount: 135, ConsensusTimestamp: consensusTimestamp, EntityId: secondAccount.EncodedId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeAccount.EncodedId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: treasuryAccount.EncodedId},
	}
	nonFeeTransfers := []dbTypes.CryptoTransfer{
		{Amount: -135, ConsensusTimestamp: consensusTimestamp, EntityId: firstAccount.EncodedId},
		{Amount: 135, ConsensusTimestamp: consensusTimestamp, EntityId: secondAccount.EncodedId},
	}
	domain.AddTransaction(dbClient, consensusTimestamp, 0, nodeAccount.EncodedId, firstAccount.EncodedId, 22,
		[]byte{0x1, 0x2, 0x3}, 14, validStartNs, cryptoTransfers, nonFeeTransfers, nil)

	// duplicate transaction
	consensusTimestamp += 1
	cryptoTransfers = []dbTypes.CryptoTransfer{
		{Amount: -15, ConsensusTimestamp: consensusTimestamp, EntityId: firstAccount.EncodedId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeAccount.EncodedId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: treasuryAccount.EncodedId},
	}
	domain.AddTransaction(dbClient, consensusTimestamp, 0, nodeAccount.EncodedId, firstAccount.EncodedId, 11,
		[]byte{0x1, 0x2, 0x3}, 14, validStartNs, cryptoTransfers, nil, nil)
	operations1 := []*types.Operation{
		{Account: firstAccount, Amount: &types.HbarAmount{Value: -135}, Type: "CRYPTOTRANSFER", Status: resultSuccess},
		{Account: secondAccount, Amount: &types.HbarAmount{Value: 135}, Type: "CRYPTOTRANSFER", Status: resultSuccess},
		{Account: firstAccount, Amount: &types.HbarAmount{Value: -15}, Type: "CRYPTOTRANSFER", Status: resultSuccess},
		{Account: nodeAccount, Amount: &types.HbarAmount{Value: 5}, Type: "CRYPTOTRANSFER", Status: resultSuccess},
		{Account: treasuryAccount, Amount: &types.HbarAmount{Value: 10}, Type: "CRYPTOTRANSFER", Status: resultSuccess},
		{Account: firstAccount, Amount: &types.HbarAmount{Value: -15}, Type: "CRYPTOTRANSFER", Status: resultSuccess},
		{Account: nodeAccount, Amount: &types.HbarAmount{Value: 5}, Type: "CRYPTOTRANSFER", Status: resultSuccess},
		{Account: treasuryAccount, Amount: &types.HbarAmount{Value: 10}, Type: "CRYPTOTRANSFER", Status: resultSuccess},
	}
	expectedTransaction1 := &types.Transaction{Hash: "0x010203", Operations: operations1}

	// a successful crypto transfer + token transfer transaction
	consensusTimestamp += 1
	validStartNs += 2

	if createTokenEntity {
		domain.AddToken(dbClient, tokenId1.EncodedId, tokenDecimals, false, tokenInitialSupply, treasuryAccount.EncodedId)
	}

	cryptoTransfers = []dbTypes.CryptoTransfer{
		{Amount: -230, ConsensusTimestamp: consensusTimestamp, EntityId: firstAccount.EncodedId},
		{Amount: 215, ConsensusTimestamp: consensusTimestamp, EntityId: secondAccount.EncodedId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeAccount.EncodedId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: treasuryAccount.EncodedId},
	}
	nonFeeTransfers = []dbTypes.CryptoTransfer{
		{Amount: -215, ConsensusTimestamp: consensusTimestamp, EntityId: firstAccount.EncodedId},
		{Amount: 215, ConsensusTimestamp: consensusTimestamp, EntityId: secondAccount.EncodedId},
	}
	tokenTransfers := []dbTypes.TokenTransfer{
		{AccountId: firstAccount.EncodedId, Amount: -160, ConsensusTimestamp: consensusTimestamp, TokenId: tokenId1.EncodedId},
		{AccountId: secondAccount.EncodedId, Amount: 160, ConsensusTimestamp: consensusTimestamp, TokenId: tokenId1.EncodedId},
	}
	domain.AddTransaction(dbClient, consensusTimestamp, 0, nodeAccount.EncodedId, firstAccount.EncodedId, 22,
		[]byte{0xa, 0xb, 0xc}, 14, validStartNs, cryptoTransfers, nonFeeTransfers, tokenTransfers)
	operations2 := []*types.Operation{
		{Account: firstAccount, Amount: &types.HbarAmount{Value: -215}, Type: "CRYPTOTRANSFER", Status: resultSuccess},
		{Account: secondAccount, Amount: &types.HbarAmount{Value: 215}, Type: "CRYPTOTRANSFER", Status: resultSuccess},
		{Account: firstAccount, Amount: &types.HbarAmount{Value: -15}, Type: "CRYPTOTRANSFER", Status: resultSuccess},
		{Account: nodeAccount, Amount: &types.HbarAmount{Value: 5}, Type: "CRYPTOTRANSFER", Status: resultSuccess},
		{Account: treasuryAccount, Amount: &types.HbarAmount{Value: 10}, Type: "CRYPTOTRANSFER", Status: resultSuccess},
	}
	if createTokenEntity {
		operations2 = append(
			operations2,
			&types.Operation{
				Account: firstAccount,
				Amount:  &types.TokenAmount{Value: -160, Decimals: tokenDecimals, TokenId: tokenId1},
				Type:    "CRYPTOTRANSFER",
				Status:  resultSuccess,
			},
			&types.Operation{
				Account: secondAccount,
				Amount:  &types.TokenAmount{Value: 160, Decimals: tokenDecimals, TokenId: tokenId1},
				Type:    "CRYPTOTRANSFER",
				Status:  resultSuccess,
			},
		)
	}
	expectedTransaction2 := &types.Transaction{Hash: "0x0a0b0c", Operations: operations2}

	// token create transaction
	domain.AddToken(dbClient, tokenId2.EncodedId, tokenDecimals, false, tokenInitialSupply, firstAccount.EncodedId)
	// add tokencreate transaction
	consensusTimestamp += 1
	validStartNs += 1
	cryptoTransfers = []dbTypes.CryptoTransfer{
		{Amount: -15, ConsensusTimestamp: consensusTimestamp, EntityId: firstAccount.EncodedId},
		{Amount: 5, ConsensusTimestamp: consensusTimestamp, EntityId: nodeAccount.EncodedId},
		{Amount: 10, ConsensusTimestamp: consensusTimestamp, EntityId: treasuryAccount.EncodedId},
	}
	tokenTransfers = []dbTypes.TokenTransfer{
		{AccountId: firstAccount.EncodedId, Amount: tokenInitialSupply, ConsensusTimestamp: consensusTimestamp, TokenId: tokenId2.EncodedId},
	}
	domain.AddTransaction(dbClient, consensusTimestamp, tokenId2.EncodedId, nodeAccount.EncodedId, firstAccount.EncodedId, 22,
		[]byte{0xaa, 0xcc, 0xdd}, dbTypes.TransactionTypeTokenCreation, validStartNs, cryptoTransfers, nil, tokenTransfers)
	metadata := map[string]interface{}{
		"currency": &rTypes.Currency{
			Symbol:   tokenId2.String(),
			Decimals: int32(tokenDecimals),
		},
		"freeze_default": false,
		"initial_supply": tokenInitialSupply,
	}
	expectedTransaction3 := &types.Transaction{
		Hash: "0xaaccdd",
		Operations: []*types.Operation{
			{Account: firstAccount, Amount: &types.HbarAmount{Value: -15}, Type: "TOKENCREATION", Status: resultSuccess},
			{Account: nodeAccount, Amount: &types.HbarAmount{Value: 5}, Type: "TOKENCREATION", Status: resultSuccess},
			{Account: treasuryAccount, Amount: &types.HbarAmount{Value: 10}, Type: "TOKENCREATION", Status: resultSuccess},
			{Account: firstAccount, Type: "TOKENCREATION", Status: resultSuccess, Metadata: metadata},
			{
				Account: firstAccount,
				Amount:  &types.TokenAmount{Value: tokenInitialSupply, TokenId: tokenId2, Decimals: tokenDecimals},
				Type:    "TOKENCREATION",
				Status:  resultSuccess,
			},
		},
	}

	return []*types.Transaction{expectedTransaction1, expectedTransaction2, expectedTransaction3}
}
