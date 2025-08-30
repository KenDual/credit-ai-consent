package com.demo.credit.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;

import org.web3j.crypto.Credentials;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;

import org.web3j.tx.ReadonlyTransactionManager;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.StaticGasProvider;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class ConsentService {

    private final Web3j web3;
    private final String contractAddress;
    private final Credentials creds;                // null nếu không có private key
    private final TransactionManager readOnly;      // dùng cho eth_call
    private final RawTransactionManager txManager;  // dùng để gửi tx (nếu có PK)
    private final ContractGasProvider gas;

    public ConsentService(
            @Value("${web3.rpc}") String rpc,
            @Value("${consent.contractAddress}") String contractAddress,
            @Value("${consent.privateKey:}") String pkEnv
    ) throws Exception {
        this.web3 = Web3j.build(new HttpService(rpc));
        this.contractAddress = contractAddress;
        if (contractAddress == null || contractAddress.isBlank()) {
            throw new IllegalArgumentException("consent.contractAddress is empty");
        }

        // from address cho eth_call có thể là bất kỳ địa chỉ hợp lệ
        String fromAddrForCall = "0x0000000000000000000000000000000000000001";
        this.readOnly = new ReadonlyTransactionManager(web3, fromAddrForCall);

        // Gas (Hardhat/Ganache thường ok với giá/limit này)
        this.gas = new StaticGasProvider(
                new BigInteger("20000000000"), // 20 gwei
                new BigInteger("8000000")      // 8M gas
        );

        // Tx manager (nếu có private key)
        if (pkEnv != null && !pkEnv.isBlank()) {
            this.creds = Credentials.create(pkEnv);
            long chainId;
            try {
                chainId = web3.ethChainId().send().getChainId().longValue();
            } catch (Exception e) {
                // fallback cho local dev (Hardhat: 31337)
                chainId = 31337L;
            }
            this.txManager = new RawTransactionManager(web3, creds, chainId);
        } else {
            this.creds = null;
            this.txManager = null;
        }
    }

    /** Gọi view: hasConsent(userId, purpose) -> bool (eth_call) */
    public boolean hasConsent(String userId, String purpose) throws Exception {
        Function fn = new Function(
                "hasConsent",
                Arrays.asList(new Utf8String(userId), new Utf8String(purpose)),
                Arrays.asList(new TypeReference<Bool>() {})
        );
        String data = FunctionEncoder.encode(fn);
        EthCall resp = web3.ethCall(
                Transaction.createEthCallTransaction(
                        // from (tuỳ ý), to (contract), data
                        "0x0000000000000000000000000000000000000001",
                        contractAddress,
                        data
                ),
                DefaultBlockParameterName.LATEST
        ).send();

        if (resp.isReverted()) return false;
        List<Type> out = FunctionReturnDecoder.decode(resp.getValue(), fn.getOutputParameters());
        if (out == null || out.isEmpty()) return false;
        return ((Bool) out.get(0)).getValue();
    }

    /** Ghi consent: grantConsent(userId, purpose, ttlSec) -> tx hash */
    public String recordConsent(String userId, String purpose, long ttlSec) throws Exception {
        if (txManager == null) {
            throw new IllegalStateException("Missing consent.privateKey; cannot send transaction");
        }
        Function fn = new Function(
                "grantConsent",
                Arrays.asList(
                        new Utf8String(userId),
                        new Utf8String(purpose),
                        new Uint256(BigInteger.valueOf(ttlSec))
                ),
                Collections.emptyList()
        );
        String data = FunctionEncoder.encode(fn);

        EthSendTransaction tx = txManager.sendTransaction(
                gas.getGasPrice("grantConsent"),
                gas.getGasLimit("grantConsent"),
                contractAddress,
                data,
                BigInteger.ZERO
        );
        if (tx.hasError()) {
            throw new RuntimeException("Tx error: " + tx.getError().getMessage());
        }
        return tx.getTransactionHash();
    }
    public String revokeConsent(String userId, String purpose) throws Exception {
        if (txManager == null) {
            throw new IllegalStateException("Missing consent.privateKey; cannot send transaction");
        }
        org.web3j.abi.datatypes.Function fn = new org.web3j.abi.datatypes.Function(
                "revokeConsent",
                java.util.Arrays.asList(new org.web3j.abi.datatypes.Utf8String(userId),
                        new org.web3j.abi.datatypes.Utf8String(purpose)),
                java.util.Collections.emptyList()
        );
        String data = org.web3j.abi.FunctionEncoder.encode(fn);
        var tx = txManager.sendTransaction(
                gas.getGasPrice("revokeConsent"),
                gas.getGasLimit("revokeConsent"),
                contractAddress,
                data,
                java.math.BigInteger.ZERO
        );
        if (tx.hasError()) throw new RuntimeException("Tx error: " + tx.getError().getMessage());
        return tx.getTransactionHash();
    }
}
