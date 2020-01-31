@TopicMessagesBase @FullSuite
Feature: HCS Base Coverage Feature

    Background: User has clients
        Given Config context is loaded
        And User obtained SDK client
        Given User obtained Mirror Node Consensus client
        Then all setup items were configured

    @Sanity
    Scenario Outline: Validate Topic message submission
        Given I attempt to create a new topic id
        And I publish <numMessages> messages
        When I provide a number of messages <numMessages> I want to receive
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | numMessages |
            | 1           |
            | 7           |

    Scenario: Validate Topic Updates
        Given I attempt to create a new topic id
        When I attempt to update an existing topic
        Then the network should confirm valid transaction receipts for this operation

    Scenario Outline: Validate Topic message listener
        Given I attempt to create a new topic id
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
            | topicId | errorCode                  |
            | ""      | "Missing required topicID" |

    @Negative
    Scenario Outline: Validate topic subscription with invalid topic id
        Given I provide a topic id <topicId>
        Then the network should observe an error <errorCode>
        Examples:
            | topicId | errorCode                                                                              |
            | "-1"    | "INVALID_ARGUMENT: subscribeTopic.filter.topicNum: must be greater than or equal to 0" |

#    # Discussions still out on this
#    Scenario Outline: Validate topic subscription with no matching topic id
#        Given I provide a topic id <topicId>
#        Then the network should observe an error <errorCode>
#        Examples:
#            | topicId   | errorCode |
#            | "0"         | "bgf"     |
#            | "123456789" | "bgfbg"   |

    #Verified
    Scenario: Validate topic deletion
        Given I attempt to create a new topic id
        When I attempt to delete the topic
        Then the network should confirm valid transaction receipts for this operation

