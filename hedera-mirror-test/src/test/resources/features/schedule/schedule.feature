@ScheduleBase @FullSuite
Feature: Schedule Base Coverage Feature

    @Acceptance @Sanity
    Scenario Outline: Validate Base Schedule Flow - ScheduleCreate of CryptoTransfer and ScheduleSign
        Given I successfully schedule a treasury disbursement
        When the network confirms schedule presence
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        When the scheduled transaction is signed by the additionalAccount
        And the network confirms some signers have provided their signatures
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        When the scheduled transaction is signed by the tokenTreasuryAccount
        And the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the network confirms the schedule is not present
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        Examples:
            | httpStatusCode |
            | 200            |

    @Acceptance
    Scenario Outline: Validate Base Schedule Flow - ScheduleCreate of CryptoAccountCreate and ScheduleDelete
        Given I successfully schedule a crypto account create
        When the network confirms schedule presence
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        When I successfully delete the schedule
        And the network confirms the schedule is not present
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        Examples:
            | httpStatusCode |
            | 200            |

    @ScheduleSanity
    Scenario Outline: Validate Base Schedule Flow - MultiSig ScheduleCreate of CryptoAccountCreate and ScheduleDelete
        Given I successfully schedule a crypto account create with <initialSignatureCount> initial signatures
        When the network confirms schedule presence
        And the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        When I successfully delete the schedule
        And the network confirms the schedule is not present
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        Examples:
            | initialSignatureCount | httpStatusCode |
            | 3                     | 200            |
            | 10                    | 200            |

    @Acceptance
    Scenario Outline: Validate scheduled Hbar and Token transfer - ScheduleCreate of TokenTransfer and multi ScheduleSign
        Given I successfully schedule a token transfer
        And the network confirms schedule presence
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        When the scheduled transaction is signed by the additionalAccount
        And the network confirms some signers have provided their signatures
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        When the scheduled transaction is signed by the tokenTreasuryAccount
        And the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        When the network confirms the schedule is not present
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        Examples:
            | httpStatusCode |
            | 200            |

#    @Acceptance - sdk bug exists where executed HCS submit message fails
    Scenario Outline: Validate scheduled HCS message - ScheduleCreate of TopicMessageSubmit and ScheduleSign
        Given I successfully schedule a topic message submit
        And the network confirms schedule presence
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        When the scheduled transaction is signed by the additionalAccount
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        And the network confirms the schedule is not present
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        Examples:
            | httpStatusCode |
            | 200            |
