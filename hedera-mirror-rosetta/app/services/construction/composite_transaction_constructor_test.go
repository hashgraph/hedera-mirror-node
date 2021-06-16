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

package construction

import (
	"testing"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks/repository"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
	"github.com/stretchr/testify/suite"
)

var (
	cryptoTransferTransaction = hedera.NewTransferTransaction()
	tokenCreateTransaction    = hedera.NewTokenCreateTransaction()
	cryptoTransferOperations  = []*types.Operation{{Type: config.OperationTypeCryptoTransfer}}
	mixedOperations           = []*types.Operation{
		{Type: config.OperationTypeCryptoTransfer},
		{Type: config.OperationTypeTokenCreate},
	}
	nilError              *types.Error
	nilOperations         []*types.Operation
	nilSigners            []hedera.AccountID
	nilTransaction        *hedera.TransferTransaction
	unsupportedOperations = []*types.Operation{{Type: config.OperationTypeTokenCreate}}
	signers               = []hedera.AccountID{payerId}
)

type mockTransactionConstructor struct {
	mock.Mock
}

func (m *mockTransactionConstructor) Construct(nodeAccountId hedera.AccountID, operations []*types.Operation) (
	ITransaction,
	[]hedera.AccountID,
	*types.Error,
) {
	args := m.Called(nodeAccountId, operations)
	return args.Get(0).(ITransaction), args.Get(1).([]hedera.AccountID), args.Get(2).(*types.Error)
}

func (m *mockTransactionConstructor) Parse(transaction ITransaction) (
	[]*types.Operation,
	[]hedera.AccountID,
	*types.Error,
) {
	args := m.Called(transaction)
	return args.Get(0).([]*types.Operation), args.Get(1).([]hedera.AccountID), args.Get(2).(*types.Error)
}

func (m *mockTransactionConstructor) Preprocess(operations []*types.Operation) ([]hedera.AccountID, *types.Error) {
	args := m.Called(operations)
	return args.Get(0).([]hedera.AccountID), args.Get(1).(*types.Error)
}

func (m *mockTransactionConstructor) GetOperationType() string {
	return config.OperationTypeCryptoTransfer
}

func (m *mockTransactionConstructor) GetSdkTransactionType() string {
	return "TransferTransaction"
}

func TestCompositeTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(compositeTransactionConstructorSuite))
}

type compositeTransactionConstructorSuite struct {
	suite.Suite
	constructor     TransactionConstructor
	mockConstructor *mockTransactionConstructor
}

func (suite *compositeTransactionConstructorSuite) SetupTest() {
	mockConstructor := &mockTransactionConstructor{}
	constructor := &compositeTransactionConstructor{
		constructorsByOperationType:   map[string]transactionConstructorWithType{},
		constructorsByTransactionType: map[string]transactionConstructorWithType{},
	}
	constructor.addConstructor(mockConstructor)

	suite.constructor = constructor
	suite.mockConstructor = mockConstructor
}

func (suite *compositeTransactionConstructorSuite) TestNewTransactionConstructor() {
	h := NewTransactionConstructor(&repository.MockTokenRepository{})
	assert.NotNil(suite.T(), h)
}

func (suite *compositeTransactionConstructorSuite) TestNewTransactionConstructorNilRepo() {
	h := NewTransactionConstructor(nil)
	assert.NotNil(suite.T(), h)
}

func (suite *compositeTransactionConstructorSuite) TestConstruct() {
	// given
	suite.mockConstructor.
		On("Construct", nodeAccountId, cryptoTransferOperations).
		Return(cryptoTransferTransaction, signers, nilError)

	// when
	actualTx, actualSigners, err := suite.constructor.Construct(nodeAccountId, cryptoTransferOperations)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), cryptoTransferTransaction, actualTx)
	assert.Equal(suite.T(), signers, actualSigners)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestConstructFail() {
	// given
	suite.mockConstructor.
		On("Construct", nodeAccountId, cryptoTransferOperations).
		Return(nilTransaction, nilSigners, errors.ErrInternalServerError)

	// when
	actualTx, actualSigners, err := suite.constructor.Construct(nodeAccountId, cryptoTransferOperations)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualTx)
	assert.Nil(suite.T(), actualSigners)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestConstructEmptyOperations() {
	// given

	// when
	actualTx, actualSigners, err := suite.constructor.Construct(nodeAccountId, []*types.Operation{})

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualTx)
	assert.Nil(suite.T(), actualSigners)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestConstructUnsupportedOperations() {
	// given

	// when
	actualTx, actualSigners, err := suite.constructor.Construct(nodeAccountId, unsupportedOperations)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualTx)
	assert.Nil(suite.T(), actualSigners)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestConstructMixedOperations() {
	// given

	// when
	actualTx, actualSigners, err := suite.constructor.Construct(nodeAccountId, mixedOperations)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualTx)
	assert.Nil(suite.T(), actualSigners)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestParse() {
	// given
	suite.mockConstructor.
		On("Parse", cryptoTransferTransaction).
		Return(cryptoTransferOperations, signers, nilError)

	// when
	actualOperations, actualSigner, err := suite.constructor.Parse(cryptoTransferTransaction)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), cryptoTransferOperations, actualOperations)
	assert.Equal(suite.T(), signers, actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestParseFail() {
	// given
	suite.mockConstructor.
		On("Parse", cryptoTransferTransaction).
		Return(nilOperations, nilSigners, errors.ErrInternalServerError)

	// when
	actualOperations, actualSigner, err := suite.constructor.Parse(cryptoTransferTransaction)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualOperations)
	assert.Nil(suite.T(), actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestParseUnsupportedTransaction() {
	// given

	// when
	actualOperations, actualSigner, err := suite.constructor.Parse(tokenCreateTransaction)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualOperations)
	assert.Nil(suite.T(), actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestPreprocess() {
	// given
	suite.mockConstructor.
		On("Preprocess", cryptoTransferOperations).
		Return(signers, nilError)

	// when
	actualSigner, err := suite.constructor.Preprocess(cryptoTransferOperations)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), signers, actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestPreprocessFail() {
	// given
	suite.mockConstructor.
		On("Preprocess", cryptoTransferOperations).
		Return(nilSigners, errors.ErrInternalServerError)

	// when
	actualSigner, err := suite.constructor.Preprocess(cryptoTransferOperations)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestPreprocessUnsupportedOperations() {
	// given

	// when
	actualSigner, err := suite.constructor.Preprocess(unsupportedOperations)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}
