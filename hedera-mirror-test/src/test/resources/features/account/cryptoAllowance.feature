@cryptoallowance @allowance @fullsuite
Feature: Account Crypto Allowance Coverage Feature

    @critical @release @acceptance
    Scenario Outline: Validate approval CryptoTransfer
        Given I approve <spender> to transfer up to <approvedAmount> tℏ
        Then the mirror node REST API should confirm the approved <approvedAmount> tℏ crypto allowance
        When <spender> transfers <transferAmount> tℏ from the approved allowance to <recipient>
        Then the mirror node REST API should confirm the approved transfer of <transferAmount> tℏ
        When I delete the crypto allowance for <spender>
        Then the mirror node REST API should confirm the crypto allowance deletion
        Examples:
            | spender | approvedAmount | recipient | transferAmount |
            | "BOB"   | 10000          | "ALICE"   | 2500           |
