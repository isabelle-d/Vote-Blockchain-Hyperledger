/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.assettransfer;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

import com.owlike.genson.Genson;

@Contract(
        name = "basic",
        info = @Info(
                title = "Vote Transfer",
                description = "The hyperlegendary Vote transfer",
                version = "0.0.1-SNAPSHOT",
                license = @License(
                        name = "Apache 2.0 License",
                        url = "http://www.apache.org/licenses/LICENSE-2.0.html"),
                contact = @Contact(
                        email = "a.transfer@example.com",
                        name = "Adrian Transfer",
                        url = "https://hyperledger.example.com")))
@Default
public final class VoteTransfer implements ContractInterface {

    private final Genson genson = new Genson();

    private enum VoteTransferErrors {
        Vote_NOT_FOUND,
        Vote_ALREADY_EXISTS
    }

    /**
     * Creates some initial Votes on the ledger.
     *
     * @param ctx the transaction context
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void InitLedger(final Context ctx) {
        if (VoteExists(ctx, "Vote1")) {
            return;
        }

        // track batch updates locally in memory to bypass fabric's empty read-set constraints during a single block execution
        Map<String, Integer> batchTallies = new HashMap<>();

        putVote(ctx, new Vote("Vote1", "blue", "5", Timestamp.valueOf("2026-07-07 16:58:00.0")));
        batchTallies.put("5", batchTallies.getOrDefault("5", 0) + 1);

        putVote(ctx, new Vote("Vote2", "red", "5", Timestamp.valueOf("2026-07-07 16:58:00.0")));
        batchTallies.put("5", batchTallies.getOrDefault("5", 0) + 1);

        putVote(ctx, new Vote("Vote3", "green", "5", Timestamp.valueOf("2026-07-07 16:58:00.0")));
        batchTallies.put("5", batchTallies.getOrDefault("5", 0) + 1);

        putVote(ctx, new Vote("Vote4", "no", "5", Timestamp.valueOf("2026-07-07 16:58:00.0")));
        batchTallies.put("5", batchTallies.getOrDefault("5", 0) + 1);

        putVote(ctx, new Vote("Vote5", "blue", "5", Timestamp.valueOf("2026-07-07 16:58:00.0")));
        batchTallies.put("5", batchTallies.getOrDefault("5", 0) + 1);
        putVote(ctx, new Vote("Vote6", "blue", "6", Timestamp.valueOf("2026-07-07 16:58:00.0")));
        batchTallies.put("6", batchTallies.getOrDefault("6", 0) + 1);


        // write final aggregated batch values to ledger state safely
        for (Map.Entry<String, Integer> entry : batchTallies.entrySet()) {
            updateTallyByCount(ctx, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Creates a new Vote on the ledger.
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Vote CreateVote(final Context ctx, final String voteID, final String voterID, final String votedID,
        final String tstamp) {
        java.sql.Timestamp timestamp = java.sql.Timestamp.valueOf(tstamp);

        if (VoteExists(ctx, voteID)) {
            String errorMessage = String.format("Vote %s already exists", voteID);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, VoteTransferErrors.Vote_ALREADY_EXISTS.toString());
        }

        updateTallyByCount(ctx, votedID, 1);

        return putVote(ctx, new Vote(voteID, voterID, votedID, timestamp));
    }

    private void updateTallyByCount(final Context ctx, final String candidateID, final int countToAdd) {
        String tallyKey = "Tally_" + candidateID;
        String currentTallyStr = ctx.getStub().getStringState(tallyKey);

        int currentCount = 0;
        if (currentTallyStr != null && !currentTallyStr.isEmpty()) {
            currentCount = Integer.parseInt(currentTallyStr);
        }

        ctx.getStub().putStringState(tallyKey, String.valueOf(currentCount + countToAdd));
    }

    private Vote putVote(final Context ctx, final Vote Vote) {
        String sortedJson = genson.serialize(Vote);
        ctx.getStub().putStringState(Vote.getVoteID(), sortedJson);
        return Vote;
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public Vote ReadVote(final Context ctx, final String VoteID) {
        String VoteJSON = ctx.getStub().getStringState(VoteID);

        if (VoteJSON == null || VoteJSON.isEmpty()) {
            String errorMessage = String.format("Vote %s does not exist", VoteID);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, VoteTransferErrors.Vote_NOT_FOUND.toString());
        }

        return genson.deserialize(VoteJSON, Vote.class);
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Vote UpdateVote(final Context ctx, final String voteID, final String voterID, final String votedID,
        final Timestamp timestamp) {

        if (!VoteExists(ctx, voteID)) {
            String errorMessage = String.format("Vote %s does not exist", voteID);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, VoteTransferErrors.Vote_NOT_FOUND.toString());
        }

        return putVote(ctx, new Vote(voteID, voterID, votedID, timestamp));
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void DeleteVote(final Context ctx, final String voteID) {
        if (!VoteExists(ctx, voteID)) {
            String errorMessage = String.format("Vote %s does not exist", voteID);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, VoteTransferErrors.Vote_NOT_FOUND.toString());
        }

        ctx.getStub().delState(voteID);
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public boolean VoteExists(final Context ctx, final String VoteID) {
        String VoteJSON = ctx.getStub().getStringState(VoteID);
        return (VoteJSON != null && !VoteJSON.isEmpty());
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetAllVotes(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();

        List<Vote> queryResults = new ArrayList<>();
        QueryResultsIterator<KeyValue> results = stub.getStateByRange("", "");

        for (KeyValue result: results) {
            String key = result.getKey();

            if (key.startsWith("Tally_")) {
                continue;
            }

            Vote Vote = genson.deserialize(result.getStringValue(), Vote.class);
            System.out.println(Vote);
            queryResults.add(Vote);
        }

        return genson.serialize(queryResults);
    }

    @Override
    public void unknownTransaction(final Context ctx) {
        throw new ChaincodeException("Undefined contract method called");
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetWinner(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        QueryResultsIterator<KeyValue> results = stub.getStateByRange("Tally_", "Tally_\uFFFf");

        int maxVotes = -1;
        List<String> winners = new ArrayList<>();

        for (KeyValue result : results) {
            String key = result.getKey();
            String candidateID = key.replace("Tally_", "");
            int count = Integer.parseInt(result.getStringValue());

            if (count > maxVotes) {
                maxVotes = count;
                winners.clear();
                winners.add(candidateID);
            } else if (count == maxVotes) {
                winners.add(candidateID);
            }
        }

        if (winners.size() > 1) {
            return "Tie between " + winners + " with " + maxVotes + " votes";
        } else if (!winners.isEmpty()) {
            return "Winner: " + winners.get(0) + " with " + maxVotes + " votes";
        }
        return "Result: No votes";
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void DeleteAllData(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();

        // scan everything from start to finish
        QueryResultsIterator<KeyValue> results = stub.getStateByRange("", "");

        for (KeyValue result : results) {
            String key = result.getKey();
            // deletes every single vote log and tally counter asset found
            stub.delState(key);
        }
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
public String GetWinnerSlow(final Context ctx) {
    ChaincodeStub stub = ctx.getStub();

    // start exactly at "Vote" and read until the end of the vote keys
    QueryResultsIterator<KeyValue> results = stub.getStateByRange("Vote", "Vote\uFFFF");

    Map<String, Integer> tallies = new HashMap<>();
    int totalVotes = 0;

    for (KeyValue result : results) {
        String key = result.getKey();

        try {
            Vote vote = genson.deserialize(result.getStringValue(), Vote.class);

            if (vote == null || vote.getVotedID() == null) {
                continue;
            }

            String candidateID = vote.getVotedID();
            tallies.put(candidateID, tallies.getOrDefault(candidateID, 0) + 1);
            totalVotes++;

        } catch (Exception e) {
            System.out.println("Skipping invalid record: " + key);
        }
    }

    if (totalVotes == 0) {
        return "[Slow Scan] No votes found.";
    }

    int maxVotes = -1;
    List<String> winners = new ArrayList<>();

    for (Map.Entry<String, Integer> entry : tallies.entrySet()) {
        if (entry.getValue() > maxVotes) {
            maxVotes = entry.getValue();
            winners.clear();
            winners.add(entry.getKey());
        } else if (entry.getValue() == maxVotes) {
            winners.add(entry.getKey());
        }
    }

    if (winners.size() == 1) {
        return "[Slow Scan] Winner: " + winners.get(0)
                + " with " + maxVotes
                + " votes";
    }

    return "[Slow Scan] Tie between "
            + winners
            + " with "
            + maxVotes
            + " votes (Total votes scanned: " + totalVotes + ")";
}


    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void BulkInjectVotes(final Context ctx, final String countStr) {
        ChaincodeStub stub = ctx.getStub();
        int count = Integer.parseInt(countStr);
        java.util.Random random = new java.util.Random(12345);

        Map<String, Integer> localTallies = new HashMap<>();



        String[] candidates = {
            "15",
            "5",
            "blue-team",
            "red-team",
            "green-candidate"
        };

        for (int i = 1; i <= count; i++) {
            String voteID = "VoteBulk_" + count + "_" + i;
            String voterID = "Voter_" + random.nextInt(50000);
            String timestamp = "2026-07-07 22:00:00.0";

            String votedID = candidates[random.nextInt(candidates.length)];
            // construct the json string manually to bypass genson's timestamp serialization limit
            String voteJson = String.format(
                "{\"voteID\":\"%s\",\"voterID\":\"%s\",\"votedID\":\"%s\",\"timestamp\":\"%s\"}",
                voteID, voterID, votedID, timestamp
            );
            stub.putStringState(voteID, voteJson);

            localTallies.put(votedID, localTallies.getOrDefault(votedID, 0) + 1);
        }

        for (Map.Entry<String, Integer> entry : localTallies.entrySet()) {
            String candidateID = entry.getKey();
            String tallyKey = "Tally_" + candidateID;
            int currentCount = 0;

            String existingVal = stub.getStringState(tallyKey);
            if (existingVal != null && !existingVal.isEmpty()) {
                currentCount = Integer.parseInt(existingVal);
            }

            stub.putStringState(tallyKey, String.valueOf(currentCount + entry.getValue()));
        }
    }


}