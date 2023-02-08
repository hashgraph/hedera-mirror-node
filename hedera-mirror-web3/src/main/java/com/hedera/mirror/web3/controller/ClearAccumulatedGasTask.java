package com.hedera.mirror.web3.controller;

import java.util.TimerTask;

public class ClearAccumulatedGasTask extends TimerTask {

    @Override
    public void run() {
        ContractController.clearAccumulatedGas();
    }
}
