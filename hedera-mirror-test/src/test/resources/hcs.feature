@TopicMessages
Feature: HCS Coverage Feature

    Background: User has clients
        Given User obtained SDK client
        Given User obtained Mirror Node Consensus client
        Then all clients are established

#Verified
#    Scenario Outline: Validate topic creation scenarios
#        Given I provide a memo <memo> and a max transaction fee of <maxFee>
#        When I attempt to create a new topic id
#        Then the network should confirm valid transaction receipts for this operation
#        Examples:
#            | memo          | maxFee   |
#            | "HCS topic 1" | 10000000 |
#            | "HCS topic 2" | 20000000 |

#Verified, then unauthorized
#    Scenario Outline: Validate Topic Updates
#        Given I provide a topic id <topicId>, memo <memo> and an auto renew period of <renewPeriod>
#        When I attempt to update an existing topic
#        Then the network should confirm valid transaction receipts for this operation
#        Examples:
#            | topicId | memo                    | renewPeriod |
#            | 1175    | "HCS topic 1 - updated" | 30          |
#            | 1176    | "HCS topic 2 - updated" | 60          |

#    #Verified
    Scenario Outline: Validate Topic subscription
        Given I provide a topic id <topicId>
        When I subscribe to the topic
        Then the network should successfully establish a channel to this topic
        Examples:
            | topicId |
            | 1175    |

#    #Verified
    Scenario Outline: Validate Topic message submission
        Given I provide a topic id <topicId>, a number of messages <numMessages>  and a sleep time between them <sleepBetweenMessages>
        When I publish random messages
        Then the network should confirm valid transaction receipts for this operation
        Examples:
            | topicId | numMessages | sleepBetweenMessages |
            | 1175    | 0           | 500                  |
            | 1175    | 1           | 500                  |
            | 1175    | 7           | 500                  |

        #Verified
    Scenario Outline: Validate Topic message listener
        Given I provide a topic id <topicId> and a number <numMessages> I want to receive within <latency> seconds
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | topicId | numMessages | latency |
            | 1175    | 0           | 5       |
            | 1175    | 2           | 2       |
            | 1175    | 5           | 5       |

    #Verified
    Scenario Outline: Validate topic filtering with past date and get X previous
        Given I provide a topic id <topicId> and a date <startDate> and a number <numMessages> I want to receive
        When I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | topicId | startDate                 | numMessages |
            | 1175    | "1970-01-01T00:00:00.00Z" | 0           |
            | 1175    | "2000-01-01T00:00:00.00Z" | 5           |

    #Verified
    Scenario Outline: Validate resubscribe topic filtering
        Given I provide a topic id <topicId> and a date <startDate> and a number <numMessages> I want to receive
        When I subscribe with a filter to retrieve messages
        And I unsubscribe from a topic
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | topicId | startDate                 | numMessages |
            | 1175    | "1970-01-01T00:00:00.00Z" | 0           |
            | 1175    | "2000-01-01T00:00:00.00Z" | 5           |

#   Unsupported w SDK right now
#    Scenario Outline: Validate topic filtering with start and end time in between min and max messages (e.g. if 100 messages get 25-30)
#        Given I provide a topic id <topicId> and a start <startDate> and end <endDate> date and a number <numMessages> I want to receive
#        Then the network should successfully observe these messages
#        Examples:
#            | topicId | startDate                 | endDate                   | numMessages |
#            | 1175       | "2020-01-01T00:00:00.00Z" | "2020-02-01T00:00:00.00Z" | 5           |
#
#   Unsupported w SDK right now
#    Scenario Outline: Validate topic filtering with past date and limit of 10
#        Given I provide a topic id <topicId> and a start <startDate> and a limit of <limit> messages I want to receive
#        Then the network should successfully observe these messages
#        Examples:
#            | topicId | startDate                 | limit |
#            | 1175       | "2020-01-01T00:00:00.00Z" | 5     |
#
#   Discussions still out on this
#    Scenario Outline: Validate topic filtering with missing topic id
#        Given I provide an invalid topic id <topicId>
#        Then the network should successfully connect
#        Examples:
#            | topicId |
#            | 1175       |
#

# Potential bug
#    Scenario Outline: Validate Re-subscribe with invalid topic id
#        Given I provide a topic id <topicId>
#        Then the network should successfully establish a channel to this topic
#        Examples:
#            | topicId |
#            | 0       |
#            | -1      |
#            | -11     |

# Unauthorized
#    Scenario Outline: Validate topic deletion
#        Given I provide a memo <memo> and a max transaction fee of <maxFee>
#        When I attempt to create a new topic id
#        And I attempt to delete the topic
#        Then the network should confirm valid transaction receipts for this operation
#        Examples:
#            | memo              | maxFee   |
#            | "HCS Del topic 1" | 10000000 |
#            | "HCS Del topic 2" | 20000000 |

