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

package main

import (
	"fmt"
	"net/http"
	"os"
	"runtime"
	"strings"

	rosettaAsserter "github.com/coinbase/rosetta-sdk-go/asserter"
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

	log.SetFormatter(&log.TextFormatter{ // Use logfmt for easy parsing by Loki
		CallerPrettyfier: func(frame *runtime.Frame) (function string, file string) {
			parts := strings.Split(frame.File, moduleName)
			relativeFilepath := parts[len(parts)-1]
			// remove function name, show file path relative to project root
			return "", fmt.Sprintf("%s:%d", relativeFilepath, frame.Line)
		},
		DisableColors: true,
		FullTimestamp: true,
	})
	log.SetLevel(logLevel)
	log.SetOutput(os.Stdout)
	log.SetReportCaller(logLevel >= log.DebugLevel)
}

// newBlockchainOnlineRouter creates a Mux http.Handler from a collection
// of server controllers, serving "online" mode.
// ref: https://www.rosetta-api.org/docs/node_deployment.html#online-mode-endpoints
func newBlockchainOnlineRouter(
	asserter *rosettaAsserter.Asserter,
	config config.Config,
	dbClient interfaces.DbClient,
	network *rTypes.NetworkIdentifier,
	version *rTypes.Version,
) (http.Handler, error) {
	accountRepo := persistence.NewAccountRepository(dbClient)
	addressBookEntryRepo := persistence.NewAddressBookEntryRepository(dbClient)
	blockRepo := persistence.NewBlockRepository(dbClient)
	transactionRepo := persistence.NewTransactionRepository(dbClient)

	baseService := services.NewOnlineBaseService(blockRepo, transactionRepo)

	networkAPIService := services.NewNetworkAPIService(baseService, addressBookEntryRepo, network, version)
	networkAPIController := server.NewNetworkAPIController(networkAPIService, asserter)

	blockAPIService := services.NewBlockAPIService(baseService)
	blockAPIController := server.NewBlockAPIController(blockAPIService, asserter)

	mempoolAPIService := services.NewMempoolAPIService()
	mempoolAPIController := server.NewMempoolAPIController(mempoolAPIService, asserter)

	constructionAPIService, err := services.NewConstructionAPIService(
		baseService,
		network.Network,
		config.Nodes,
		config.Shard,
		config.Realm,
		construction.NewTransactionConstructor(),
	)
	if err != nil {
		return nil, err
	}
	constructionAPIController := server.NewConstructionAPIController(constructionAPIService, asserter)

	accountAPIService := services.NewAccountAPIService(baseService, accountRepo, config.Shard, config.Realm)
	accountAPIController := server.NewAccountAPIController(accountAPIService, asserter)
	healthController, err := middleware.NewHealthController(config.Db)
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
	asserter *rosettaAsserter.Asserter,
	config config.Config,
	network *rTypes.NetworkIdentifier,
	version *rTypes.Version,
) (http.Handler, error) {
	baseService := services.NewOfflineBaseService()

	constructionAPIService, err := services.NewConstructionAPIService(
		baseService,
		network.Network,
		config.Nodes,
		config.Shard,
		config.Realm,
		construction.NewTransactionConstructor(),
	)
	if err != nil {
		return nil, err
	}
	constructionAPIController := server.NewConstructionAPIController(constructionAPIService, asserter)
	healthController, err := middleware.NewHealthController(config.Db)
	if err != nil {
		return nil, err
	}

	metricsController := middleware.NewMetricsController()
	networkAPIService := services.NewNetworkAPIService(baseService, nil, network, version)
	networkAPIController := server.NewNetworkAPIController(networkAPIService, asserter)

	return server.NewRouter(constructionAPIController, healthController, metricsController, networkAPIController), nil
}

func main() {
	configLogger("info")

	rosettaConfig, err := config.LoadConfig()
	if err != nil {
		log.Fatalf("Failed to load config: %s", err)
	}

	log.Infof("%s version %s, rosetta api version %s", moduleName, Version, rTypes.RosettaAPIVersion)

	configLogger(rosettaConfig.Log.Level)

	network := &rTypes.NetworkIdentifier{
		Blockchain: types.Blockchain,
		Network:    strings.ToLower(rosettaConfig.Network),
		SubNetworkIdentifier: &rTypes.SubNetworkIdentifier{
			Network: fmt.Sprintf("shard %d realm %d", rosettaConfig.Shard, rosettaConfig.Realm),
		},
	}

	version := &rTypes.Version{
		RosettaVersion:    rTypes.RosettaAPIVersion,
		NodeVersion:       rosettaConfig.NodeVersion,
		MiddlewareVersion: &Version,
	}

	asserter, err := rosettaAsserter.NewServer(
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

		router, err = newBlockchainOnlineRouter(asserter, *rosettaConfig, dbClient, network, version)
		if err != nil {
			log.Fatal(err)
		}

		log.Info("Serving Rosetta API in ONLINE mode")
	} else {
		router, err = newBlockchainOfflineRouter(asserter, *rosettaConfig, network, version)
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
