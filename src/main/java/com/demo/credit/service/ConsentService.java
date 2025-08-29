package com.demo.credit.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.tx.ReadonlyTransactionManager;
import org.web3j.tx.Contract;
import java.math.BigInteger;
import java.util.List;

@Service
public class ConsentService {
    private final Web3j web3j;
    private final String contract;

    public ConsentService(Web3j web3j, @Value("${web3.contract-address}") String contract) {
        this.web3j = web3j;
        this.contract = contract;
    }

    public boolean isActive(String consentIdHex) throws Exception {
        var func = new org.web3j.abi.datatypes.Function(
                "active",
                List.of(new Bytes32(org.web3j.utils.Numeric.hexStringToByteArray(consentIdHex))),
                List.of(new TypeReference<Bool>() {
                }));
        var txMgr = new ReadonlyTransactionManager(web3j, contract);
        var resp = Contract.executeCallSingleValueReturn(web3j, txMgr, contract, func, Bool.class);
        return resp.getValue();
    }
}
