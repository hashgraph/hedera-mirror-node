@cryptoallowance @allowance @fullsuite
Feature: Account Crypto Allowance Coverage Feature

    @critical @release @acceptance
    Scenario Outline: Validate approval CryptoTransfer
        Given I approve <senderName> to transfer up to <approvedAmount> tℏ
        Then the mirror node REST API should confirm the approved <approvedAmount> tℏ crypto transfer allowance
        When <senderName> transfers <transferAmount> tℏ from their approved balance to <recipientName>
        Then the mirror node REST API should return status <httpStatusCode> for the crypto transfer transaction
        When I delete the crypto allowance for <senderName>
        Then the mirror node REST API should confirm the crypto allowance deletion
        Examples:
            | senderName | approvedAmount | recipientName | transferAmount | httpStatusCode |
            | "ALICE"    | 10000          | "BOB"         | 2500           | 200            |
