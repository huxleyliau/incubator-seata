/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.seata.core.rpc.processor.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.seata.common.thread.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * handle ServerOnRequestProcessor and ServerOnResponseProcessor log print.
 *
 * @since 1.3.0
 */
public class BatchLogHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchLogHandler.class);

    private static final BlockingQueue<String> LOG_QUEUE = new ArrayBlockingQueue<>(1024 * 10);

    public static final BatchLogHandler INSTANCE = new BatchLogHandler();

    private static final int MAX_LOG_SEND_THREAD = 1;
    private static final int MAX_LOG_TAKE_SIZE = 1024;
    private static final long KEEP_ALIVE_TIME = 0L;
    private static final String THREAD_PREFIX = "batchLoggerPrint";
    private static final long BUSY_SLEEP_MILLS = 5L;

    static {
        ExecutorService mergeSendExecutorService = new ThreadPoolExecutor(MAX_LOG_SEND_THREAD, MAX_LOG_SEND_THREAD,
            KEEP_ALIVE_TIME, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
            new NamedThreadFactory(THREAD_PREFIX, MAX_LOG_SEND_THREAD, true));
        mergeSendExecutorService.submit(new BatchLogRunnable());
    }

    public boolean writeLog(String log) {
        try {
            LOG_QUEUE.put(log);
        } catch (InterruptedException e) {
            LOGGER.error("put message to logQueue error: {}", e.getMessage(), e);
            return false;
        }
        return true;
    }

    /**
     * The type Batch log runnable.
     */
    static class BatchLogRunnable implements Runnable {

        @Override
        public void run() {
            List<String> logList = new ArrayList<>();
            while (true) {
                try {
                    logList.add(LOG_QUEUE.take());
                    LOG_QUEUE.drainTo(logList, MAX_LOG_TAKE_SIZE);
                    if (LOGGER.isInfoEnabled()) {
                        for (String str : logList) {
                            LOGGER.info(str);
                        }
                    }
                    logList.clear();
                    TimeUnit.MILLISECONDS.sleep(BUSY_SLEEP_MILLS);
                } catch (InterruptedException exx) {
                    LOGGER.error("batch log busy sleep error:{}", exx.getMessage(), exx);
                }

            }
        }
    }

}
