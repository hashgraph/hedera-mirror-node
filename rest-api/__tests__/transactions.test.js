const request = require('supertest');
const server = require('../server');

beforeAll(async () => {
    console.log('Jest starting!');
    jest.setTimeout(10000);
});

afterAll(() => {
    // console.log('server closed!');
});

describe('transaction tests', () => {
    let testAccountNum;
    let testAccountTs;
    let apiPrefix = '/api/v1';

    test('Get transactions with no parameters', async () => {
        const response = await request(server).get(apiPrefix + '/transactions');
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;

        testAccountNum = transactions[0].to_account;
        testAccountTs = transactions[0].seconds;

        expect(transactions.length).toBeGreaterThan(10);
    });

    test('Get transactions with limit parameters', async () => {
        const response = await request(server).get(apiPrefix + '/transactions?limit=10');
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;
        expect(transactions.length).toEqual(10);
    });

    test('Get transactions with timestamp & limit parameters', async () => {
        const response = await request(server).get(apiPrefix + '/transactions' +
            '?timestamp=gt:' + (testAccountTs - 1) +
            '&timestamp=lt:' + (testAccountTs + 1) + '&limit=1');
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;
        expect(transactions.length).toEqual(1);
    });

    test('Get transactions with account id parameters', async () => {
        console.log (apiPrefix + '/transactions' + 
	        '?account.id=' + testAccountNum + '&type=credit&limit=1');
        const response = await request(server).get(apiPrefix + '/transactions' +
            '?account.id=' + testAccountNum + '&type=credit&limit=1');
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;
        expect(transactions.length).toEqual(1);
        let to_account = transactions[0].to_account;
        let check = to_account == testAccountNum;
        expect(check).toBeTruthy();
    });
});
