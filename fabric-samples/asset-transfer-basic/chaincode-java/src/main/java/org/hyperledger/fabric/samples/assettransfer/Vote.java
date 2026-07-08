/*
 * SPDX-License-Identifier: Apache-2.0
 */
//original is asset, but now we will change asset into a 'vote'

package org.hyperledger.fabric.samples.assettransfer;

import java.sql.Timestamp;
import java.util.Objects;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import com.owlike.genson.annotation.JsonProperty;

@DataType()
public final class Vote {

    @Property()
    private final String voteID; //specific vote

    @Property()
    private final String voterID; //who voted

    @Property()
    private final String votedID; //who or what was voted for

    @Property()
    private final Timestamp timestamp;

    public String getVoteID() {
        return voteID;
    }
    public String getVoterID() {
        return voterID;
    }

    public String getVotedID() {
        return votedID;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public Vote(@JsonProperty("voteID") final String voteID, @JsonProperty("voterID") final String voterID,
            @JsonProperty("votedID") final String votedID, @JsonProperty("timestamp") final Timestamp timestamp) {
        this.voteID = voteID;
        this.voterID = voterID;
        this.votedID = votedID;
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }

        Vote other = (Vote) obj;

        return Objects.deepEquals(
                new String[] {getVoteID(), getVoterID(), getVotedID()},
                new String[] {other.getVoteID(), other.getVoterID(), other.getVotedID()})
                &&
                Objects.deepEquals(
                new Timestamp[] {getTimestamp()},
                new Timestamp[] {other.getTimestamp()});
    }

    @Override
    public int hashCode() {
        return Objects.hash(getVoteID(), getVoterID(), getVotedID(), getTimestamp());
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "@" + Integer.toHexString(hashCode()) + " [voteID=" + voteID + ", voterID="
                + voterID + ", votedID=" + votedID + ", timestamp=" + timestamp + "]";
    }
}
