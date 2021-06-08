@topicmessagesbase @fullsuite
Feature: HCS Base Coverage Feature

    @sanity @basicsubscribe @acceptance @extended
    Scenario Outline: Validate Topic message submission
        Given I successfully create a new topic id
        And I publish and verify <numMessages> messages sent
        When I provide a number of messages <numMessages> I want to receive
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | numMessages |
            | 10          |

    @opensubscribe @extended
    Scenario Outline: Validate Topic message submission to an open submit topic
        Given I successfully create a new open topic
        And I publish and verify <numMessages> messages sent
        When I provide a number of messages <numMessages> I want to receive
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | numMessages |
            | 2           |

    @subscribeonly @SubscribeOnly
    Scenario Outline: Validate topic message subscription only
        Given I provide a topic id <topicId>
        And I provide a starting timestamp <startTimestamp> and a number of messages <numMessages> I want to receive
        When I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | topicId | startTimestamp | numMessages |
            | ""      | "-86400"       | 5           |

    @publishonly
    Scenario Outline: Validate topic message subscription
        Given I provide a topic id <topicId>
        And I publish <numBatches> batches of <numMessages> messages every <milliSleep> milliseconds
        And I subscribe with a filter to retrieve these published messages
        Then the network should successfully observe these messages
        Examples:
            | topicId | numBatches | numMessages | milliSleep |
            | ""      | 2          | 3           | 2000       |

    @publishandverify
    Scenario Outline: Validate topic message subscription
        Given I provide a topic id <topicId>
        And I publish and verify <numMessages> messages sent
        And I subscribe with a filter to retrieve these published messages
        Then the network should successfully observe these messages
        Examples:
            | topicId  | numMessages |
            | "171231" | 340         |

    @updatetopic @acceptance @extended
    Scenario: Validate Topic Updates
        Given I successfully create a new topic id
        Then I successfully update an existing topic

    @deletetopic @acceptance @extended
    Scenario: Validate topic deletion
        Given I successfully create a new topic id
        Then I successfully delete the topic

    @latency @extended
    Scenario Outline: Validate Topic message listener latency
        Given I successfully create a new topic id
        And I publish and verify <numMessages> messages sent
        When I provide a number of messages <numMessages> I want to receive within <latency> seconds
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | numMessages | latency |
            | 2           | 30      |
            | 5           | 30      |

    @negative @extended
    Scenario Outline: Validate topic subscription with missing topic id
        Given I provide a topic id <topicId>
        Then the network should observe an error <errorCode>
        Examples:
            | topicId | errorCode                                                                              |
            | ""      | "Missing required topicID"                                                             |
            | "-1"    | "INVALID_ARGUMENT: subscribeTopic.filter.topicNum: must be greater than or equal to 0" |
