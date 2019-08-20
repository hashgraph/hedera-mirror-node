const request = require('supertest');
const math = require('mathjs');
const server = require('../server');
const utils = require('../utils.js');


beforeAll(async () => {
    console.log('Jest starting!');
    jest.setTimeout(10000);
});

afterAll(() => {
    // console.log('server closed!');
});

describe('transaction tests', () => {
    let testAccountNum;
    let testAccountTsNs;
    let apiPrefix = '/api/v1';

    test('Get transactions with no parameters', async () => {
        const response = await request(server).get(apiPrefix + '/transactions');
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;

        testAccountNum = null;
        for (let xfer of transactions[0].transfers) {
            if (xfer.amount > 0) {
                testAccountNum = xfer.account.split('.')[2];
            }
        }
        testAccountTsNs = transactions[0].consensus_timestamp;


        expect(transactions.length).toBeGreaterThan(10);
        expect(testAccountNum).toBeTruthy();
    });

    test('Get transactions with limit parameters', async () => {
        const response = await request(server).get(apiPrefix + '/transactions?limit=10');
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;
        expect(transactions.length).toEqual(10);
    });

    test('Get transactions with timestamp & limit parameters', async () => {
        let plusOne = math.add(math.bignumber(testAccountTsNs), math.bignumber(1));
        let minusOne = math.subtract(math.bignumber(testAccountTsNs), math.bignumber(1));
        const response = await request(server).get(apiPrefix + '/transactions' +
            '?timestamp=gt:' + minusOne.toString() +
            '&timestamp=lt:' + plusOne.toString() + '&limit=1');
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;
        expect(transactions.length).toEqual(1);
    });


    test('Get transactions with account id parameters', async () => {
        console.log(apiPrefix + '/transactions' +
            '?account.id=' + testAccountNum + '&type=credit&limit=1');
        const response = await request(server).get(apiPrefix + '/transactions' +
            '?account.id=' + testAccountNum + '&type=credit&limit=1');
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;
        expect(transactions.length).toEqual(1);
        let check = false;
        for (let xfer of transactions[0].transfers) {
            if (xfer.account.split('.')[2] === testAccountNum) {
                check = true;
            }
        }
        expect(check).toBeTruthy();
    });

    test('Get transactions with account id range parameters', async () => {
        let accLow = Math.max(0, Number(testAccountNum) - 1000);
        let accHigh = Math.min(Number.MAX_SAFE_INTEGER, Number(testAccountNum) + 1000);
        console.log(apiPrefix + '/transactions' +
            '?account.id=gt:' + accLow + '&account.id=lt:' + accHigh);
        const response = await request(server).get(apiPrefix + '/transactions' +
            '?account.id=gt:' + accLow + '&account.id=lt:' + accHigh);
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;
        expect(transactions.length).toEqual(1000);
        let check = true;
        for (let xfer of transactions[0].transfers) {
            let acc = xfer.account.split('.')[2];
            if (acc < accLow || acc > accHigh) {
                check = false;
            }
        }
        expect(check).toBeTruthy();

        let next = JSON.parse(response.text).links.next;
        expect(next).not.toBe(undefined);
    });

    for (let tsOptions of ['', 'timestamp=gt:1560300000', 'timestamp=gt:100&timestamp=lte:5000000000']) {
        for (let accOptions of ['', 'account.id=gte:1', 'account.id=gte:1&account.id=lt:99999999']) {
            for (let orderOptions of ['', 'order=desc', 'order=asc']) {

                test('/transactions tests with options: ' +
                    '[' + tsOptions + ' - ' + accOptions + ' - ' + orderOptions + ']', async () => {
                        let extraParams = tsOptions;
                        extraParams += ((extraParams !== '' && accOptions !== '') ? '&' : '') + accOptions;
                        extraParams += ((extraParams !== '' && orderOptions !== '') ? '&' : '') + orderOptions;

                        console.log(apiPrefix + '/transactions' + (extraParams === '' ? '' : '?') + extraParams);
                        let response = await request(server).get(apiPrefix + '/transactions' + (extraParams === '' ? '' : '?') + extraParams);
                        expect(response.status).toEqual(200);
                        let transactions = JSON.parse(response.text).transactions;
                        expect(transactions.length).toEqual(1000);

                        let check = true;
                        let prevSeconds = utils.secNsToSeconds(transactions[0].consensus_timestamp);

                        for (let txn of transactions) {
                            if ((orderOptions === 'order=asc' && utils.secNsToSeconds(txn.consensus_timestamp) < prevSeconds) ||
                                (orderOptions !== 'order=asc' && utils.secNsToSeconds(txn.consensus_timestamp) > prevSeconds)) {
                                check = false;
                            }
                            prevSeconds = utils.secNsToSeconds(txn.seconds);
                        }
                        expect(check).toBeTruthy();

                        let next = JSON.parse(response.text).links.next;
                        expect(next).not.toBe(null);

                        next = next.replace(new RegExp('^.*' + apiPrefix), apiPrefix);
                        response = await request(server).get(next);
                        expect(response.status).toEqual(200);
                        transactions = JSON.parse(response.text).transactions;
                        expect(transactions.length).toEqual(1000);

                        check = true;
                        for (let txn of transactions) {
                            if ((orderOptions === 'order=asc' && utils.secNsToSeconds(txn.seconds) < prevSeconds) ||
                                (orderOptions !== 'order=asc' && utils.secNsToSeconds(txn.seconds) > prevSeconds)) {
                                check = false;
                            }
                            prevSeconds = utils.secNsToSeconds(txn.seconds);
                        }
                        expect(check).toBeTruthy();

                    });
            }
        }
    }

});
