/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

// external libraries
import {Router} from '@awaitjs/express';

import {ContractController} from '../controllers';

const router = Router();

const resource = 'contracts';
router.getAsync('/', ContractController.getContracts);
router.getAsync('/:contractId', ContractController.getContractById);
router.getAsync('/:contractId/results', ContractController.getContractResultsById);
router.getAsync('/:contractId/state', ContractController.getContractStateById);
router.getAsync('/:contractId/results/logs', ContractController.getContractLogsById);
router.getAsync('/:contractId/results/:consensusTimestamp([0-9.]+)', ContractController.getContractResultsByTimestamp);
router.getAsync('/results', ContractController.getContractResults);
router.getAsync('/results/logs', ContractController.getContractLogs);
router.getAsync('/results/:transactionIdOrHash', ContractController.getContractResultsByTransactionIdOrHash);
router.getAsync('/results/:transactionIdOrHash/actions', ContractController.getContractActions);

export default {
  resource,
  router,
};
