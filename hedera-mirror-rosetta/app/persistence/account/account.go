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

package account

import (
	"database/sql"
	"encoding/json"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"gorm.io/gorm"
)

const (
	balanceChangeBetween string = `select
                                    coalesce((
                                      select sum(amount::bigint) from crypto_transfer
                                      where
                                        consensus_timestamp > @start and
                                        consensus_timestamp <= @end and
                                        entity_id = @account_id
                                    ), 0) as value,
                                    coalesce((
                                      select json_agg(change)
                                      from (
                                        select json_build_object(
                                            'token_id', tt.token_id,
                                            'decimals', t.decimals,
                                            'value', sum(tt.amount::bigint)
                                        ) change
                                        from token_transfer tt
                                        join token t
                                          on t.token_id = tt.token_id
                                        where
                                          consensus_timestamp > @start and
                                          consensus_timestamp <= @end and
                                          account_id = @account_id
                                        group by tt.account_id, tt.token_id, t.decimals
                                      ) token_change
                                    ), '[]') as token_values`

	latestBalanceBeforeConsensus string = `with abm as (
                                             select max(consensus_timestamp)
                                             from account_balance_file where consensus_timestamp <= @timestamp
                                           )
                                           select
                                             abm.max consensus_timestamp,
                                             coalesce(ab.balance, 0) balance,
                                             coalesce((
                                               select json_agg(json_build_object(
                                                 'token_id', tb.token_id,
                                                 'decimals', t.decimals,
                                                 'value', tb.balance
                                               ))
                                               from token_balance tb
                                               join token t
                                                 on t.token_id = tb.token_id
                                               where tb.consensus_timestamp = abm.max and tb.account_id = @account_id
                                             ), '[]') token_balances
                                           from abm
                                           left join account_balance ab
                                             on ab.consensus_timestamp = abm.max and ab.account_id = @account_id`
)

type combinedAccountBalance struct {
	ConsensusTimestamp int64
	Balance            int64
	TokenBalances      string
}

type accountBalanceChange struct {
	Value       int64
	TokenValues string
}

// accountRepository struct that has connection to the Database
type accountRepository struct {
	dbClient *gorm.DB
}

// NewAccountRepository creates an instance of a accountRepository struct
func NewAccountRepository(dbClient *gorm.DB) repositories.AccountRepository {
	return &accountRepository{
		dbClient: dbClient,
	}
}

// RetrieveBalanceAtBlock returns the hbar balance and token balances of the account at a given block (
// provided by consensusEnd timestamp).
// balance = balanceAtLatestBalanceSnapshot + balanceChangeBetweenSnapshotAndBlock
func (ar *accountRepository) RetrieveBalanceAtBlock(
	addressStr string,
	consensusEnd int64,
) ([]types.Amount, *rTypes.Error) {
	accountId, err := types.AccountFromString(addressStr)
	if err != nil {
		return nil, err
	}

	snapshotTimestamp, hbarAmount, tokenAmountMap, err := ar.getLatestBalanceSnapshot(accountId.EncodedId, consensusEnd)
	if err != nil {
		return nil, err
	}

	hbarValue, tokenValues, err := ar.getBalanceChange(accountId.EncodedId, snapshotTimestamp, consensusEnd)
	if err != nil {
		return nil, err
	}

	hbarAmount.Value += hbarValue
	tokenAmounts := ar.getUpdatedTokenAmounts(tokenAmountMap, tokenValues)

	amounts := make([]types.Amount, 0, 1+len(tokenAmounts))
	amounts = append(amounts, hbarAmount)
	amounts = append(amounts, tokenAmounts...)

	return amounts, nil
}

func (ar *accountRepository) getLatestBalanceSnapshot(accountId, consensusEnd int64) (
	int64,
	*types.HbarAmount,
	map[int64]*types.TokenAmount,
	*rTypes.Error,
) {
	// gets the most recent balance at or before consensusEnd
	cb := &combinedAccountBalance{}
	result := ar.dbClient.Raw(
		latestBalanceBeforeConsensus,
		sql.Named("account_id", accountId),
		sql.Named("timestamp", consensusEnd),
	).
		First(cb)
	if result.Error != nil {
		return 0, nil, nil, hErrors.ErrDatabaseError
	}

	hbarAmount := types.HbarAmount{Value: cb.Balance}

	var tokenAmounts []*types.TokenAmount
	if err := json.Unmarshal([]byte(cb.TokenBalances), &tokenAmounts); err != nil {
		return 0, nil, nil, hErrors.ErrInvalidToken
	}

	tokenAmountMap := make(map[int64]*types.TokenAmount, len(tokenAmounts))
	for _, tokenAmount := range tokenAmounts {
		tokenAmountMap[tokenAmount.TokenId.EncodedId] = tokenAmount
	}

	return cb.ConsensusTimestamp, &hbarAmount, tokenAmountMap, nil
}

func (ar *accountRepository) getBalanceChange(accountId, consensusStart, consensusEnd int64) (
	int64,
	[]*types.TokenAmount,
	*rTypes.Error,
) {
	change := &accountBalanceChange{}
	// gets the balance change from the Balance snapshot until the target block
	result := ar.dbClient.Raw(
		balanceChangeBetween,
		sql.Named("account_id", accountId),
		sql.Named("start", consensusStart),
		sql.Named("end", consensusEnd),
	).
		First(change)
	if result.Error != nil {
		return 0, nil, hErrors.ErrDatabaseError
	}

	var tokenValues []*types.TokenAmount
	if err := json.Unmarshal([]byte(change.TokenValues), &tokenValues); err != nil {
		return 0, nil, hErrors.ErrInvalidToken
	}

	return change.Value, tokenValues, nil
}

func (ar *accountRepository) getUpdatedTokenAmounts(
	tokenAmountMap map[int64]*types.TokenAmount,
	tokenValues []*types.TokenAmount,
) []types.Amount {
	for _, tokenValue := range tokenValues {
		encodedId := tokenValue.TokenId.EncodedId
		if _, ok := tokenAmountMap[encodedId]; ok {
			tokenAmountMap[encodedId].Value += tokenValue.Value
		} else {
			tokenAmountMap[encodedId] = tokenValue
		}
	}

	amounts := make([]types.Amount, 0, len(tokenAmountMap))
	for _, tokenAmount := range tokenAmountMap {
		amounts = append(amounts, tokenAmount)
	}

	return amounts
}
