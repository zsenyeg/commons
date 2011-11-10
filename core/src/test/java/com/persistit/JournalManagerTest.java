/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.persistit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.Test;

import com.persistit.JournalManager.PageNode;
import com.persistit.RecoveryManager.RecoveryListener;
import com.persistit.TimestampAllocator.Checkpoint;
import com.persistit.exception.PersistitException;
import com.persistit.unit.PersistitUnitTestCase;
import com.persistit.unit.UnitTestProperties;

public class JournalManagerTest extends PersistitUnitTestCase {

    /*
     * This class needs to be in com.persistit rather than com.persistit.unit
     * because it uses some package- private methods in Persistit.
     */

    private String _volumeName = "persistit";

    @Test
    public void testJournalRecords() throws Exception {
        store1();
        _persistit.flush();
        final Transaction txn = _persistit.getTransaction();

        final Volume volume = _persistit.getVolume(_volumeName);
        volume.setHandle(0);

        final JournalManager jman = new JournalManager(_persistit);
        final String path = UnitTestProperties.DATA_PATH + "/JournalManagerTest_journal_";
        jman.init(null, path, 100 * 1000 * 1000);
        final BufferPool pool = _persistit.getBufferPool(16384);
        final long pages = Math.min(1000, volume.getStorage().getNextAvailablePage() - 1);
        for (int i = 0; i < 1000; i++) {
            final Buffer buffer = pool.get(volume, i % pages, false, true);
            if ((i % 400) == 0) {
                jman.rollover();
            }
            jman.writePageToJournal(buffer);
            buffer.releaseTouched();
        }

        final Checkpoint checkpoint1 = _persistit.getTimestampAllocator().forceCheckpoint();
        jman.writeCheckpointToJournal(checkpoint1);
        final Exchange exchange = _persistit.getExchange(_volumeName, "JournalManagerTest1", false);
        assertTrue(exchange.next(true));
        final long[] timestamps = new long[100];

        for (int i = 0; i < 100; i++) {
            assertTrue(exchange.next(true));
            final int treeHandle = jman.handleForTree(exchange.getTree());
            timestamps[i] = _persistit.getTimestampAllocator().updateTimestamp();
            
            txn.writeStoreRecordToJournal(treeHandle, exchange.getKey(), exchange.getValue());
            if (i % 50 == 0) {
                jman.rollover();
            }
            jman.writeTransactionToJournal(txn.getTransactionBuffer(), timestamps[i], i % 4 == 1 ?  timestamps[i] + 1 :  0, 0);
        }
        jman.rollover();
        exchange.clear().append(Key.BEFORE);
        int commitCount = 0;
        long noPagesAfterThis = jman.getCurrentAddress();

        Checkpoint checkpoint2 = null;

        for (int i = 0; i < 100; i++) {
            assertTrue(exchange.next(true));
            final int treeHandle = jman.handleForTree(exchange.getTree());
            timestamps[i] = _persistit.getTimestampAllocator().updateTimestamp();
            txn.writeDeleteRecordToJournal(treeHandle, exchange.getKey(), exchange.getKey());
            if (i == 66) {
                jman.rollover();
            }
            if (i == 50) {
                checkpoint2 = _persistit.getTimestampAllocator().forceCheckpoint();
                jman.writeCheckpointToJournal(checkpoint2);
                noPagesAfterThis = jman.getCurrentAddress();
                commitCount = 0;
            }
            jman.writeTransactionToJournal(txn.getTransactionBuffer(), timestamps[i], i % 4 == 3 ?  timestamps[i] + 1 :  0, 0);
        }

        store1();

        /**
         * These pages will have timestamps larger than the last valid
         * checkpoint and therefore should not be represented in the recovered
         * page map.
         */
        for (int i = 0; i < 1000; i++) {
            final Buffer buffer = pool.get(volume, i % pages, false, true);
            if ((i % 400) == 0) {
                jman.rollover();
            }
            if (buffer.getTimestamp() > checkpoint2.getTimestamp()) {
                jman.writePageToJournal(buffer);
            }
            buffer.releaseTouched();
        }
        jman.close();
        volume.setHandle(0);

        RecoveryManager rman = new RecoveryManager(_persistit);
        rman.init(path);

        rman.buildRecoveryPlan();
        assertTrue(rman.getKeystoneAddress() != -1);
        assertEquals(checkpoint2.getTimestamp(), rman.getLastValidCheckpoint().getTimestamp());
        final Map<PageNode, PageNode> pageMap = new HashMap<PageNode, PageNode>();
        final Map<PageNode, PageNode> branchMap = new HashMap<PageNode, PageNode>();

        rman.collectRecoveredPages(pageMap, branchMap);
        assertEquals(pages, pageMap.size());

        for (final PageNode pn : pageMap.values()) {
            assertTrue(pn.getJournalAddress() <= noPagesAfterThis);
        }

        final Set<Long> recoveryTimestamps = new HashSet<Long>();
        final RecoveryListener actor = new RecoveryListener() {

            @Override
            public void store(final long address, final long timestamp, Exchange exchange) throws PersistitException {
                recoveryTimestamps.add(timestamp);
            }

            @Override
            public void removeKeyRange(final long address, final long timestamp, Exchange exchange, Key from, Key to)
                    throws PersistitException {
                recoveryTimestamps.add(timestamp);
            }

            @Override
            public void removeTree(final long address, final long timestamp, Exchange exchange)
                    throws PersistitException {
                recoveryTimestamps.add(timestamp);
            }

            @Override
            public void startRecovery(long address, long timestamp) throws PersistitException {
            }

            @Override
            public void startTransaction(long address, long timestamp) throws PersistitException {
            }

            @Override
            public void endTransaction(long address, long timestamp) throws PersistitException {
            }

            @Override
            public void endRecovery(long address, long timestamp) throws PersistitException {
            }

        };
        rman.applyAllCommittedTransactions(actor);
        assertEquals(commitCount, recoveryTimestamps.size());

    }

    @Test
    public void testRollover() throws Exception {
        store1();
        _persistit.flush();
        final Volume volume = _persistit.getVolume(_volumeName);
        volume.setHandle(0);
        final JournalManager jman = new JournalManager(_persistit);
        final String path = UnitTestProperties.DATA_PATH + "/JournalManagerTest_journal_";
        jman.init(null, path, 100 * 1000 * 1000);
        final BufferPool pool = _persistit.getBufferPool(16384);
        final long pages = Math.min(1000, volume.getStorage().getNextAvailablePage() - 1);
        for (int i = 0; jman.getCurrentAddress() < 300 * 1000 * 1000; i++) {
            final Buffer buffer = pool.get(volume, i % pages, false, true);
            buffer.setDirtyAtTimestamp(_persistit.getTimestampAllocator().updateTimestamp());
            buffer.save();
            jman.writePageToJournal(buffer);
            buffer.releaseTouched();
        }
        final Checkpoint checkpoint1 = _persistit.getTimestampAllocator().forceCheckpoint();
        jman.writeCheckpointToJournal(checkpoint1);
        _persistit.checkpoint();
        final Properties saveProperties = _persistit.getProperties();
        _persistit.close();
        _persistit = new Persistit();
        _persistit.initialize(saveProperties);
        _persistit.getJournalManager().setAppendOnly(true);
        final RecoveryManager rman = new RecoveryManager(_persistit);
        rman.init(path);
        assertTrue(rman.analyze());
    }

    @Test
    public void testRollover768048() throws Exception {
        final Transaction txn = _persistit.getTransaction();
        final Exchange exchange = _persistit.getExchange(_volumeName, "JournalManagerTest1", true);
        final JournalManager jman = new JournalManager(_persistit);
        final String path = UnitTestProperties.DATA_PATH + "/JournalManagerTest_journal_";
        jman.init(null, path, 100 * 1000 * 1000);
        exchange.clear().append("key");
        final StringBuilder sb = new StringBuilder(1000000);
        for (int i = 0; sb.length() < 1000; i++) {
            sb.append(String.format("%018d  ", i));
        }
        final String kilo = sb.toString();
        exchange.getValue().put(kilo);
        int overhead = JournalRecord.SR.OVERHEAD + exchange.getKey().getEncodedSize() + JournalRecord.JE.OVERHEAD + 1;
        long timestamp = 0;
        long addressBeforeRollover = -1;
        long addressAfterRollover = -1;

        while (jman.getCurrentAddress() < 300 * 1000 * 1000) {
            long remaining = jman.getBlockSize() - (jman.getCurrentAddress() % jman.getBlockSize()) - 1;
            if (remaining == JournalRecord.JE.OVERHEAD) {
                addressBeforeRollover = jman.getCurrentAddress();
            }
            long size = remaining - overhead;
            if (size > 0 && size < sb.length()) {
                exchange.getValue().put(kilo.substring(0, (int) size));
            } else {
                exchange.getValue().put(kilo);
            }
            txn.writeStoreRecordToJournal(12345, exchange.getKey(), exchange.getValue());
            if (remaining == JournalRecord.JE.OVERHEAD) {
                addressAfterRollover = jman.getCurrentAddress();
                assertTrue(addressAfterRollover - addressBeforeRollover < 2000);
            }
        }
    }

    private void store1() throws PersistitException {
        final Exchange exchange = _persistit.getExchange(_volumeName, "JournalManagerTest1", true);
        exchange.removeAll();
        final StringBuilder sb = new StringBuilder();

        for (int i = 1; i < 50000; i++) {
            sb.setLength(0);
            sb.append((char) (i / 20 + 64));
            sb.append((char) (i % 20 + 64));
            exchange.clear().append(sb);
            exchange.getValue().put("Record #" + i);
            exchange.store();
        }
    }

    @Override
    public void runAllTests() throws Exception {
        // TODO Auto-generated method stub

    }

}
