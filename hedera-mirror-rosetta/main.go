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
	"net/http"
	"os"
	"strings"

	"github.com/coinbase/rosetta-sdk-go/asserter"
	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/config"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/db"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/middleware"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/construction"
	log "github.com/sirupsen/logrus"
)

const moduleName = "hedera-mirror-rosetta"

var Version = "development"

func configLogger(level string) {
	var err error
	var logLevel log.Level

	if logLevel, err = log.ParseLevel(strings.ToLower(level)); err != nil {
		// if invalid, default to info
		logLevel = log.InfoLevel
	}

	log.SetLevel(logLevel)
	log.SetOutput(os.Stdout)
	log.SetFormatter(&log.TextFormatter{ // Use logfmt for easy parsing by Loki
		DisableColors: true,
		FullTimestamp: true,
	})
}

// newBlockchainOnlineRouter creates a Mux http.Handler from a collection
// of server controllers, serving "online" mode.
// ref: https://www.rosetta-api.org/docs/node_deployment.html#online-mode-endpoints
func newBlockchainOnlineRouter(
	network *rTypes.NetworkIdentifier,
	rosetta config.Rosetta,
	asserter *asserter.Asserter,
	version *rTypes.Version,
	dbClient interfaces.DbClient,
) (http.Handler, error) {
	accountRepo := persistence.NewAccountRepository(dbClient)
	addressBookEntryRepo := persistence.NewAddressBookEntryRepository(dbClient)
	blockRepo := persistence.NewBlockRepository(dbClient)
	tokenRepo := persistence.NewTokenRepository(dbClient)
	transactionRepo := persistence.NewTransactionRepository(dbClient)

	baseService := services.NewBaseService(blockRepo, transactionRepo)

	networkAPIService := services.NewNetworkAPIService(baseService, addressBookEntryRepo, network, version)
	networkAPIController := server.NewNetworkAPIController(networkAPIService, asserter)

	blockAPIService := services.NewBlockAPIService(baseService)
	blockAPIController := server.NewBlockAPIController(blockAPIService, asserter)

	mempoolAPIService := services.NewMempoolAPIService()
	mempoolAPIController := server.NewMempoolAPIController(mempoolAPIService, asserter)

	constructionAPIService, err := services.NewConstructionAPIService(
		network.Network,
		rosetta.Nodes,
		construction.NewTransactionConstructor(tokenRepo),
	)
	if err != nil {
		return nil, err
	}
	constructionAPIController := server.NewConstructionAPIController(constructionAPIService, asserter)

	accountAPIService := services.NewAccountAPIService(baseService, accountRepo)
	accountAPIController := server.NewAccountAPIController(accountAPIService, asserter)
	healthController, err := middleware.NewHealthController(rosetta.Db)
	metricsController := middleware.NewMetricsController()
	if err != nil {
		return nil, err
	}

	return server.NewRouter(
		networkAPIController,
		blockAPIController,
		mempoolAPIController,
		constructionAPIController,
		accountAPIController,
		healthController,
		metricsController,
	), nil
}

// newBlockchainOfflineRouter creates a Mux http.Handler from a collection
// of server controllers, serving "offline" mode.
// ref: https://www.rosetta-api.org/docs/node_deployment.html#offline-mode-endpoints
func newBlockchainOfflineRouter(
	network string,
	rosetta config.Rosetta,
	asserter *asserter.Asserter,
) (http.Handler, error) {
	constructionAPIService, err := services.NewConstructionAPIService(
		network,
		rosetta.Nodes,
		construction.NewTransactionConstructor(nil),
	)
	if err != nil {
		return nil, err
	}
	constructionAPIController := server.NewConstructionAPIController(constructionAPIService, asserter)
	healthController, err := middleware.NewHealthController(rosetta.Db)
	metricsController := middleware.NewMetricsController()
	if err != nil {
		return nil, err
	}

	return server.NewRouter(constructionAPIController, healthController, metricsController), nil
}

func main() {
	configLogger("info")

	rosettaConfig, err := config.LoadConfig()
	if err != nil {
		log.Fatalf("Failed to load config: %s", err)
	}

	log.Infof("%s version %s, rosetta api version %s", moduleName, Version, rosettaConfig.ApiVersion)

	configLogger(rosettaConfig.Log.Level)

	network := &rTypes.NetworkIdentifier{
		Blockchain: types.Blockchain,
		Network:    strings.ToLower(rosettaConfig.Network),
		SubNetworkIdentifier: &rTypes.SubNetworkIdentifier{
			Network: fmt.Sprintf("shard %s realm %s", rosettaConfig.Shard, rosettaConfig.Realm),
		},
	}

	version := &rTypes.Version{
		RosettaVersion:    rosettaConfig.ApiVersion,
		NodeVersion:       rosettaConfig.NodeVersion,
		MiddlewareVersion: &Version,
	}

	asserter, err := asserter.NewServer(
		types.SupportedOperationTypes,
		true,
		[]*rTypes.NetworkIdentifier{network},
		nil,
		false,
		"",
	)
	if err != nil {
		log.Fatal(err)
	}

	var router http.Handler

	if rosettaConfig.Online {
		dbClient := db.ConnectToDb(rosettaConfig.Db)

		router, err = newBlockchainOnlineRouter(network, *rosettaConfig, asserter, version, dbClient)
		if err != nil {
			log.Fatal(err)
		}

		log.Info("Serving Rosetta API in ONLINE mode")
	} else {
		router, err = newBlockchainOfflineRouter(network.Network, *rosettaConfig, asserter)
		if err != nil {
			log.Fatal(err)
		}

		log.Info("Serving Rosetta API in OFFLINE mode")
	}

	metricsMiddleware := middleware.MetricsMiddleware(router)
	tracingMiddleware := middleware.TracingMiddleware(metricsMiddleware)
	corsMiddleware := server.CorsMiddleware(tracingMiddleware)
	log.Infof("Listening on port %d", rosettaConfig.Port)
	log.Fatal(http.ListenAndServe(fmt.Sprintf(":%d", rosettaConfig.Port), corsMiddleware))
}
