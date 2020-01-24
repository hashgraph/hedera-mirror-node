@TopicMessages
Feature: HCS Coverage Feature

    Background: User has clients
        Given User obtained SDK client
        Given User obtained Mirror Node Consensus client
        Given I attempt to create a new topic id
        Then all setup items were configured

    #Verified
    Scenario Outline: Validate Topic message submission
        Given I provide a number of messages <numMessages> I want to receive
        When I publish random messages
        Then the network should confirm valid transaction receipts for this operation
        Examples:
            | numMessages |
            | 1           |
            | 7           |

#    Verified, then unauthorized
#    Scenario: Validate Topic Updates
#        When I attempt to update an existing topic
#        Then the network should confirm valid transaction receipts for this operation

    #Verified
    Scenario Outline: Validate Topic message listener
        Given I provide a number of messages <numMessages> I want to receive within <latency> seconds
        When I publish random messages
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | numMessages | latency |
            | 2           | 30      |
            | 5           | 30      |

    #Verified
    Scenario Outline: Validate topic filtering with past date and get X previous
        Given I provide a date <startDate> and a number of messages <numMessages> I want to receive
        When I publish random messages
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | startDate                 | numMessages |
            | "1970-01-01T00:00:00.00Z" | 2           |
            | "2000-01-01T00:00:00.00Z" | 5           |

    #Verified
    Scenario Outline: Validate resubscribe topic filtering
        Given I provide a date <startDate> and a number of messages <numMessages> I want to receive
        When I publish random messages
        And I subscribe with a filter to retrieve messages
        And I unsubscribe from a topic
        And I subscribe with a filter to retrieve messages
        Then the network should successfully observe these messages
        Examples:
            | startDate                 | numMessages |
            | "1970-01-01T00:00:00.00Z" | 2           |
            | "2000-01-01T00:00:00.00Z" | 5           |

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
#        Given I provide a topic id <topicId>
#        Then the network should successfully establish a channel to this topic
#        Examples:
#            | topicId |
#            | 1175       |


# Potential bug
#    Scenario Outline: Validate Re-subscribe with invalid topic id
#        Given I provide a topic id <topicId>
#        Then the network should successfully establish a channel to this topic
#        Examples:
#            | topicId |
#            | 0       |
#            | -1      |
#            | -11     |

#    # Unauthorized error
#    Scenario: Validate topic deletion
#        When I attempt to delete the topic
#        Then the network should confirm valid transaction receipts for this operation

    # Must be last scenario in file
    @TopicClientClose
    Scenario: Client close place holder

