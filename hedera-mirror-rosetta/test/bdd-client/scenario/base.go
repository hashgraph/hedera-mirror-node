/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
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
 */

package scenario

import (
	"context"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hiero-ledger/hiero-sdk-go/v2/sdk"
	log "github.com/sirupsen/logrus"
)

type baseFeature struct {
	transactionHash string
}

func (b *baseFeature) cleanup() {
	b.transactionHash = ""
}

func (b *baseFeature) findTransaction(ctx context.Context, operationType string) (*types.Transaction, error) {
	transaction, err := testClient.FindTransaction(ctx, b.transactionHash)
	if err != nil {
		log.Infof("Failed to find %s transaction with hash %s", operationType, b.transactionHash)
	}
	return transaction, err
}

func (b *baseFeature) submit(
	ctx context.Context,
	memo string,
	operations []*types.Operation,
	signers map[string]hiero.PrivateKey,
) (err error) {
	operationType := operations[0].Type
	b.transactionHash, err = testClient.Submit(ctx, memo, operations, signers)
	if err != nil {
		log.Errorf("Failed to submit %s transaction: %s", operationType, err)
	} else {
		log.Infof("Submitted %s transaction %s successfully", operationType, b.transactionHash)
	}
	return err
}
