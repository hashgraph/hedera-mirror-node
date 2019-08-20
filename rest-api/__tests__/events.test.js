const request = require('supertest');
const math = require('mathjs');
const server = require('../server');
//const utils = require('../utils');

beforeAll(async () => {
    console.log('Jest starting!');
    jest.setTimeout(10000);
});

afterAll(() => {
    // console.log('server closed!');
});

describe('events tests', () => {
    let testNodeNum;
    let testConsensusTs;
    let apiPrefix = '/api/v1';

    test('Get events with no parameters', async () => {
        const response = await request(server).get(apiPrefix + '/events');
        expect(response.status).toEqual(200);
        let events = JSON.parse(response.text).events;

        testNodeNum = Number(events[0].creator_node_id);
        testConsensusTs = Number(events[0].consensus_timestamp);

        expect(events.length).toBe(1000);
        expect(testNodeNum).toBeLessThan(39);

        let next = JSON.parse(response.text).links.next;
        expect(next).not.toBe(undefined);
    });

    test('Get events with limit parameters', async () => {
        const response = await request(server).get(apiPrefix + '/events?limit=10');
        expect(response.status).toEqual(200);
        let events = JSON.parse(response.text).events;
        expect(events.length).toEqual(10);
    });

    test('Get events with timestamp & limit parameters', async () => {
        const response = await request(server).get(apiPrefix + '/events' +
            '?timestamp=gt:' + (testConsensusTs - 1) +
            '&timestamp=lt:' + (testConsensusTs + 1) + '&limit=1');
        expect(response.status).toEqual(200);
        let events = JSON.parse(response.text).events;
        expect(events.length).toEqual(1);
    });

    test('Get events with creatornode parameters', async () => {
        const response = await request(server).get(apiPrefix + '/events' +
            '?creatornode=' + testNodeNum);
        expect(response.status).toEqual(200);
        let events = JSON.parse(response.text).events;
        expect(events.length).toBeGreaterThan(0);
        let check = true;
        for (let e of events) {
            if (e.creator_node_id != testNodeNum) {
                check = false;
            }
        }
        expect(check).toBeTruthy();
    });

    let endTsNs = Math.round(new Date().getTime() / 1000);
    let startTsNs = endTsNs - (30 * 24 * 60 * 60);


    for (let tsOptions of ['', 'timestamp=gt:1560300000', 'timestamp=gt:' + startTsNs + '&timestamp=lte:' + endTsNs]) {
        for (let nodeOptions of ['', 'creatornode=gte:0', 'creatornode=gte:0&creatornode=lt:13']) {
            for (let orderOptions of ['', 'order=desc', 'order=asc']) {
                test('/events tests with options: ' +
                    '[' + tsOptions + ' - ' + nodeOptions + ' - ' + orderOptions + ']', async () => {
                        let extraParams = tsOptions;
                        extraParams += ((extraParams !== '' && nodeOptions !== '') ? '&' : '') + nodeOptions;
                        extraParams += ((extraParams !== '' && orderOptions !== '') ? '&' : '') + orderOptions;

                        console.log(apiPrefix + '/events' + (extraParams === '' ? '' : '?') + extraParams);
                        let response = await request(server).get(apiPrefix + '/events' + (extraParams === '' ? '' : '?') + extraParams);
                        expect(response.status).toEqual(200);
                        let events = JSON.parse(response.text).events;
                        expect(events.length).toEqual(1000);

                        let check = true;
                        let prevNs = events[0].consensus_timestamp_ns;

                        for (let e of events) {
                            if ((orderOptions === 'order=asc' && e.consensus_timestamp_ns < prevNs) ||
                                (orderOptions !== 'order=asc' && e.consensus_timestamp_ns > prevNs)) {
                                check = false;
                            }
                            prevNs = e.consensus_timestamp_ns;
                        }
                        expect(check).toBeTruthy();

                        let next = JSON.parse(response.text).links.next;
                        expect(next).not.toBe(null);

                        next = next.replace(new RegExp('^.*' + apiPrefix), apiPrefix);
                        response = await request(server).get(next);
                        expect(response.status).toEqual(200);
                        events = JSON.parse(response.text).events;
                        expect(events.length).toEqual(1000);

                        check = true;
                        for (let e of events) {
                            if ((orderOptions === 'order=asc' && e.consensus_timestamp_ns < prevNs) ||
                                (orderOptions !== 'order=asc' && e.consensus_timestamp_ns > prevNs)) {
                                check = false;
                            }
                            prevNs = e.consensus_timestamp_ns;
                        }
                        expect(check).toBeTruthy();
                    });
            }
        }
    }

});
