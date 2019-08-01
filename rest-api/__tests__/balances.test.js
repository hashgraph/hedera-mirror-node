const request = require('supertest');
const server = require('../server');

beforeAll(async () => {
    console.log('Jest starting!');
    jest.setTimeout(10000);
});

afterAll(() => {
    // console.log('server closed!');
});

describe('/balances tests', () => {
    let testAccountNum;
    let testBalanceTs;
    let apiPrefix = '/api/v1';

    test('Get balances with no parameters', async () => {
        const response = await request(server).get(apiPrefix + '/balances');
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;

        testBalanceTs = balances[0].seconds;
        testAccountNum = balances[0].accountbalances[0].account.replace('0.0.', '');;

        expect(balances[0].accountbalances.length).toBeGreaterThan(10);
    });

    test('Get balances with limit parameters', async () => {
        const response = await request(server).get(apiPrefix + '/balances?limit=10');
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;
        expect(balances[0].accountbalances.length).toEqual(10);

    });

    test('Get balances with timestamp & limit parameters', async () => {
        const response = await request(server).get(apiPrefix + '/balances' +
            '?timestamp=gt:' + (testBalanceTs - 1) +
            '&timestamp=lt:' + (testBalanceTs + 1) + '&limit=1');
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;
        expect(balances[0].accountbalances.length).toEqual(1);

    });

    test('Get balances with account id parameters', async () => {
        const response = await request(server).get(apiPrefix + '/balances' +
            '?account.id=' + testAccountNum + '&limit=1');
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;
        expect(balances[0].accountbalances.length).toEqual(1);
    });
});
