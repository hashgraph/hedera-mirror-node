const request = require('supertest');
const math = require('mathjs');
const config = require('../config.js');
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

    for (let accOptions of ['', 'account.id=gte:1', 'account.id=gte:1&account.id=lt:99999999']) {
        for (let balanceOptions of ['', 'account.balance=gte:1000', 'account.balance=lt:10000000000000']) {
            for (let tsOptions of ['', 'timestamp=gt:1560300000', 'timestamp=gt:100&timestamp=lte:5000000000']) {
                for (let orderOptions of ['', 'order=desc', 'order=asc']) {

                    test('/balances tests with options: ' +
                        '[' + ' - ' + accOptions + ' - ' + orderOptions + ']', async () => {
                            let extraParams = accOptions;
                            extraParams += ((extraParams !== '' && balanceOptions !== '') ? '&' : '') + balanceOptions;
                            extraParams += ((extraParams !== '' && tsOptions !== '') ? '&' : '') + tsOptions;
                            extraParams += ((extraParams !== '' && orderOptions !== '') ? '&' : '') + orderOptions;


                            console.log(apiPrefix + '/balances' + (extraParams === '' ? '' : '?') + extraParams);
                            let response = await request(server).get(apiPrefix + '/balances' + (extraParams === '' ? '' : '?') + extraParams);
                            expect(response.status).toEqual(200);

                            const currentSeconds = Math.floor(new Date().getTime() / 1000) - (60 * 60);
                            expect(utils.secNsToSeconds(JSON.parse(response.text).timestamp)).toBeGreaterThan(currentSeconds);

                            let balances = JSON.parse(response.text).balances;
                            expect(balances.length).toEqual(1000);

                            // Validate that pagination works and that it doesn't have any gaps
                            const bigPageEntries = balances;
                            let paginatedEntries = [];
                            const numPages = 5;
                            const pageSize = config.limits.RESPONSE_ROWS / numPages;
                            const firstAcc = orderOptions === 'order=asc' ?
                                balances[balances.length - 1].account :
                                balances[0].account;
                            for (let index = 0; index < numPages; index++) {
                                const nextUrl = paginatedEntries.length === 0 ?
                                    apiPrefix + '/balances' +
                                    (extraParams === '' ? '' : '?') + extraParams +
                                    (extraParams === '' ? '?' : '&') + 'account.id=lte:' + firstAcc +
                                    '&limit=' + pageSize :
                                    JSON.parse(response.text).links.next
                                        .replace(new RegExp('^.*' + apiPrefix), apiPrefix);
                                console.log(nextUrl);
                                response = await request(server).get(nextUrl);
                                expect(response.status).toEqual(200);
                                let chunk = JSON.parse(response.text).balances;
                                expect(chunk.length).toEqual(pageSize);
                                paginatedEntries = paginatedEntries.concat(chunk);

                                let next = JSON.parse(response.text).links.next;
                                expect(next).not.toBe(null);
                            }

                            check = (paginatedEntries.length === bigPageEntries.length);
                            expect(check).toBeTruthy();

                            check = true;
                            for (i = 0; i < config.limits.RESPONSE_ROWS; i++) {
                                if (bigPageEntries[i].account !== paginatedEntries[i].account ||
                                    bigPageEntries[i].balance !== paginatedEntries[i].balance) {
                                    check = false;
                                }
                            }
                            expect(check).toBeTruthy();

                            check = true;
                            if (balances.length > 1) {
                                const firstAcc = balances[0].account.split(".")[2];
                                const secondAcc = balances[1].account.split(".")[2];
                                if ((orderOptions === 'order=asc' && secondAcc < firstAcc) ||
                                    (orderOptions !== 'order=asc' && secondAcc > firstAcc)) {
                                    check = false;
                                }
                            }
                            expect(check).toBeTruthy();
                        });
                }
            }
        }
    }
});
