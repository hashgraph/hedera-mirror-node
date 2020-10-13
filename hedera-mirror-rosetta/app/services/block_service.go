/*-
 * ‌
 * Hedera Mirror Node
 *
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
 * ‍
 */

package services

import (
	"context"
	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
)

// BlockAPIService implements the server.BlockAPIServicer interface.
type BlockAPIService struct {
	Commons
}

// NewBlockAPIService creates a new instance of a BlockAPIService.
func NewBlockAPIService(commons Commons) server.BlockAPIServicer {
	return &BlockAPIService{
		Commons: commons,
	}
}

// Block implements the /block endpoint.
func (s *BlockAPIService) Block(ctx context.Context, request *rTypes.BlockRequest) (*rTypes.BlockResponse, *rTypes.Error) {
	block, err := s.RetrieveBlock(request.BlockIdentifier)
	if err != nil {
		return nil, err
	}

	transactions, err := s.transactionRepo.FindBetween(block.ConsensusStartNanos, block.ConsensusEndNanos)
	if err != nil {
		return nil, err
	}

	block.Transactions = transactions
	rBlock := block.ToRosetta()
	return &rTypes.BlockResponse{
		Block: rBlock,
	}, nil
}

// BlockTransaction implements the /block/transaction endpoint.
func (s *BlockAPIService) BlockTransaction(
	ctx context.Context,
	request *rTypes.BlockTransactionRequest,
) (*rTypes.BlockTransactionResponse, *rTypes.Error) {
	h := hex.SafeRemoveHexPrefix(request.BlockIdentifier.Hash)
	block, err := s.blockRepo.FindByIdentifier(request.BlockIdentifier.Index, h)
	if err != nil {
		return nil, err
	}

	transaction, err := s.transactionRepo.FindByHashInBlock(request.TransactionIdentifier.Hash, block.ConsensusStartNanos, block.ConsensusEndNanos)
	if err != nil {
		return nil, err
	}
	rTransaction := transaction.ToRosetta()
	return &rTypes.BlockTransactionResponse{
		Transaction: rTransaction,
	}, nil
}
