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
        const response = await request(server).get(apiPrefix + '/balances/history');
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;

        testBalanceTs = balances[0].seconds;
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
        const response = await request(server).get(apiPrefix + '/balances/history' +
            '?timestamp=gt:' + (testBalanceTs - 1) +
            '&timestamp=lt:' + (testBalanceTs + 1) + '&limit=1');
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
                    //let extraParams = tsOptions + accOptions + orderOptions;
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
                        if ((orderOptions === 'order=asc' && balances[1].seconds <= balances[0].seconds) ||
                        (orderOptions !== 'order=asc' && balances[1].seconds >= balances[0].seconds)) {
                            check = false;
                        }
                    }
                    expect(check).toBeTruthy();

                    // Check the freshness of the data - should be in the past 1 hour
                    const asofSeconds = Number(balances[0].seconds)
                    const currentSeconds = Math.floor(new Date().getTime()/1000) - 3600;
                    console.log ("As of sec:" + asofSeconds + " > " + currentSeconds);
                    console.log ("As of: " + new Date(asofSeconds * 1000));
                    console.log ("Expected: " + new Date(currentSeconds * 1000));
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
                    //let extraParams = tsOptions + accOptions + orderOptions;
                    let extraParams = accOptions;
                    extraParams += ((extraParams !== '' && orderOptions !== '') ? '&' : '') + orderOptions;

                    console.log(apiPrefix + '/balances' + (extraParams === '' ? '' : '?') + extraParams);
                    let response = await request(server).get(apiPrefix + '/balances' + (extraParams === '' ? '' : '?') + extraParams);
                    expect(response.status).toEqual(200);

                    const asOf = JSON.parse(response.text).asOf;
                    const currentSeconds = Math.floor(new Date().getTime()/1000) - 3600;
                    expect(Number(asOf.seconds)).toBeGreaterThan(currentSeconds);

                    let balances = JSON.parse(response.text).balances;
                    expect(balances.length).toEqual(1000);

                    // var numbalances = balances.reduce(function (accumulator, b) {
                    //     return accumulator + b.balances.length;
                    //   }, 0);
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

                        console.log ("firstAcc: " + firstAcc);
                        console.log ("secondAcc: " + secondAcc);
                        console.log ("order: " + orderOptions);
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
