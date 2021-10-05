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

package interfaces

import (
	"context"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
)

// AccountRepository Interface that all AccountRepository structs must implement
type AccountRepository interface {

	// RetrieveBalanceAtBlock returns the hbar balance and token balances of the account at a given block (
	// provided by consensusEnd timestamp).
	// balance = balanceAtLatestBalanceSnapshot + balanceChangeBetweenSnapshotAndBlock
	RetrieveBalanceAtBlock(ctx context.Context, accountId, consensusEnd int64) ([]types.Amount, *rTypes.Error)

	// RetrieveEverOwnedTokensByBlockAfter returns the tokens the account has ever owned by the end of the block after
	// consensusEnd
	RetrieveEverOwnedTokensByBlockAfter(ctx context.Context, accountId, consensusEnd int64) (
		[]domain.Token,
		*rTypes.Error,
	)
}
