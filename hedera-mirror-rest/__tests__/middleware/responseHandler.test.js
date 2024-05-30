/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import {jest} from '@jest/globals';

import config from '../../config';
import {NotFoundError} from '../../errors';
import {responseHandler} from '../../middleware';
import {JSONStringify} from '../../utils';

const {
  response: {headers},
} = config;

describe('Response middleware', () => {
  let mockRequest, mockResponse, responseData;

  beforeEach(() => {
    responseData = {transactions: [], links: {next: null}};
    mockRequest = {
      ip: '127.0.0.1',
      method: 'GET',
      originalUrl: '/api/v1/transactions/0.0.10-1234567890-000000001',
      requestStartTime: Date.now() - 5,
      route: {
        path: '/api/v1/transactions/:transactionId',
      },
    };
    mockResponse = {
      locals: {
        responseData: responseData,
        statusCode: 200,
      },
      send: jest.fn(),
      set: jest.fn(),
      status: jest.fn(),
    };
  });

  test('No response data', async () => {
    mockResponse.locals.responseData = undefined;
    await expect(responseHandler(mockRequest, mockResponse, null)).rejects.toThrow(NotFoundError);
  });

  test('Custom headers', async () => {
    await responseHandler(mockRequest, mockResponse, null);
    expect(mockResponse.send).toBeCalledWith(JSONStringify(responseData));
    expect(mockResponse.set).toHaveBeenNthCalledWith(1, headers.default);
    expect(mockResponse.set).toHaveBeenNthCalledWith(2, headers.path[mockRequest.route.path]);
    expect(mockResponse.set).toHaveBeenNthCalledWith(3, 'Content-Type', 'application/json; charset=utf-8');
    expect(mockResponse.status).toBeCalledWith(mockResponse.locals.statusCode);
  });

  test('Default headers', async () => {
    mockRequest.route.path = '/api/v1/accounts';
    await responseHandler(mockRequest, mockResponse, null);
    expect(mockResponse.send).toBeCalledWith(JSONStringify(responseData));
    expect(mockResponse.set).toHaveBeenNthCalledWith(1, headers.default);
    expect(mockResponse.set).toHaveBeenNthCalledWith(2, undefined);
    expect(mockResponse.set).toHaveBeenNthCalledWith(3, 'Content-Type', 'application/json; charset=utf-8');
    expect(mockResponse.status).toBeCalledWith(mockResponse.locals.statusCode);
  });

  test('Custom Content-Type', async () => {
    mockResponse.locals.responseContentType = 'text/plain';
    mockResponse.locals.responseData = '123';
    await responseHandler(mockRequest, mockResponse, null);
    expect(mockResponse.send).toBeCalledWith(mockResponse.locals.responseData);
    expect(mockResponse.set).toHaveBeenNthCalledWith(1, headers.default);
    expect(mockResponse.set).toHaveBeenNthCalledWith(2, headers.path[mockRequest.route.path]);
    expect(mockResponse.set).toHaveBeenNthCalledWith(3, 'Content-Type', mockResponse.locals.responseContentType);
    expect(mockResponse.status).toBeCalledWith(mockResponse.locals.statusCode);
  });

  test('should set the Link next header and confirm it exists', async () => {
    const MOCK_URL = 'http://mock.url/next';
    mockResponse.locals.responseData.links.next = MOCK_URL;
    const assertNextValue = `<${MOCK_URL}>; rel=\"next\"`;
    await responseHandler(mockRequest, mockResponse, null);
    expect(mockResponse.set).toHaveBeenNthCalledWith(4, 'Link', assertNextValue);
  });

  test('should NOT set the Link next header and confirm it exists', async () => {
    await responseHandler(mockRequest, mockResponse, null);
    expect(mockResponse.set).toHaveBeenCalledTimes(3);
  });
});
