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

package persistence

import (
	"context"
	"database/sql"
	"encoding/json"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	types2 "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/types"
	log "github.com/sirupsen/logrus"
)

const (
	balanceChangeBetween = `select
                              coalesce((
                                select sum(amount) from crypto_transfer
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
                                      'type', t.type,
                                      'value', sum(tt.amount)
                                  ) change
                                  from token_transfer tt
                                  join token t
                                    on t.token_id = tt.token_id
                                  where
                                    consensus_timestamp > @start and
                                    consensus_timestamp <= @end and
                                    account_id = @account_id
                                  group by tt.account_id, tt.token_id, t.decimals, t.type
                                ) token_change
                              ), '[]') as token_values,
                              coalesce((
                                select json_agg(change)
                                from (
                                  select json_build_object(
                                    'token_id', token_id,
                                    'type', type,
                                    'value', sum(amount)
                                  ) change
                                  from (
                                    select
                                      t.token_id,
                                      t.type,
                                      case when nftt.receiver_account_id = @account_id then 1
                                        else -1
                                      end amount
                                    from nft_transfer nftt
                                    join token t on t.token_id = nftt.token_id
                                    where
                                      consensus_timestamp > @start and
                                      consensus_timestamp <= @end and
                                      (receiver_account_id = @account_id or sender_account_id = @account_id) and
                                      serial_number <> -1
                                  ) nft_change
                                  group by token_id, type
                                ) aggregated_nft_change
                              ), '[]') as nft_values`
	latestBalanceBeforeConsensus = `with abm as (
                                      select max(consensus_timestamp)
                                      from account_balance_file where consensus_timestamp <= @timestamp
                                    )
                                    select
                                      abm.max consensus_timestamp,
                                      coalesce(ab.balance, 0) balance,
                                      coalesce((
                                        select json_agg(json_build_object(
                                          'decimals', t.decimals,
                                          'token_id', tb.token_id,
                                          'type', t.type,
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
	// #nosec
	selectEverOwnedTokensByBlockAfter = `with next_rf as (
                                          select consensus_end
                                          from record_file
                                          where consensus_end > @consensus_timestamp
                                          order by consensus_end
                                          limit 1
                                        )
                                        select distinct on (t.token_id) t.decimals, t.token_id, t.type
                                        from token_account ta
                                        join next_rf on ta.modified_timestamp <= consensus_end
                                        join token t on t.token_id = ta.token_id
                                        where account_id = @account_id`
)

type combinedAccountBalance struct {
	ConsensusTimestamp int64
	Balance            int64
	TokenBalances      string
}

type accountBalanceChange struct {
	Value       int64
	TokenValues string
	NftValues   string
}

// accountRepository struct that has connection to the Database
type accountRepository struct {
	dbClient *types2.DbClient
}

// NewAccountRepository creates an instance of a accountRepository struct
func NewAccountRepository(dbClient *types2.DbClient) interfaces.AccountRepository {
	return &accountRepository{dbClient}
}

func (ar *accountRepository) RetrieveBalanceAtBlock(
	ctx context.Context,
	accountId int64,
	consensusEnd int64,
) ([]types.Amount, *rTypes.Error) {
	snapshotTimestamp, hbarAmount, tokenAmountMap, err := ar.getLatestBalanceSnapshot(ctx, accountId, consensusEnd)
	if err != nil {
		return nil, err
	}

	hbarValue, tokenValues, err := ar.getBalanceChange(ctx, accountId, snapshotTimestamp, consensusEnd)
	if err != nil {
		return nil, err
	}

	hbarAmount.Value += hbarValue
	tokenAmounts := getUpdatedTokenAmounts(tokenAmountMap, tokenValues)

	amounts := make([]types.Amount, 0, 1+len(tokenAmounts))
	amounts = append(amounts, hbarAmount)
	amounts = append(amounts, tokenAmounts...)

	return amounts, nil
}

func (ar *accountRepository) RetrieveEverOwnedTokensByBlockAfter(
	ctx context.Context,
	accountId int64,
	consensusEnd int64,
) ([]domain.Token, *rTypes.Error) {
	db, cancel := ar.dbClient.GetDbWithContext(ctx)
	if cancel != nil {
		defer cancel()
	}

	tokens := make([]domain.Token, 0)
	if err := db.Raw(
		selectEverOwnedTokensByBlockAfter,
		sql.Named("account_id", accountId),
		sql.Named("consensus_timestamp", consensusEnd),
	).Scan(&tokens).Error; err != nil {
		log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
		return nil, hErrors.ErrDatabaseError
	}

	return tokens, nil
}

func (ar *accountRepository) getLatestBalanceSnapshot(ctx context.Context, accountId, consensusEnd int64) (
	int64,
	*types.HbarAmount,
	map[int64]*types.TokenAmount,
	*rTypes.Error,
) {
	db, cancel := ar.dbClient.GetDbWithContext(ctx)
	defer cancel()

	// gets the most recent balance at or before consensusEnd
	cb := &combinedAccountBalance{}
	if err := db.Raw(
		latestBalanceBeforeConsensus,
		sql.Named("account_id", accountId),
		sql.Named("timestamp", consensusEnd),
	).First(cb).Error; err != nil {
		log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
		return 0, nil, nil, hErrors.ErrDatabaseError
	}

	if cb.ConsensusTimestamp == 0 {
		return 0, nil, nil, hErrors.ErrNodeIsStarting
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

func (ar *accountRepository) getBalanceChange(ctx context.Context, accountId, consensusStart, consensusEnd int64) (
	int64,
	[]*types.TokenAmount,
	*rTypes.Error,
) {
	db, cancel := ar.dbClient.GetDbWithContext(ctx)
	defer cancel()

	change := &accountBalanceChange{}
	// gets the balance change from the Balance snapshot until the target block
	if err := db.Raw(
		balanceChangeBetween,
		sql.Named("account_id", accountId),
		sql.Named("start", consensusStart),
		sql.Named("end", consensusEnd),
	).First(change).Error; err != nil {
		log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
		return 0, nil, hErrors.ErrDatabaseError
	}

	// fungible token values with the exception that a TokenDissociate of a deleted NFT class will have an entry
	// and its type then should be 'NON_FUNGIBLE_UNIQUE'
	var tokenValues []*types.TokenAmount
	if err := json.Unmarshal([]byte(change.TokenValues), &tokenValues); err != nil {
		return 0, nil, hErrors.ErrInvalidToken
	}

	// nft values
	var nftValues []*types.TokenAmount
	if err := json.Unmarshal([]byte(change.NftValues), &nftValues); err != nil {
		return 0, nil, hErrors.ErrInvalidToken
	}

	tokenValues = append(tokenValues, nftValues...)

	return change.Value, tokenValues, nil
}

func getUpdatedTokenAmounts(
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
