/*
 * Copyright 2013 Basho Technologies Inc.
 *
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
 */
package com.basho.riak.client.core.operations.itest;

import com.basho.riak.client.core.RiakFuture;
import com.basho.riak.client.core.StreamingRiakFuture;
import com.basho.riak.client.core.operations.ListKeysOperation;
import com.basho.riak.client.core.operations.StoreOperation;
import com.basho.riak.client.core.operations.StreamingListKeysOperation;
import com.basho.riak.client.core.query.Location;
import com.basho.riak.client.core.query.Namespace;
import com.basho.riak.client.core.query.RiakObject;
import com.basho.riak.client.core.util.BinaryValue;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 *
 * @author Brian Roach <roach at basho dot com>
 * @author Alex Moore <amoore at basho dot com>
 */
public class ITestListKeysOperation extends ITestBase
{
    private Logger logger = LoggerFactory.getLogger("ITestListKeysOperation");

    private final String keyBase = "my_key";
    private final String value = "{\"value\":\"value\"}";
    private final String bucketType = super.bucketType.toStringUtf8();

    @Test
    public void testListNoKeysDefaultType() throws InterruptedException, ExecutionException
    {
        testListNoKeys(Namespace.DEFAULT_BUCKET_TYPE, "_0");
    }

    @Test
    public void testListNoKeysTestType() throws InterruptedException, ExecutionException
    {
        assumeTrue(testBucketType);
        testListNoKeys(bucketType, "_1");
    }

    @Test
    public void testListNoKeysDefaultTypeStreaming() throws InterruptedException, ExecutionException
    {
        testListKeysStreaming(Namespace.DEFAULT_BUCKET_TYPE, "_2", 0);
    }


    @Test
    public void testListKeyDefaultType() throws InterruptedException, ExecutionException
    {
        testListSingleKey(Namespace.DEFAULT_BUCKET_TYPE, "_3");
    }

    @Test
    public void testListKeyTestType() throws InterruptedException, ExecutionException
    {
        assumeTrue(testBucketType);
        testListSingleKey(bucketType, "_4");
    }

    @Test
    public void testListKeyDefaultTypeStreaming() throws InterruptedException, ExecutionException
    {
        testListKeysStreaming(Namespace.DEFAULT_BUCKET_TYPE, "_5", 1);
    }


    @Test
    public void testLargeKeyListDefaultType() throws InterruptedException, ExecutionException
    {
        testManyKeyList(Namespace.DEFAULT_BUCKET_TYPE, "_6", 1000);
    }

    @Test
    public void testLargeKeyListDefaultTypeStreaming() throws InterruptedException, ExecutionException
    {
        testListKeysStreaming(Namespace.DEFAULT_BUCKET_TYPE, "_7", 1000);
    }

    @Test
    public void testLargeKeyListTestType() throws InterruptedException, ExecutionException
    {
        assumeTrue(testBucketType);
        testManyKeyList(bucketType, "_8", 1000);
    }

    @Test
    public void testLargeKeyListTestTypeStreaming() throws InterruptedException, ExecutionException
    {
        assumeTrue(testBucketType);
        testListKeysStreaming(bucketType, "_9", 1000);
    }



    private void testListNoKeys(String bucketType, String bucketSuffix) throws InterruptedException, ExecutionException
    {
        Namespace ns = setupBucket(bucketType, bucketSuffix, 0);

        List<BinaryValue> kList = getAllKeyListResults(ns);

        assertTrue(kList.isEmpty());
        resetAndEmptyBucket(ns);
    }

    private void testListSingleKey(String bucketType, String bucketSuffix) throws InterruptedException, ExecutionException
    {
        final Namespace ns = setupBucket(bucketType, bucketSuffix, 1);

        List<BinaryValue> kList = getAllKeyListResults(ns);

        assertEquals(kList.size(), 1);
        assertEquals(kList.get(0), createKeyName(0));
        resetAndEmptyBucket(ns);
    }

    private void testManyKeyList(String bucketType, String bucketSuffix, int numExpected) throws InterruptedException, ExecutionException
    {
        final Namespace ns = setupBucket(bucketType, bucketSuffix, numExpected);

        List<BinaryValue> kList = getAllKeyListResults(ns);

        assertEquals(numExpected, kList.size());

        resetAndEmptyBucket(ns);
    }

    private void testListKeysStreaming(String bucketType, String bucketSuffix, int numExpected) throws InterruptedException, ExecutionException
    {
        final Namespace ns = setupBucket(bucketType, bucketSuffix, numExpected);

        StreamingListKeysOperation slko = new StreamingListKeysOperation.Builder(ns).build();
        final StreamingRiakFuture<BinaryValue, Namespace> execute = cluster.execute(slko);

        final BlockingQueue<BinaryValue> resultsQueue = execute.getResultsQueue();
        List<BinaryValue> kList = new LinkedList<>();
        int timeouts = 0;

        for (int i = 0; i < numExpected; i++)
        {
            final BinaryValue key = resultsQueue.poll(1, TimeUnit.SECONDS);

            if(key != null)
            {
                kList.add(key);
                continue;
            }

            timeouts++;
            if(timeouts == 10)
            {
                break;
            }
        }

        assertEquals(numExpected, kList.size());

        ITestBase.resetAndEmptyBucket(ns);
    }

    private List<BinaryValue> getAllKeyListResults(Namespace ns) throws InterruptedException, ExecutionException
    {
        ListKeysOperation klistOp = new ListKeysOperation.Builder(ns).build();
        cluster.execute(klistOp);
        return klistOp.get().getKeys();
    }

    private Namespace setupBucket(String bucketType, String bucketSuffix, int numExpected) throws InterruptedException
    {
        final Namespace ns = new Namespace(bucketType, bucketName + bucketSuffix);
        storeObjects(ns, numExpected);
        return ns;
    }

    private void storeObjects(Namespace ns, int expected) throws InterruptedException
    {
        for (int i = 0; i < expected; i++)
        {
            BinaryValue key = createKeyName(i);
            RiakObject rObj = new RiakObject().setValue(BinaryValue.create(value));
            Location location = new Location(ns, key);
            StoreOperation storeOp =
                    new StoreOperation.Builder(location)
                            .withContent(rObj)
                            .build();

            final RiakFuture<StoreOperation.Response, Location> execute = cluster.execute(storeOp);
            execute.await();
            assertTrue(execute.isSuccess());
        }
    }

    private BinaryValue createKeyName(int i) {return BinaryValue.unsafeCreate((keyBase + i).getBytes());}
}
