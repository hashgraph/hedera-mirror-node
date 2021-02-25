@ScheduleBase @FullSuite
Feature: Schedule Base Coverage Feature
  # inner transaction - 1 of 3 transfers - pure HBAR, HBAR and token, HCS message for inner transactions. 2 or 3 signatures
  #outer - 3 sigs or 10 signatures
  #Negative - pending

    @Acceptance @Sanity
    Scenario Outline: Validate Base Schedule Flow - Create
        Given I successfully schedule a treasury disbursement
        When the network confirms schedule presence
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        Examples:
            | httpStatusCode |
            | 200            |

    @Acceptance
    Scenario Outline: Validate scheduled Hbar and Token transfer - Create and Sign
        Given I successfully schedule a token transfer
        And the network confirms schedule presence
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        When the scheduled transaction is signed by the additionalAccount
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the scheduled transaction is signed by the tokenTreasuryAccount
        And the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        Then the network confirms all signers have provided their signatures
        Examples:
            | httpStatusCode |
            | 200            |

    @Acceptance @ScheduleSanity
    Scenario Outline: Validate scheduled HCS message - Create and Sign
        Given I successfully schedule a topic message submit
        And the network confirms schedule presence
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        When the scheduled transaction is signed by the additionalAccount
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the scheduled transaction is signed by the tokenTreasuryAccount
        And the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        Then the network confirms all signers have provided their signatures
        Examples:
            | httpStatusCode |
            | 200            |
