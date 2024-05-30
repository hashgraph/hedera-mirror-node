/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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
 */

const maxWorkers = process.env.CI ? 2 : '50%'; // 2 workers in CI and 50% of cores number of works in local envs
const config = {
  collectCoverage: true,
  coverageDirectory: 'build/coverage/',
  coveragePathIgnorePatterns: [
    '<rootDir>/build/',
    '<rootDir>/check-state-proof/',
    '<rootDir>/monitoring/',
    '<rootDir>/node_modules/',
    '<rootDir>/__tests__/',
  ],
  globalTeardown: './__tests__/globalTeardown.js',
  maxWorkers,
  reporters: [['github-actions', {silent: false}], 'jest-junit', ['summary', {summaryThreshold: 0}]],
  setupFilesAfterEnv: ['./__tests__/jestSetup.js'],
  testEnvironment: 'node',
  testPathIgnorePatterns: ['/build/', '/node_modules/'],
  testRegex: '/__tests__/.*\\.test\\.js$',
  verbose: true,
};

export default config;
