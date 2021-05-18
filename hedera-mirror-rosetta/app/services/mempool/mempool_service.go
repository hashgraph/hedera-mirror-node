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

package mempool

import (
	"context"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
)

// MempoolAPIService implements the server.MempoolAPIServicer
type MempoolAPIService struct{}

// NewMempoolAPIService creates a new instance of a MempoolAPIService
func NewMempoolAPIService() *MempoolAPIService {
	return &MempoolAPIService{}
}

// Mempool implements the /mempool endpoint
func (m *MempoolAPIService) Mempool(
	ctx context.Context,
	request *rTypes.NetworkRequest,
) (*rTypes.MempoolResponse, *rTypes.Error) {
	return &rTypes.MempoolResponse{
		TransactionIdentifiers: []*rTypes.TransactionIdentifier{},
	}, nil
}

// Mempool implements the /mempool/transaction endpoint
func (m *MempoolAPIService) MempoolTransaction(
	ctx context.Context,
	request *rTypes.MempoolTransactionRequest,
) (*rTypes.MempoolTransactionResponse, *rTypes.Error) {
	return nil, errors.ErrTransactionNotFound
}
