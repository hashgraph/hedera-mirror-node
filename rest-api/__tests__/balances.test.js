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

describe('/balances tests', () => {
    let testAccountNum;
    let testBalanceTsNs;
    let apiPrefix = '/api/v1';

    test('Get balances with no parameters', async () => {
        const response = await request(server).get(apiPrefix + '/balances/history');
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;

        testBalanceTsNs = balances[0].timestamp;
        testAccountNum = balances[0].accountbalances[0].account.replace('0.0.', '');;

        expect(balances[0].accountbalances.length).toBeGreaterThan(10);
    });

    test('Get balances with limit parameters', async () => {
        const response = await request(server).get(apiPrefix + '/balances/history?limit=10');
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;
        expect(balances[0].accountbalances.length).toEqual(10);

    });

    test('Get balances with timestamp & limit parameters', async () => {
        let plusOne = math.add(math.bignumber(testBalanceTsNs), math.bignumber(1));
        let minusOne = math.subtract(math.bignumber(testBalanceTsNs), math.bignumber(1));
        const response = await request(server).get(apiPrefix + '/balances/history' +
            '?timestamp=gt:' + minusOne.toString() +
            '&timestamp=lt:' + plusOne.toString() + '&limit=1');
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;
        expect(balances[0].accountbalances.length).toEqual(1);

    });

    test('Get balances with account id parameters', async () => {
        const response = await request(server).get(apiPrefix + '/balances/history' +
            '?account.id=' + testAccountNum + '&limit=1');
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;
        expect(balances[0].accountbalances.length).toEqual(1);
    });

    for (let tsOptions of ['', 'timestamp=gt:1560300000', 'timestamp=gt:100&timestamp=lte:5000000000']) {
        for (let accOptions of ['', 'account.id=gte:1', 'account.id=gte:1&account.id=lt:99999999']) {
            for (let orderOptions of ['', 'order=desc', 'order=asc']) {

                test('/balances/history tests with options: ' +
                    '[' + tsOptions + ' - ' + accOptions + ' - ' + orderOptions + ']', async () => {
                        let extraParams = tsOptions;
                        extraParams += ((extraParams !== '' && accOptions !== '') ? '&' : '') + accOptions;
                        extraParams += ((extraParams !== '' && orderOptions !== '') ? '&' : '') + orderOptions;

                        console.log(apiPrefix + '/balances/history' + (extraParams === '' ? '' : '?') + extraParams);
                        let response = await request(server).get(apiPrefix + '/balances/history' + (extraParams === '' ? '' : '?') + extraParams);
                        expect(response.status).toEqual(200);
                        let balances = JSON.parse(response.text).balances;
                        expect(balances[0].accountbalances.length).toEqual(1000);

                        let next = JSON.parse(response.text).links.next;
                        expect(next).not.toBe(null);

                        next = next.replace(new RegExp('^.*' + apiPrefix), apiPrefix);
                        response = await request(server).get(next);
                        expect(response.status).toEqual(200);
                        balances = JSON.parse(response.text).balances;

                        var numbalances = balances.reduce(function (accumulator, b) {
                            return accumulator + b.accountbalances.length;
                        }, 0);
                        expect(numbalances).toEqual(1000);

                        check = true;
                        if (balances.length > 1) {
                            if ((orderOptions === 'order=asc' && utils.secNsToSeconds(balances[1].timestamp) <= utils.secNsToSeconds(balances[0].timestamp)) ||
                                (orderOptions !== 'order=asc' && utils.secNsToSeconds(balances[1].timestamp) >= utils.secNsToSeconds(balances[0].timestamp))) {
                                check = false;
                            }
                        }
                        expect(check).toBeTruthy();

                        // Check the freshness of the data - should be in the past 1 hour
                        const asofSeconds = utils.secNsToSeconds(balances[0].timestamp)
                        const currentSeconds = Math.floor(new Date().getTime() / 1000) - 3600;

                        if (orderOptions === 'order=asc') {
                            expect(asofSeconds).toBeLessThan(currentSeconds);
                        } else {
                            expect(asofSeconds).toBeGreaterThan(currentSeconds);
                        }
                    });
            }
        }
    }

    for (let accOptions of ['', 'account.id=gte:1', 'account.id=gte:1&account.id=lt:99999999']) {
        for (let orderOptions of ['', 'order=desc', 'order=asc']) {

            test('/balances tests with options: ' +
                '[' + ' - ' + accOptions + ' - ' + orderOptions + ']', async () => {
                    let extraParams = accOptions;
                    extraParams += ((extraParams !== '' && orderOptions !== '') ? '&' : '') + orderOptions;

                    console.log(apiPrefix + '/balances' + (extraParams === '' ? '' : '?') + extraParams);
                    let response = await request(server).get(apiPrefix + '/balances' + (extraParams === '' ? '' : '?') + extraParams);
                    expect(response.status).toEqual(200);

                    const currentSeconds = Math.floor(new Date().getTime() / 1000) - 3600;
                    expect(utils.secNsToSeconds(JSON.parse(response.text).timestamp)).toBeGreaterThan(currentSeconds);

                    let balances = JSON.parse(response.text).balances;
                    expect(balances.length).toEqual(1000);

                    let next = JSON.parse(response.text).links.next;
                    expect(next).not.toBe(null);

                    next = next.replace(new RegExp('^.*' + apiPrefix), apiPrefix);
                    response = await request(server).get(next);
                    expect(response.status).toEqual(200);
                    balances = JSON.parse(response.text).balances;

                    check = true;
                    if (balances.length > 1) {
                        const firstAcc = balances[0].account.split(".")[2];
                        const secondAcc = balances[1].account.split(".")[2];
                        if ((orderOptions === 'order=desc' && secondAcc >= firstAcc) ||
                            (orderOptions !== 'order=desc' && secondAcc <= firstAcc)) {
                            check = false;
                        }
                    }
                    expect(check).toBeTruthy();

                });
        }
    }
});
