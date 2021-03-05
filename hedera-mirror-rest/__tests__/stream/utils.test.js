'use strict';

const long = require('long');
const {
  proto: {AccountID, TransactionID},
} = require('@hashgraph/proto/lib/proto');
const utils = require('../../stream/utils');

describe('utils protoTransactionIdToString', () => {
  const accountID = {
    shardNum: 0,
    realmNum: 0,
    accountNum: 1010,
  };

  const testSpecs = [
    {
      transactionId: TransactionID.create({
        accountID,
        transactionValidStart: {
          seconds: 193823,
          nanos: 0,
        },
      }),
      expected: '0.0.1010@193823.0',
    },
    {
      transactionId: TransactionID.create({
        accountID,
        transactionValidStart: {
          seconds: 193823,
          nanos: 999999999,
        },
      }),
      expected: '0.0.1010@193823.999999999',
    },
    {
      transactionId: TransactionID.create({
        accountID,
        transactionValidStart: {
          seconds: long.MAX_VALUE,
          nanos: 999999999,
        },
      }),
      expected: `0.0.1010@${long.MAX_VALUE.toString()}.999999999`,
    },
  ];

  testSpecs.forEach((testSpec) => {
    test(`expect output ${testSpec.expected}`, () => {
      expect(utils.protoTransactionIdToString(testSpec.transactionId)).toEqual(testSpec.expected);
    });
  });
});
