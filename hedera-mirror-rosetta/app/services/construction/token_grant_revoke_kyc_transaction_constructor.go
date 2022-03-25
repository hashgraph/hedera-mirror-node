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

package construction

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

type tokenGrantRevokeKycTransactionConstructor struct {
	commonTransactionConstructor
}

func (t *tokenGrantRevokeKycTransactionConstructor) Construct(
	_ context.Context,
	operations types.OperationSlice,
) (interfaces.Transaction, []types.AccountId, *rTypes.Error) {
	payer, account, token, rErr := t.preprocess(operations)
	if rErr != nil {
		return nil, nil, rErr
	}

	var tx interfaces.Transaction
	if t.operationType == types.OperationTypeTokenGrantKyc {
		tx = hedera.NewTokenGrantKycTransaction().
			SetAccountID(account.ToSdkAccountId()).
			SetTokenID(*token)
	} else {
		tx = hedera.NewTokenRevokeKycTransaction().
			SetAccountID(account.ToSdkAccountId()).
			SetTokenID(*token)
	}

	return tx, []types.AccountId{*payer}, nil
}

func (t *tokenGrantRevokeKycTransactionConstructor) Parse(_ context.Context, transaction interfaces.Transaction) (
	types.OperationSlice,
	[]types.AccountId,
	*rTypes.Error,
) {
	return parseTokenFreezeKyc(t.operationType, transaction)
}

func (t *tokenGrantRevokeKycTransactionConstructor) Preprocess(_ context.Context, operations types.OperationSlice) (
	[]types.AccountId,
	*rTypes.Error,
) {
	payer, _, _, err := t.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return []types.AccountId{*payer}, nil
}

func (t *tokenGrantRevokeKycTransactionConstructor) preprocess(operations types.OperationSlice) (
	*types.AccountId,
	*types.AccountId,
	*hedera.TokenID,
	*rTypes.Error,
) {
	return preprocessTokenFreezeKyc(operations, t.GetOperationType(), t.validate)
}

func newTokenGrantKycTransactionConstructor() transactionConstructorWithType {
	return &tokenGrantRevokeKycTransactionConstructor{
		commonTransactionConstructor: newCommonTransactionConstructor(
			hedera.NewTokenGrantKycTransaction(),
			types.OperationTypeTokenGrantKyc,
		),
	}
}

func newTokenRevokeKycTransactionConstructor() transactionConstructorWithType {
	return &tokenGrantRevokeKycTransactionConstructor{
		commonTransactionConstructor: newCommonTransactionConstructor(
			hedera.NewTokenRevokeKycTransaction(),
			types.OperationTypeTokenRevokeKyc,
		),
	}
}
