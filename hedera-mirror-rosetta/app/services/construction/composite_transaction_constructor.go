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
	"reflect"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-sdk-go/v2"
	log "github.com/sirupsen/logrus"
)

type transactionConstructorWithType interface {
	TransactionConstructor
	GetOperationType() string
	GetSdkTransactionType() string
}

type compositeTransactionConstructor struct {
	constructorsByOperationType   map[string]transactionConstructorWithType
	constructorsByTransactionType map[string]transactionConstructorWithType
}

func (c *compositeTransactionConstructor) Construct(
	nodeAccountId hedera.AccountID,
	operations []*rTypes.Operation,
) (ITransaction, []hedera.AccountID, *rTypes.Error) {
	h, err := c.validate(operations)
	if err != nil {
		return nil, nil, err
	}

	return h.Construct(nodeAccountId, operations)
}

func (c *compositeTransactionConstructor) Parse(transaction ITransaction, signed bool) (
	[]*rTypes.Operation,
	[]hedera.AccountID,
	*rTypes.Error,
) {
	name := reflect.TypeOf(transaction).Elem().Name()
	h, ok := c.constructorsByTransactionType[name]
	if !ok {
		log.Errorf("No constructor to parse constructed transaction %s", name)
		return nil, nil, errors.ErrInternalServerError
	}

	return h.Parse(transaction, signed)
}

func (c *compositeTransactionConstructor) Preprocess(operations []*rTypes.Operation) ([]hedera.AccountID, *rTypes.Error) {
	h, err := c.validate(operations)
	if err != nil {
		return nil, err
	}

	return h.Preprocess(operations)
}

func (c *compositeTransactionConstructor) addConstructor(constructor transactionConstructorWithType) {
	c.constructorsByOperationType[constructor.GetOperationType()] = constructor
	c.constructorsByTransactionType[constructor.GetSdkTransactionType()] = constructor
}

func (c *compositeTransactionConstructor) validate(operations []*rTypes.Operation) (transactionConstructorWithType, *rTypes.Error) {
	if len(operations) == 0 {
		return nil, errors.ErrEmptyOperations
	}

	operationType := operations[0].Type
	for _, operation := range operations[1:] {
		if operation.Type != operationType {
			return nil, errors.ErrMultipleOperationTypesPresent
		}
	}

	h, ok := c.constructorsByOperationType[operationType]
	if !ok {
		log.Errorf("Operation type %s is not supported", operationType)
		return nil, errors.ErrOperationTypeUnsupported
	}

	return h, nil
}

func NewTransactionConstructor(tokenRepo repositories.TokenRepository) TransactionConstructor {
	c := &compositeTransactionConstructor{
		constructorsByOperationType:   make(map[string]transactionConstructorWithType),
		constructorsByTransactionType: make(map[string]transactionConstructorWithType),
	}

	c.addConstructor(newCryptoTransferTransactionConstructor(tokenRepo))
	c.addConstructor(newTokenCreateTransactionConstructor())

	if tokenRepo != nil {
		c.addConstructor(newTokenAssociateTransactionConstructor(tokenRepo))
		c.addConstructor(newTokenBurnTransactionConstructor(tokenRepo))
		c.addConstructor(newTokenDeleteTransactionConstructor(tokenRepo))
		c.addConstructor(newTokenDissociateTransactionConstructor(tokenRepo))
		c.addConstructor(newTokenFreezeTransactionConstructor(tokenRepo))
		c.addConstructor(newTokenGrantKycTransactionConstructor(tokenRepo))
		c.addConstructor(newTokenRevokeKycTransactionConstructor(tokenRepo))
		c.addConstructor(newTokenMintTransactionConstructor(tokenRepo))
		c.addConstructor(newTokenUnfreezeTransactionConstructor(tokenRepo))
		c.addConstructor(newTokenUpdateTransactionConstructor(tokenRepo))
		c.addConstructor(newTokenWipeTransactionConstructor(tokenRepo))
	}

	return c
}
