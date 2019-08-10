#!/bin/bash
java -Dlog4j.configurationFile=./config/log4j2.xml -cp mirrorNode.jar com.hedera.addressBook.NetworkAddressBook
rm .102env
