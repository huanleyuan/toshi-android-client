package com.toshi.transaction;

import com.toshi.model.local.UnsignedW3Transaction;
import com.toshi.model.network.TransactionRequest;
import com.toshi.model.sofa.payment.Payment;
import com.toshi.model.sofa.SofaAdapters;
import com.toshi.util.paymentTask.TransactionRequestBuilder;

import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TransactionRequestTest {
    @Test
    public void testGenerateTransactionRequestFromUnsignedW3Tx() {
        try {
            final UnsignedW3Transaction unsignedW3Transaction = SofaAdapters.get().unsignedW3TransactionFrom("{\"from\":\"0x28731183c0229b19263cbfd928d445a25034ba4a\",\"to\":\"0x2bf743f85d9c8202dc07481251037176429ec8bd\",\"value\":\"0x7c6243e8bb3d1\",\"data\":\"0x\",\"gas\":\"0x5208\",\"gasPrice\":\"0x5410\",\"nonce\":\"0x7\"}");
            final TransactionRequest transactionRequest = new TransactionRequestBuilder().generateTransactionRequest(unsignedW3Transaction);

            assertThat(transactionRequest.getValue(), is("0x7c6243e8bb3d1"));
            assertThat(transactionRequest.getFrom(), is("0x28731183c0229b19263cbfd928d445a25034ba4a"));
            assertThat(transactionRequest.getTo(), is("0x2bf743f85d9c8202dc07481251037176429ec8bd"));
            assertThat(transactionRequest.getData(), is("0x"));
            assertThat(transactionRequest.getGas(), is("0x5208"));
            assertThat(transactionRequest.getGasPrice(), is("0x5410"));
            assertThat(transactionRequest.getNonce(), is("0x7"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testGenerateTransactionRequestFromPayment() {
        final Payment payment = new Payment()
                .setFromAddress("0x28731183c0229b19263cbfd928d445a25034ba4a")
                .setToAddress("0x011c6dd9565b8b83e6a9ee3f06e89ece3251ef2f")
                .setValue("0x53c4a825b0578");
        final TransactionRequest transactionRequest = new TransactionRequestBuilder().generateTransactionRequest(payment);

        assertThat(transactionRequest.getFrom(), is("0x28731183c0229b19263cbfd928d445a25034ba4a"));
        assertThat(transactionRequest.getTo(), is("0x011c6dd9565b8b83e6a9ee3f06e89ece3251ef2f"));
        assertThat(transactionRequest.getValue(), is("0x53c4a825b0578"));
    }
}
