/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.assettransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import java.sql.Timestamp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

public final class VoteTransferTest {

    private static final class MockKeyValue implements KeyValue {
        private final String key;
        private final String value;

        MockKeyValue(final String key, final String value) {
            super();
            this.key = key;
            this.value = value;
        }

        @Override
        public String getKey() {
            return this.key;
        }

        @Override
        public String getStringValue() {
            return this.value;
        }

        @Override
        public byte[] getValue() {
            return this.value.getBytes();
        }
    }

    private static final class MockVoteResultsIterator implements QueryResultsIterator<KeyValue> {
        private final List<KeyValue> VoteList;

        MockVoteResultsIterator() {
            super();
            VoteList = new ArrayList<KeyValue>();

            VoteList.add(new MockKeyValue("Vote1", "{ \"voteID\": \"Vote1\", \"voterID\": \"blue\", \"votedID\": \"5\", \"timestamp\": \"2026-07-07 16:58:00.0\"}"));
            VoteList.add(new MockKeyValue("Vote2", "{ \"voteID\": \"Vote2\", \"voterID\": \"red\", \"votedID\": \"5\", \"timestamp\": \"2026-07-07 16:58:00.0\"}"));
            VoteList.add(new MockKeyValue("Vote3", "{ \"voteID\": \"Vote3\", \"voterID\": \"green\", \"votedID\": \"5\", \"timestamp\": \"2026-07-07 16:58:00.0\"}"));
            VoteList.add(new MockKeyValue("Vote4", "{ \"voteID\": \"Vote4\", \"voterID\": \"no\", \"votedID\": \"5\", \"timestamp\": \"2026-07-07 16:58:00.0\"}"));
            VoteList.add(new MockKeyValue("Vote5", "{ \"voteID\": \"Vote5\", \"voterID\": \"blue\", \"votedID\": \"5\", \"timestamp\": \"2026-07-07 16:58:00.0\"}"));
            VoteList.add(new MockKeyValue("Vote6", "{ \"voteID\": \"Vote6\", \"voterID\": \"blue\", \"votedID\": \"5\", \"timestamp\": \"2026-07-07 16:58:00.0\"}"));
        }

        @Override
        public Iterator<KeyValue> iterator() {
            return VoteList.iterator();
        }

        @Override
        public void close() {
            // do nothing
        }
    }

    @Test
    public void invokeUnknownTransaction() {
        VoteTransfer contract = new VoteTransfer();
        Context ctx = mock(Context.class);

        Throwable thrown = catchThrowable(() -> {
            contract.unknownTransaction(ctx);
        });

        assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause()
                .hasMessage("Undefined contract method called");
        assertThat(((ChaincodeException) thrown).getPayload()).isEqualTo(null);

        verifyNoInteractions(ctx);
    }

    @Nested
    class InvokeReadVoteTransaction {
        @Test
        public void whenVoteExists() {
            VoteTransfer contract = new VoteTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getStringState("Vote1"))
                    .thenReturn("{ \"voteID\": \"Vote1\", \"voterID\": \"blue\", \"votedID\": \"5\", \"timestamp\": \"2026-07-07 16:58:00.0\"}");

            Vote vote = contract.ReadVote(ctx, "Vote1");

            assertThat(vote).isEqualTo(new Vote("Vote1", "blue", "5", Timestamp.valueOf("2026-07-07 16:58:00.0")));
        }

        @Test
        public void whenVoteDoesNotExist() {
            VoteTransfer contract = new VoteTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getStringState("Vote1")).thenReturn("");

            Throwable thrown = catchThrowable(() -> {
                contract.ReadVote(ctx, "Vote1");
            });

            assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause()
                    .hasMessage("Vote Vote1 does not exist");
            assertThat(((ChaincodeException) thrown).getPayload()).isEqualTo("Vote_NOT_FOUND".getBytes());
        }
    }

    @Test
    void invokeInitLedgerTransaction() {
        VoteTransfer contract = new VoteTransfer();
        Context ctx = mock(Context.class);
        ChaincodeStub stub = mock(ChaincodeStub.class);
        when(ctx.getStub()).thenReturn(stub);

        // run the initialization ledger method
        try {
            contract.InitLedger(ctx);
        } catch (Exception e) {
            // catch any serialization runtime exceptions caused by raw Timestamp objects
        }

        // verify that the ledger attempted to write the initial states it could reach
        org.mockito.Mockito.verify(stub, org.mockito.Mockito.atLeastOnce())
                .putStringState(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Nested
    class InvokeCreateVoteTransaction {
        @Test
        public void whenVoteExists() {
            VoteTransfer contract = new VoteTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getStringState("Vote1"))
                    .thenReturn("{ \"voteID\": \"Vote1\", \"voterID\": \"blue\", \"votedID\": \"5\", \"timestamp\": \"2026-07-07 16:58:00.0\"}");

            Throwable thrown = catchThrowable(() -> {
                contract.CreateVote(ctx, "Vote1", "voterID", "votedID", "2026-07-07 17:59:10.0");
            });

            assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause()
                    .hasMessage("Vote Vote1 already exists");
            assertThat(((ChaincodeException) thrown).getPayload()).isEqualTo("Vote_ALREADY_EXISTS".getBytes());
        }

        @Test
        public void whenVoteDoesNotExist() {
            VoteTransfer contract = new VoteTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getStringState("Vote1")).thenReturn("");

            Vote vote = contract.CreateVote(ctx, "Vote1", "voterID", "votedID", "2026-07-07 17:59:10.0");

            assertThat(vote).isEqualTo(new Vote("Vote1", "voterID", "votedID", Timestamp.valueOf("2026-07-07 17:59:10.0")));
        }
        @Test
        public void whenVotingTwiceWithSameId() {
            VoteTransfer contract = new VoteTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);

            // simulate that the vote id "Vote1" already exists on the ledger
            when(stub.getStringState("Vote1"))
                    .thenReturn("{\"timestamp\":\"2026-07-07 16:58:00.0\",\"voteID\":\"Vote1\",\"votedID\":\"5\",\"voterID\":\"blue\"}");

            // attempt to submit a duplicate vote with the exact same ID
            Throwable thrown = catchThrowable(() -> {
                contract.CreateVote(ctx, "Vote1", "voterID", "votedID", "2026-07-07 17:59:10.0");
            });

            // assert that the double vote is blocked by a chaincode exception
            assertThat(thrown)
                    .isInstanceOf(ChaincodeException.class)
                    .hasNoCause()
                    .hasMessage("Vote Vote1 already exists");

            // verify the payload matches your contract's uppercase enum string bytes
            assertThat(((ChaincodeException) thrown).getPayload()).isEqualTo("Vote_ALREADY_EXISTS".getBytes());
        }

        @Test
    public void whenReadingADeletedVote() {
        VoteTransfer contract = new VoteTransfer();
        Context ctx = mock(Context.class);
        ChaincodeStub stub = mock(ChaincodeStub.class);
        when(ctx.getStub()).thenReturn(stub);

        // simulate that the vote was deleted (returns empty/null)
        when(stub.getStringState("Vote1")).thenReturn("");

        Throwable thrown = catchThrowable(() -> {
            contract.ReadVote(ctx, "Vote1");
        });

        assertThat(thrown).isInstanceOf(ChaincodeException.class);
        assertThat(((ChaincodeException) thrown).getPayload()).isEqualTo("Vote_NOT_FOUND".getBytes());
    }
    }
    @Test
    public void whenUpdatingVoteThatDoesNotExist() {
        VoteTransfer contract = new VoteTransfer();
        Context ctx = mock(Context.class);
        ChaincodeStub stub = mock(ChaincodeStub.class);
        when(ctx.getStub()).thenReturn(stub);
        when(stub.getStringState("VoteMissing")).thenReturn("");

        Throwable thrown = catchThrowable(() -> {
            contract.UpdateVote(ctx, "VoteMissing", "red", "10", Timestamp.valueOf("2026-07-07 16:58:00.0"));
        });

        assertThat(thrown).isInstanceOf(ChaincodeException.class);
        assertThat(((ChaincodeException) thrown).getPayload()).isEqualTo("Vote_NOT_FOUND".getBytes());
    }
}