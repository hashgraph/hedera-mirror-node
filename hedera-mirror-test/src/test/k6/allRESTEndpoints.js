/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
 */

import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate } from "k6/metrics";

export let errorRate = new Rate("errors");

// options used by k6 framework
export let options = {
  vus: __ENV.DEFAULT_VUS,
  duration: __ENV.DEFAULT_DURATION,
  thresholds: {
    errors: ["rate<0.1"], // threshold on a custom metric
    http_req_duration: ["p(95)<500"], // threshold on a standard metric
  },
  insecureSkipTLSVerify: true,
  noConnectionReuse: true,
  noVUConnectionReuse: true,
};

const SLEEP_DURATION = 2;

export default function () {
  group("/api/v1/accounts", () => {
    let url = __ENV.BASE_URL + `/api/v1/accounts?limit=${__ENV.DEFAULT_LIMIT}`;
    let request = http.get(url);
    console.log(`call : ${url}`);
    check(request, {
      "Accounts OK": (r) => r.status === 200,
    });
    errorRate.add(!request);
    sleep(SLEEP_DURATION);
  });
  group("/api/v1/accounts/{id}", () => {
    let url = __ENV.BASE_URL + `/api/v1/accounts/${__ENV.DEFAULT_ACCOUNT}`;
    let request = http.get(url);
    console.log(`call : ${url}`);
    check(request, {
      "Account Transactions OK": (r) => r.status === 200,
    });
    errorRate.add(!request);
    sleep(SLEEP_DURATION);
  });
  group("/api/v1/balances", () => {
    let url = __ENV.BASE_URL + `/api/v1/balances?limit=${__ENV.DEFAULT_LIMIT}`;
    let request = http.get(url);
    console.log(`call : ${url}`);
    check(request, {
      "Balances OK": (r) => r.status === 200,
    });
    errorRate.add(!request);
    sleep(SLEEP_DURATION);
  });
  group("/api/v1/transactions", () => {
    let url = __ENV.BASE_URL + `/api/v1/transactions?limit=${__ENV.DEFAULT_LIMIT}`;
    let request = http.get(url);
    console.log(`call : ${url}`);
    check(request, {
      "Transactions OK": (r) => r.status === 200,
    });
    errorRate.add(!request);
    sleep(SLEEP_DURATION);
  });
  group("/api/v1/transactions/{id}", () => {
    let url = __ENV.BASE_URL + `/api/v1/transactions/${__ENV.DEFAULT_TRANSACTION}`;
    let request = http.get(url);
    console.log(`call : ${url}`);
    check(request, {
      "Transaction Id OK": (r) => r.status === 200,
    });
    errorRate.add(!request);
    sleep(SLEEP_DURATION);
  });
  // group("/api/v1/transactions/{id}/stateproof", () => {
  //     let id = "TODO_EDIT_THE_ID";
  //     let url = BASE_URL + `/api/v1/transactions/${id}/stateproof`;
  //     // Request No. 1
  //     let request = http.get(url);
  //     sleep(SLEEP_DURATION);
  // });
  group("/api/v1/topics/{id}/messages", () => {
    let url = __ENV.BASE_URL + `/api/v1/topics/${__ENV.DEFAULT_TOPIC}/messages?limit=${__ENV.DEFAULT_LIMIT}`;
    let request = http.get(url);
    console.log(`call : ${url}`);
    check(request, {
      "Topic Messages OK": (r) => r.status === 200,
    });
    errorRate.add(!request);
    sleep(SLEEP_DURATION);
  });
  group("/api/v1/topics/{id}/messages/{sequencenumber}", () => {
    let url =
      __ENV.BASE_URL + `/api/v1/topics/${__ENV.DEFAULT_TOPIC}/messages?sequencenumber=${__ENV.DEFAULT_TOPIC_SEQUENCE}`;
    let request = http.get(url);
    console.log(`call : ${url}`);
    check(request, {
      "Topic Message OK": (r) => r.status === 200,
    });
    errorRate.add(!request);
    sleep(SLEEP_DURATION);
  });
  group("/api/v1/topics/messages/{consensusTimestamp}", () => {
    let url = __ENV.BASE_URL + `/api/v1/topics/messages/${__ENV.DEFAULT_TOPIC_TIMESTAMP}`;
    let request = http.get(url);
    check(request, {
      "Topic messgae Timestamp OK": (r) => r.status === 200,
    });
    errorRate.add(!request);
    sleep(SLEEP_DURATION);
  });
  group("/api/v1/tokens", () => {
    let url = __ENV.BASE_URL + `/api/v1/tokens?limit=${__ENV.DEFAULT_LIMIT}`;
    let request = http.get(url);
    console.log(`call : ${url}`);
    check(request, {
      "Tokens OK": (r) => r.status === 200,
    });
    errorRate.add(!request);
    sleep(SLEEP_DURATION);
  });
  group("/api/v1/tokens/{id}", () => {
    let url = __ENV.BASE_URL + `/api/v1/tokens/${__ENV.DEFAULT_TOKEN}`;
    let request = http.get(url);
    console.log(`call : ${url}`);
    check(request, {
      "Token Id OK": (r) => r.status === 200,
    });
    errorRate.add(!request);
    sleep(SLEEP_DURATION);
  });
  group("/api/v1/tokens/{id}/balances", () => {
    let url = __ENV.BASE_URL + `/api/v1/tokens/${__ENV.DEFAULT_TOKEN}/balances`;
    let request = http.get(url);
    console.log(`call : ${url}`);
    check(request, {
      "Token Balance OK": (r) => r.status === 200,
    });
    errorRate.add(!request);
    sleep(SLEEP_DURATION);
  });
}
