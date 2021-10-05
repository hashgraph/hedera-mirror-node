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

package services

import (
	"context"

	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/tools"
)

// blockAPIService implements the server.BlockAPIServicer interface.
type blockAPIService struct {
	BaseService
}

// NewBlockAPIService creates a new instance of a blockAPIService.
func NewBlockAPIService(baseService BaseService) server.BlockAPIServicer {
	return &blockAPIService{BaseService: baseService}
}

// Block implements the /block endpoint.
func (s *blockAPIService) Block(
	ctx context.Context,
	request *rTypes.BlockRequest,
) (*rTypes.BlockResponse, *rTypes.Error) {
	block, err := s.RetrieveBlock(ctx, request.BlockIdentifier)
	if err != nil {
		return nil, err
	}

	if block.Transactions, err = s.FindBetween(ctx, block.ConsensusStartNanos, block.ConsensusEndNanos); err != nil {
		return nil, err
	}

	return &rTypes.BlockResponse{Block: block.ToRosetta()}, nil
}

// BlockTransaction implements the /block/transaction endpoint.
func (s *blockAPIService) BlockTransaction(
	ctx context.Context,
	request *rTypes.BlockTransactionRequest,
) (*rTypes.BlockTransactionResponse, *rTypes.Error) {
	h := tools.SafeRemoveHexPrefix(request.BlockIdentifier.Hash)
	block, err := s.FindByIdentifier(ctx, request.BlockIdentifier.Index, h)
	if err != nil {
		return nil, err
	}

	transaction, err := s.FindByHashInBlock(
		ctx,
		request.TransactionIdentifier.Hash,
		block.ConsensusStartNanos,
		block.ConsensusEndNanos,
	)
	if err != nil {
		return nil, err
	}

	return &rTypes.BlockTransactionResponse{Transaction: transaction.ToRosetta()}, nil
}
