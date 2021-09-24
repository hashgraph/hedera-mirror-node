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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
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
	unsupportedOperations = []*types.Operation{{Type: config.OperationTypeTokenCreate}}
	signers               = []hedera.AccountID{payerId}
)

func TestCompositeTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(compositeTransactionConstructorSuite))
}

type compositeTransactionConstructorSuite struct {
	suite.Suite
	constructor     TransactionConstructor
	mockConstructor *mocks.MockTransactionConstructor
}

func (suite *compositeTransactionConstructorSuite) SetupTest() {
	mockConstructor := &mocks.MockTransactionConstructor{}
	constructor := &compositeTransactionConstructor{
		constructorsByOperationType:   map[string]transactionConstructorWithType{},
		constructorsByTransactionType: map[string]transactionConstructorWithType{},
	}
	constructor.addConstructor(mockConstructor)

	suite.constructor = constructor
	suite.mockConstructor = mockConstructor
}

func (suite *compositeTransactionConstructorSuite) TestNewTransactionConstructor() {
	h := NewTransactionConstructor(&mocks.MockTokenRepository{})
	assert.NotNil(suite.T(), h)
}

func (suite *compositeTransactionConstructorSuite) TestNewTransactionConstructorNilRepo() {
	h := NewTransactionConstructor(nil)
	assert.NotNil(suite.T(), h)
}

func (suite *compositeTransactionConstructorSuite) TestConstruct() {
	// given
	suite.mockConstructor.
		On("Construct", defaultContext, nodeAccountId, cryptoTransferOperations, int64(0)).
		Return(cryptoTransferTransaction, signers, mocks.NilError)

	// when
	actualTx, actualSigners, err := suite.constructor.Construct(
		defaultContext,
		nodeAccountId,
		cryptoTransferOperations,
		0,
	)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), cryptoTransferTransaction, actualTx)
	assert.Equal(suite.T(), signers, actualSigners)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestConstructFail() {
	// given
	suite.mockConstructor.
		On("Construct", defaultContext, nodeAccountId, cryptoTransferOperations, int64(0)).
		Return(mocks.NilHederaTransaction, mocks.NilSigners, errors.ErrInternalServerError)

	// when
	actualTx, actualSigners, err := suite.constructor.Construct(
		defaultContext,
		nodeAccountId,
		cryptoTransferOperations,
		0,
	)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualTx)
	assert.Nil(suite.T(), actualSigners)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestConstructEmptyOperations() {
	// given

	// when
	actualTx, actualSigners, err := suite.constructor.Construct(
		defaultContext,
		nodeAccountId,
		[]*types.Operation{},
		0,
	)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualTx)
	assert.Nil(suite.T(), actualSigners)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestConstructUnsupportedOperations() {
	// given

	// when
	actualTx, actualSigners, err := suite.constructor.Construct(
		defaultContext,
		nodeAccountId,
		unsupportedOperations,
		0,
	)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualTx)
	assert.Nil(suite.T(), actualSigners)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestConstructMixedOperations() {
	// given

	// when
	actualTx, actualSigners, err := suite.constructor.Construct(
		defaultContext,
		nodeAccountId,
		mixedOperations,
		0,
	)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualTx)
	assert.Nil(suite.T(), actualSigners)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestParse() {
	// given
	suite.mockConstructor.
		On("Parse", defaultContext, cryptoTransferTransaction).
		Return(cryptoTransferOperations, signers, mocks.NilError)

	// when
	actualOperations, actualSigner, err := suite.constructor.Parse(defaultContext, cryptoTransferTransaction)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), cryptoTransferOperations, actualOperations)
	assert.Equal(suite.T(), signers, actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestParseFail() {
	// given
	suite.mockConstructor.
		On("Parse", defaultContext, cryptoTransferTransaction).
		Return(mocks.NilOperations, mocks.NilSigners, errors.ErrInternalServerError)

	// when
	actualOperations, actualSigner, err := suite.constructor.Parse(defaultContext, cryptoTransferTransaction)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualOperations)
	assert.Nil(suite.T(), actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestParseUnsupportedTransaction() {
	// given

	// when
	actualOperations, actualSigner, err := suite.constructor.Parse(defaultContext, tokenCreateTransaction)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualOperations)
	assert.Nil(suite.T(), actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestPreprocess() {
	// given
	suite.mockConstructor.
		On("Preprocess", defaultContext, cryptoTransferOperations).
		Return(signers, mocks.NilError)

	// when
	actualSigner, err := suite.constructor.Preprocess(defaultContext, cryptoTransferOperations)

	// then
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), signers, actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestPreprocessFail() {
	// given
	suite.mockConstructor.
		On("Preprocess", defaultContext, cryptoTransferOperations).
		Return(mocks.NilSigners, errors.ErrInternalServerError)

	// when
	actualSigner, err := suite.constructor.Preprocess(defaultContext, cryptoTransferOperations)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}

func (suite *compositeTransactionConstructorSuite) TestPreprocessUnsupportedOperations() {
	// given

	// when
	actualSigner, err := suite.constructor.Preprocess(defaultContext, unsupportedOperations)

	// then
	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), actualSigner)
	suite.mockConstructor.AssertExpectations(suite.T())
}
