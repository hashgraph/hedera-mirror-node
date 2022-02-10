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

package persistence

import (
	"context"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"sync"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/tools"
	log "github.com/sirupsen/logrus"
)

const (
	batchSize                      = 2000
	transactionResultSuccess int32 = 22
)

const (
	andTransactionHashFilter  = " and transaction_hash = @hash"
	orderByConsensusTimestamp = " order by consensus_timestamp"
	// selectTransactionsInTimestampRange selects the transactions with its crypto transfers in json, non-fee transfers
	// in json, token transfers in json, and optionally the token information when the transaction is token create,
	// token delete, or token update. Note the three token transactions are the ones the entity_id in the transaction
	// table is its related token id and require an extra rosetta operation
	selectTransactionsInTimestampRange = `select
                                            t.consensus_timestamp,
                                            t.entity_id,
                                            t.payer_account_id,
                                            t.result,
                                            t.transaction_hash as hash,
                                            t.type,
                                            coalesce((
                                              select json_agg(json_build_object(
                                                'account_id', entity_id,
                                                'amount', amount))
                                              from crypto_transfer where consensus_timestamp = t.consensus_timestamp
                                            ), '[]') as crypto_transfers,
                                            case
                                              when t.type = 14 then coalesce((
                                                  select json_agg(json_build_object(
                                                      'account_id', entity_id,
                                                      'amount', amount
                                                    ))
                                                  from non_fee_transfer
                                                  where consensus_timestamp = t.consensus_timestamp
                                                ), '[]')
                                              else '[]'
                                            end as non_fee_transfers,
                                            coalesce((
                                              select json_agg(json_build_object(
                                                  'account_id', account_id,
                                                  'amount', amount,
                                                  'decimals', tk.decimals,
                                                  'token_id', tkt.token_id,
                                                  'type', tk.type
                                                ))
                                              from token_transfer tkt
                                              join token tk on tk.token_id = tkt.token_id
                                              where tkt.consensus_timestamp = t.consensus_timestamp
                                            ), '[]') as token_transfers,
                                            coalesce((
                                              select json_agg(json_build_object(
                                                  'receiver_account_id', receiver_account_id,
                                                  'sender_account_id', sender_account_id,
                                                  'serial_number', serial_number,
                                                  'token_id', tk.token_id
                                                ))
                                              from nft_transfer nftt
                                              join token tk on tk.token_id = nftt.token_id
                                              where nftt.consensus_timestamp = t.consensus_timestamp and serial_number <> -1
                                            ), '[]') as nft_transfers,
                                            case
                                              when t.type in (29, 35, 36) then coalesce((
                                                  select json_build_object(
                                                    'decimals', decimals,
                                                    'freeze_default', freeze_default,
                                                    'initial_supply', initial_supply,
                                                    'token_id', token_id,
                                                    'type', type
                                                  )
                                                  from token
                                                  where token_id = t.entity_id
                                                ), '{}')
                                              else '{}'
                                            end as token
                                          from transaction t
                                          where consensus_timestamp >= @start and consensus_timestamp <= @end`
	selectTransactionsByHashInTimestampRange  = selectTransactionsInTimestampRange + andTransactionHashFilter
	selectTransactionsInTimestampRangeOrdered = selectTransactionsInTimestampRange + orderByConsensusTimestamp
)

// transaction maps to the transaction query which returns the required transaction fields, CryptoTransfers json string,
// NonFeeTransfers json string, TokenTransfers json string, and Token definition json string
type transaction struct {
	ConsensusTimestamp int64
	EntityId           *domain.EntityId
	Hash               []byte
	PayerAccountId     int64
	Result             int16
	Type               int16
	CryptoTransfers    string
	NftTransfers       string
	NonFeeTransfers    string
	TokenTransfers     string
	Token              string
}

func (t transaction) getHashString() string {
	return tools.SafeAddHexPrefix(hex.EncodeToString(t.Hash))
}

type transfer interface {
	getAccountId() domain.EntityId
	getAmount() types.Amount
}

type hbarTransfer struct {
	AccountId domain.EntityId `json:"account_id"`
	Amount    int64           `json:"amount"`
}

func (t hbarTransfer) getAccountId() domain.EntityId {
	return t.AccountId
}

func (t hbarTransfer) getAmount() types.Amount {
	return &types.HbarAmount{Value: t.Amount}
}

type singleNftTransfer struct {
	accountId    domain.EntityId
	receiver     bool
	serialNumber int64
	tokenId      domain.EntityId
}

func (n singleNftTransfer) getAccountId() domain.EntityId {
	return n.accountId
}

func (n singleNftTransfer) getAmount() types.Amount {
	amount := int64(1)
	if !n.receiver {
		amount = -1
	}

	return &types.TokenAmount{
		SerialNumbers: []int64{n.serialNumber},
		TokenId:       n.tokenId,
		Type:          domain.TokenTypeNonFungibleUnique,
		Value:         amount,
	}
}

type tokenTransfer struct {
	AccountId domain.EntityId `json:"account_id"`
	Amount    int64           `json:"amount"`
	Decimals  int64           `json:"decimals"`
	TokenId   domain.EntityId `json:"token_id"`
	Type      string          `json:"type"`
}

func (t tokenTransfer) getAccountId() domain.EntityId {
	return t.AccountId
}

func (t tokenTransfer) getAmount() types.Amount {
	return &types.TokenAmount{
		Decimals: t.Decimals,
		TokenId:  t.TokenId,
		Type:     t.Type,
		Value:    t.Amount,
	}
}

// transactionRepository struct that has connection to the Database
type transactionRepository struct {
	once     sync.Once
	dbClient interfaces.DbClient
	types    map[int]string
}

// NewTransactionRepository creates an instance of a TransactionRepository struct
func NewTransactionRepository(dbClient interfaces.DbClient) interfaces.TransactionRepository {
	return &transactionRepository{dbClient: dbClient}
}

func (tr *transactionRepository) FindBetween(ctx context.Context, start, end int64) (
	[]*types.Transaction,
	*rTypes.Error,
) {
	if start > end {
		return nil, hErrors.ErrStartMustNotBeAfterEnd
	}

	db, cancel := tr.dbClient.GetDbWithContext(ctx)
	defer cancel()

	transactions := make([]*transaction, 0)
	for start <= end {
		transactionsBatch := make([]*transaction, 0)
		err := db.
			Raw(selectTransactionsInTimestampRangeOrdered, sql.Named("start", start), sql.Named("end", end)).
			Limit(batchSize).
			Find(&transactionsBatch).
			Error
		if err != nil {
			log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
			return nil, hErrors.ErrDatabaseError
		}

		transactions = append(transactions, transactionsBatch...)

		if len(transactionsBatch) < batchSize {
			break
		}

		start = transactionsBatch[len(transactionsBatch)-1].ConsensusTimestamp + 1
	}

	hashes := make([]string, 0)
	sameHashMap := make(map[string][]*transaction)
	for _, t := range transactions {
		h := t.getHashString()
		if _, ok := sameHashMap[h]; !ok {
			// save the unique hashes in chronological order
			hashes = append(hashes, h)
		}

		sameHashMap[h] = append(sameHashMap[h], t)
	}

	res := make([]*types.Transaction, 0, len(sameHashMap))
	for _, hash := range hashes {
		sameHashTransactions := sameHashMap[hash]
		transaction, err := tr.constructTransaction(sameHashTransactions)
		if err != nil {
			return nil, err
		}
		res = append(res, transaction)
	}
	return res, nil
}

func (tr *transactionRepository) FindByHashInBlock(
	ctx context.Context,
	hashStr string,
	consensusStart int64,
	consensusEnd int64,
) (*types.Transaction, *rTypes.Error) {
	var transactions []*transaction
	transactionHash, err := hex.DecodeString(tools.SafeRemoveHexPrefix(hashStr))
	if err != nil {
		return nil, hErrors.ErrInvalidTransactionIdentifier
	}

	db, cancel := tr.dbClient.GetDbWithContext(ctx)
	defer cancel()

	if err = db.Raw(
		selectTransactionsByHashInTimestampRange,
		sql.Named("hash", transactionHash),
		sql.Named("start", consensusStart),
		sql.Named("end", consensusEnd),
	).Find(&transactions).Error; err != nil {
		log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
		return nil, hErrors.ErrDatabaseError
	}

	if len(transactions) == 0 {
		return nil, hErrors.ErrTransactionNotFound
	}

	transaction, rErr := tr.constructTransaction(transactions)
	if rErr != nil {
		return nil, rErr
	}

	return transaction, nil
}

func (tr *transactionRepository) constructTransaction(sameHashTransactions []*transaction) (
	*types.Transaction,
	*rTypes.Error,
) {
	tResult := &types.Transaction{Hash: sameHashTransactions[0].getHashString()}
	operations := make([]*types.Operation, 0)
	success := types.TransactionResults[transactionResultSuccess]

	for _, transaction := range sameHashTransactions {
		cryptoTransfers := make([]hbarTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.CryptoTransfers), &cryptoTransfers); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		nonFeeTransfers := make([]hbarTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.NonFeeTransfers), &nonFeeTransfers); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		tokenTransfers := make([]tokenTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.TokenTransfers), &tokenTransfers); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		nftTransfers := make([]domain.NftTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.NftTransfers), &nftTransfers); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		token := domain.Token{}
		if err := json.Unmarshal([]byte(transaction.Token), &token); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		transactionResult := types.TransactionResults[int32(transaction.Result)]
		transactionType := types.TransactionTypes[int32(transaction.Type)]

		nonFeeTransferMap := aggregateNonFeeTransfers(nonFeeTransfers)
		adjustedCryptoTransfers := adjustCryptoTransfers(cryptoTransfers, nonFeeTransferMap)

		operations = tr.appendHbarTransferOperations(transactionResult, transactionType, nonFeeTransfers, operations)
		// crypto transfers are always successful regardless of the transaction result
		operations = tr.appendHbarTransferOperations(success, transactionType, adjustedCryptoTransfers, operations)
		operations = tr.appendTokenTransferOperations(transactionResult, transactionType, tokenTransfers, operations)
		operations = tr.appendNftTransferOperations(transactionResult, transactionType, nftTransfers, operations)

		if !token.TokenId.IsZero() {
			// only for TokenCreate, TokenDeletion, and TokenUpdate, TokenId is non-zero
			operation, err := getTokenOperation(len(operations), token, transaction, transactionResult, transactionType)
			if err != nil {
				return nil, err
			}
			operations = append(operations, operation)
		}

		if transaction.Result == int16(transactionResultSuccess) {
			tResult.EntityId = transaction.EntityId
		}
	}

	tResult.Operations = operations
	return tResult, nil
}

func (tr *transactionRepository) appendHbarTransferOperations(
	transactionResult string,
	transactionType string,
	hbarTransfers []hbarTransfer,
	operations []*types.Operation,
) []*types.Operation {
	transfers := make([]transfer, 0, len(hbarTransfers))
	for _, hbarTransfer := range hbarTransfers {
		transfers = append(transfers, hbarTransfer)
	}

	return tr.appendTransferOperations(transactionResult, transactionType, transfers, operations)
}

func (tr *transactionRepository) appendNftTransferOperations(
	transactionResult string,
	transactionType string,
	nftTransfers []domain.NftTransfer,
	operations []*types.Operation,
) []*types.Operation {
	transfers := make([]transfer, 0, 2*len(nftTransfers))
	for _, nftTransfer := range nftTransfers {
		transfers = append(transfers, getSingleNftTransfers(nftTransfer)...)
	}

	return tr.appendTransferOperations(transactionResult, transactionType, transfers, operations)
}

func (tr *transactionRepository) appendTokenTransferOperations(
	transactionResult string,
	transactionType string,
	tokenTransfers []tokenTransfer,
	operations []*types.Operation,
) []*types.Operation {
	transfers := make([]transfer, 0, len(tokenTransfers))
	for _, tokenTransfer := range tokenTransfers {
		// The wiped amount of a deleted NFT class by a TokenDissociate is presented as tokenTransferList and
		// saved to token_transfer table, filter it
		if tokenTransfer.Type != domain.TokenTypeFungibleCommon {
			continue
		}

		transfers = append(transfers, tokenTransfer)
	}

	return tr.appendTransferOperations(transactionResult, transactionType, transfers, operations)
}

func (tr *transactionRepository) appendTransferOperations(
	transactionResult string,
	transactionType string,
	transfers []transfer,
	operations []*types.Operation,
) []*types.Operation {
	for _, transfer := range transfers {
		operations = append(operations, &types.Operation{
			Index:   int64(len(operations)),
			Type:    transactionType,
			Status:  transactionResult,
			Account: types.Account{EntityId: transfer.getAccountId()},
			Amount:  transfer.getAmount(),
		})
	}
	return operations
}

func IsTransactionResultSuccessful(result int32) bool {
	return result == transactionResultSuccess
}

func constructAccount(encodedId int64) (types.Account, *rTypes.Error) {
	account, err := types.NewAccountFromEncodedID(encodedId)
	if err != nil {
		log.Errorf(hErrors.CreateAccountDbIdFailed, encodedId)
		return types.Account{}, hErrors.ErrInternalServerError
	}
	return account, nil
}

func adjustCryptoTransfers(
	cryptoTransfers []hbarTransfer,
	nonFeeTransferMap map[int64]int64,
) []hbarTransfer {
	cryptoTransferMap := make(map[int64]hbarTransfer)
	for _, transfer := range cryptoTransfers {
		key := transfer.AccountId.EncodedId
		cryptoTransferMap[key] = hbarTransfer{
			AccountId: transfer.AccountId,
			Amount:    transfer.Amount + cryptoTransferMap[key].Amount,
		}
	}

	adjusted := make([]hbarTransfer, 0, len(cryptoTransfers))
	for key, aggregated := range cryptoTransferMap {
		amount := aggregated.Amount - nonFeeTransferMap[key]
		if amount != 0 {
			adjusted = append(adjusted, hbarTransfer{
				AccountId: aggregated.AccountId,
				Amount:    amount,
			})
		}
	}

	return adjusted
}

func aggregateNonFeeTransfers(nonFeeTransfers []hbarTransfer) map[int64]int64 {
	nonFeeTransferMap := make(map[int64]int64)

	// the original transfer list from the transaction body
	for _, transfer := range nonFeeTransfers {
		// the original transfer list may have multiple entries for one entity, so accumulate it
		nonFeeTransferMap[transfer.AccountId.EncodedId] += transfer.Amount
	}

	return nonFeeTransferMap
}

func getSingleNftTransfers(nftTransfer domain.NftTransfer) []transfer {
	transfers := make([]transfer, 0)
	if nftTransfer.ReceiverAccountId != nil {
		transfers = append(transfers, singleNftTransfer{
			accountId:    *nftTransfer.ReceiverAccountId,
			receiver:     true,
			serialNumber: nftTransfer.SerialNumber,
			tokenId:      nftTransfer.TokenId,
		})
	}

	if nftTransfer.SenderAccountId != nil {
		transfers = append(transfers, singleNftTransfer{
			accountId:    *nftTransfer.SenderAccountId,
			serialNumber: nftTransfer.SerialNumber,
			tokenId:      nftTransfer.TokenId,
		})
	}
	return transfers
}

func getTokenOperation(
	index int,
	token domain.Token,
	transaction *transaction,
	transactionResult string,
	transactionType string,
) (*types.Operation, *rTypes.Error) {
	payerId, err := constructAccount(transaction.PayerAccountId)
	if err != nil {
		return nil, err
	}

	operation := &types.Operation{
		Index:   int64(index),
		Type:    transactionType,
		Status:  transactionResult,
		Account: payerId,
	}

	metadata := make(map[string]interface{})
	metadata["currency"] = types.Token{Token: token}.ToRosettaCurrency()
	metadata["freeze_default"] = token.FreezeDefault
	metadata["initial_supply"] = token.InitialSupply
	operation.Metadata = metadata

	return operation, nil
}
