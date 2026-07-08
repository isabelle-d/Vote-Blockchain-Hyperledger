/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.assettransfer;

import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public final class VoteTest {

    @Nested
    class Equality {

        @Test
        public void isReflexive() {
            Vote Vote = new Vote("Vote1", "Blue", "20", Timestamp.valueOf("2026-07-07 16:58:00.0"));
            assertThat(Vote).isEqualTo(Vote);
        }

        @Test
        public void isSymmetric() {
            Vote VoteA = new Vote("Vote1", "Blue", "20",  Timestamp.valueOf("2026-07-07 16:59:00.0"));
            Vote VoteB = new Vote("Vote1", "Blue", "20", Timestamp.valueOf("2026-07-07 16:59:00.0"));

            assertThat(VoteA).isEqualTo(VoteB);
            assertThat(VoteB).isEqualTo(VoteA);
        }

        @Test
        public void isTransitive() {
            Vote VoteA = new Vote("Vote1", "Blue", "20",  Timestamp.valueOf("2026-07-07 16:59:00.0"));
            Vote VoteB = new Vote("Vote1", "Blue", "20",  Timestamp.valueOf("2026-07-07 16:59:00.0"));
            Vote VoteC = new Vote("Vote1", "Blue", "20",  Timestamp.valueOf("2026-07-07 16:59:00.0"));

            assertThat(VoteA).isEqualTo(VoteB);
            assertThat(VoteB).isEqualTo(VoteC);
            assertThat(VoteA).isEqualTo(VoteC);
        }

        @Test
        public void handlesInequality() {
            Vote VoteA = new Vote("Vote1", "Blue", "20",  Timestamp.valueOf("2026-07-07 16:59:00.0"));
            Vote VoteB = new Vote("Vote2", "red", "21",  Timestamp.valueOf("2026-07-07 16:59:00.0"));

            assertThat(VoteA).isNotEqualTo(VoteB);
        }

        @Test
        public void handlesOtherObjects() {
            Vote VoteA = new Vote("Vote1", "Blue", "20",  Timestamp.valueOf("2026-07-07 16:59:00.0"));
            String VoteB = "not a Vote";

            assertThat(VoteA).isNotEqualTo(VoteB);
        }

        @Test
        public void handlesNull() {
            Vote Vote = new Vote("Vote1", "Blue", "20",  Timestamp.valueOf("2026-07-07 16:59:00.0"));

            assertThat(Vote).isNotEqualTo(null);
        }
    }

}
