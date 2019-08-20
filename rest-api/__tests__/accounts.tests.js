const request = require('supertest');
const math = require('mathjs');
const server = require('../server');
const utils = require('../utils.js');

beforeAll(async () => {
    console.log('Jest starting!');
    jest.setTimeout(10000 * 3);
});

afterAll(() => {
    // console.log('server closed!');
});

describe('/accounts tests', () => {
    let testAccountNum;
    let testBalanceTsNs;
    let testBalanceAmount;

    let apiPrefix = '/api/v1';

    test('Get accounts with no parameters', async () => {
        const response = await request(server).get(apiPrefix + '/accounts');
        expect(response.status).toEqual(200);
        let accounts = JSON.parse(response.text).accounts;

        testBalanceTsNs = accounts[0].balance.timestamp;
        testBalanceAmount = accounts[0].balance.balance;

        testAccountNum = accounts[0].account.replace('0.0.', '');
        console.log("testBalanceTsNs: [", testBalanceTsNs, ']');

        expect(accounts.length).toBe(1000);
    });

    test('Get accounts with limit parameters', async () => {
        const response = await request(server).get(apiPrefix + '/accounts?limit=10');
        expect(response.status).toEqual(200);
        let accounts = JSON.parse(response.text).accounts;

        expect(accounts.length).toEqual(10);
    });

    test('Get accounts with balance parameters', async () => {
        let plusOne = testBalanceAmount + 1;
        let minusOne = testBalanceAmount - 1;
        const response = await request(server).get(apiPrefix + '/accounts' +
            '?balance=gt:' + minusOne +
            '&balance=lt:' + plusOne + '&limit=1');
        expect(response.status).toEqual(200);
        let accounts = JSON.parse(response.text).accounts;

        expect(accounts.length).toEqual(1);
    });

    test('Get accounts with account id parameters', async () => {
        const response = await request(server).get(apiPrefix + '/accounts' +
            '?account.id=' + testAccountNum + '&limit=1');
        expect(response.status).toEqual(200);
        let accounts = JSON.parse(response.text).accounts;
        expect(accounts.length).toEqual(1);
    });

    for (let balanceOptions of ['', 'balance=gt:100', 'balance=gt:100&balance=lte:10000000000000']) {
        for (let accOptions of ['', 'account.id=gte:1', 'account.id=gte:1&account.id=lt:99999999']) {
            for (let orderOptions of ['', 'order=desc', 'order=asc']) {

                test('/accounts tests with options: ' +
                    '[' + balanceOptions + ' - ' + accOptions + ' - ' + orderOptions + ']', async () => {
                        let extraParams = balanceOptions;
                        extraParams += ((extraParams !== '' && accOptions !== '') ? '&' : '') + accOptions;
                        extraParams += ((extraParams !== '' && orderOptions !== '') ? '&' : '') + orderOptions;

                        console.log(apiPrefix + '/accounts' + (extraParams === '' ? '' : '?') + extraParams);
                        let response = await request(server).get(apiPrefix + '/accounts' + (extraParams === '' ? '' : '?') + extraParams);
                        expect(response.status).toEqual(200);
                        let accounts = JSON.parse(response.text).accounts;
                        expect(accounts.length).toBeGreaterThan(100);

                        let next = JSON.parse(response.text).links.next;

                        if (next !== null) {
                            next = next.replace(new RegExp('^.*' + apiPrefix), apiPrefix);
                            response = await request(server).get(next);
                            expect(response.status).toEqual(200);
                            accounts = JSON.parse(response.text).accounts;
                            expect(accounts.length).toBeGreaterThan(10);
                        }

                        check = true;
                        if (accounts.length > 1) {
                            const first = accounts[0].account.split('.')[2];
                            const second = accounts[1].account.split('.')[2];
                            if ((orderOptions === 'order=desc' && first <= second) ||
                                (orderOptions !== 'order=desc' && first >= second)) {
                                check = false;
                            }
                        }
                        expect(check).toBeTruthy();

                        // Check the freshness of the data - should be in the past 1 hour
                        const asofSeconds = math.ceil(accounts[0].balance.timestamp);
                        const minusTenMinutes = Math.floor(new Date().getTime() / 1000) - (60 * 10);
                        const plusTenMinutes = Math.floor(new Date().getTime() / 1000) + (60 * 10);

                        expect(plusTenMinutes).toBeGreaterThan(asofSeconds);
                        expect(minusTenMinutes).toBeLessThan(asofSeconds);
                    });
            }
        }
    }
});
