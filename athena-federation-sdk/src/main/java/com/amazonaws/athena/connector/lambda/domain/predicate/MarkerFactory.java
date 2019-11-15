package com.amazonaws.athena.connector.lambda.domain.predicate;

/*-
 * #%L
 * Amazon Athena Query Federation SDK
 * %%
 * Copyright (C) 2019 Amazon Web Services
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.amazonaws.athena.connector.lambda.data.Block;
import com.amazonaws.athena.connector.lambda.data.BlockAllocator;
import com.amazonaws.athena.connector.lambda.data.BlockUtils;
import org.apache.arrow.vector.types.pojo.ArrowType;

import java.util.HashMap;
import java.util.Map;

import static com.amazonaws.athena.connector.lambda.data.BlockUtils.setValue;

public class MarkerFactory
        implements AutoCloseable
{
    private final BlockAllocator allocator;
    private final Map<ArrowType, Block> sharedMarkerBlocks = new HashMap<>();
    private final Map<ArrowType, Integer> markerLeases = new HashMap<>();

    public MarkerFactory(BlockAllocator allocator)
    {
        this.allocator = allocator;
    }

    public Marker createNullable(ArrowType type, Object value, Marker.Bound bound)
    {
        BlockLease lease = getOrCreateBlock(type);
        if (value != null) {
            setValue(lease.getBlock().getFieldVector(Marker.DEFAULT_COLUMN), lease.getPos(), value);
        }
        return new SharedBlockMarker(this, lease.getBlock(), lease.getPos(), bound, value == null);
    }

    public Marker create(ArrowType type, Object value, Marker.Bound bound)
    {
        BlockLease lease = getOrCreateBlock(type);
        setValue(lease.getBlock().getFieldVector(Marker.DEFAULT_COLUMN), lease.getPos(), value);
        return new SharedBlockMarker(this, lease.getBlock(), lease.getPos(), bound, false);
    }

    public Marker create(ArrowType type, Marker.Bound bound)
    {
        BlockLease lease = getOrCreateBlock(type);
        return new SharedBlockMarker(this, lease.getBlock(), lease.getPos(), bound, true);
    }

    private synchronized BlockLease getOrCreateBlock(ArrowType type)
    {
        Block sharedBlock = sharedMarkerBlocks.get(type);
        Integer leaseNumber = markerLeases.get(type);
        if (sharedBlock == null) {
            sharedBlock = BlockUtils.newEmptyBlock(allocator, Marker.DEFAULT_COLUMN, type);
            sharedMarkerBlocks.put(type, sharedBlock);
            leaseNumber = 0;
        }
        markerLeases.put(type, ++leaseNumber);
        BlockLease lease = new BlockLease(sharedBlock, leaseNumber - 1);
        sharedBlock.setRowCount(leaseNumber);
        return lease;
    }

    /**
     * This leasing strategy optimizes for the create, return usecase it does not attempt to handle fragmentation
     * in any meaningful way beyond what the columnar nature of Arrow provides.
     */
    private synchronized void returnBlockLease(ArrowType type, int pos)
    {
        Block sharedBlock = sharedMarkerBlocks.get(type);
        Integer leaseNumber = markerLeases.get(type);

        if (sharedBlock != null && leaseNumber > 0 && leaseNumber == pos + 1) {
            markerLeases.put(type, leaseNumber - 1);
        }
    }

    @Override
    public void close()
            throws Exception
    {
        for (Block next : sharedMarkerBlocks.values()) {
            next.close();
        }

        sharedMarkerBlocks.clear();
        markerLeases.clear();
    }

    private static class BlockLease
    {
        private final Block block;
        private final int pos;

        public BlockLease(Block block, int pos)
        {
            this.block = block;
            this.pos = pos;
        }

        public Block getBlock()
        {
            return block;
        }

        public int getPos()
        {
            return pos;
        }
    }

    public class SharedBlockMarker
            extends Marker
    {
        private final MarkerFactory factory;
        private final int valuePosition;

        public SharedBlockMarker(MarkerFactory factory, Block block, int valuePosition, Bound bound, boolean nullValue)
        {
            super(block, valuePosition, bound, nullValue);
            this.factory = factory;
            this.valuePosition = valuePosition;
        }

        @Override
        public void close()
                throws Exception
        {
            //Don't call close on the super since we don't own the block, it shared.
            factory.returnBlockLease(getType(), valuePosition);
        }
    }
}
