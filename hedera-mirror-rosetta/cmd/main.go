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

package main

import (
    "fmt"
    accountService "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/account"
    "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/base"
    blockService "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/block"
    constructionService "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/construction"
    mempoolService "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/mempool"
    networkService "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/network"
    "log"
    "net/http"
    "strings"

    "github.com/coinbase/rosetta-sdk-go/asserter"
    "github.com/coinbase/rosetta-sdk-go/server"
    "github.com/coinbase/rosetta-sdk-go/types"
    "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/account"
    addressBookEntry "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/addressbook/entry"
    "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/block"
    "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/transaction"
    "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
    "github.com/jinzhu/gorm"
)

// NewBlockchainOnlineRouter creates a Mux http.Handler from a collection
// of server controllers, serving "online" mode.
// ref: https://www.rosetta-api.org/docs/node_deployment.html#online-mode-endpoints
func NewBlockchainOnlineRouter(network *types.NetworkIdentifier, asserter *asserter.Asserter, version *types.Version, dbClient *gorm.DB) http.Handler {
    blockRepo := block.NewBlockRepository(dbClient)
    transactionRepo := transaction.NewTransactionRepository(dbClient)
    accountRepo := account.NewAccountRepository(dbClient)
    addressBookEntryRepo := addressBookEntry.NewAddressBookEntryRepository(dbClient)

    baseService := base.NewBaseService(blockRepo, transactionRepo)

    networkAPIService := networkService.NewNetworkAPIService(baseService, addressBookEntryRepo, network, version)
    networkAPIController := server.NewNetworkAPIController(networkAPIService, asserter)

    blockAPIService := blockService.NewBlockAPIService(baseService)
    blockAPIController := server.NewBlockAPIController(blockAPIService, asserter)

    mempoolAPIService := mempoolService.NewMempoolAPIService()
    mempoolAPIController := server.NewMempoolAPIController(mempoolAPIService, asserter)

    constructionAPIService := constructionService.NewConstructionAPIService()
    constructionAPIController := server.NewConstructionAPIController(constructionAPIService, asserter)

    accountAPIService := accountService.NewAccountAPIService(baseService, accountRepo)
    accountAPIController := server.NewAccountAPIController(accountAPIService, asserter)

    return server.NewRouter(networkAPIController, blockAPIController, mempoolAPIController, constructionAPIController, accountAPIController)
}

// NewBlockchainOfflineRouter creates a Mux http.Handler from a collection
// of server controllers, serving "offline" mode.
// ref: https://www.rosetta-api.org/docs/node_deployment.html#offline-mode-endpoints
func NewBlockchainOfflineRouter(asserter *asserter.Asserter) http.Handler {
    constructionAPIService := constructionService.NewConstructionAPIService()
    constructionAPIController := server.NewConstructionAPIController(constructionAPIService, asserter)

    return server.NewRouter(constructionAPIController)
}

func main() {
    configuration := LoadConfig()

    network := &types.NetworkIdentifier{
        Blockchain: config.Blockchain,
        Network:    strings.ToLower(configuration.Hedera.Mirror.Rosetta.Network),
        SubNetworkIdentifier: &types.SubNetworkIdentifier{
            Network: fmt.Sprintf("shard %s realm %s", configuration.Hedera.Mirror.Rosetta.Shard, configuration.Hedera.Mirror.Rosetta.Realm),
        },
    }

    version := &types.Version{
        RosettaVersion:    configuration.Hedera.Mirror.Rosetta.ApiVersion,
        NodeVersion:       configuration.Hedera.Mirror.Rosetta.NodeVersion,
        MiddlewareVersion: &configuration.Hedera.Mirror.Rosetta.Version,
    }

    asserter, err := asserter.NewServer(
        []string{config.OperationTypeCryptoTransfer},
        true,
        []*types.NetworkIdentifier{network},
    )
    if err != nil {
        log.Fatal(err)
    }

    var router http.Handler

    if configuration.Hedera.Mirror.Rosetta.Online {
        dbClient := connectToDb(configuration.Hedera.Mirror.Rosetta.Db)
        defer dbClient.Close()

        router = NewBlockchainOnlineRouter(network, asserter, version, dbClient)
        log.Printf("Serving Rosetta API in ONLINE mode")
    } else {
        router = NewBlockchainOfflineRouter(asserter)
        log.Printf("Serving Rosetta API in OFFLINE mode")
    }

    loggedRouter := server.LoggerMiddleware(router)
    corsRouter := server.CorsMiddleware(loggedRouter)
    log.Printf("Listening on port %s\n", configuration.Hedera.Mirror.Rosetta.Port)
    log.Fatal(http.ListenAndServe(fmt.Sprintf(":%s", configuration.Hedera.Mirror.Rosetta.Port), corsRouter))
}
