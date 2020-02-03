@TopicMessagesBase @FullSuite
Feature: HCS Base Coverage Feature

    Background: User has clients
        Given Config context is loaded
        And User obtained SDK client
        Given User obtained Mirror Node Consensus client
        Then all setup items were configured

    @Sanity
    Scenario Outline: Validate Topic message submission
        Given I successfully create a new topic id
        And I publish <numMessages> messages
        When I provide a number of messages <numMessages> I want to receive
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | numMessages |
            | 1           |
            | 7           |

    Scenario: Validate Topic Updates
        Given I successfully create a new topic id
        Then I successfully update an existing topic

    Scenario Outline: Validate Topic message listener
        Given I successfully create a new topic id
        And I publish <numMessages> messages
        When I provide a number of messages <numMessages> I want to receive within <latency> seconds
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | numMessages | latency |
            | 2           | 30      |
            | 5           | 30      |

    @Negative
    Scenario Outline: Validate topic subscription with missing topic id
        Given I provide a topic id <topicId>
        Then the network should observe an error <errorCode>
        Examples:
            | topicId | errorCode                                                                              |
            | ""      | "Missing required topicID"                                                             |
            | "-1"    | "INVALID_ARGUMENT: subscribeTopic.filter.topicNum: must be greater than or equal to 0" |

    Scenario: Validate topic deletion
        Given I successfully create a new topic id
        Then I successfully delete the topic

