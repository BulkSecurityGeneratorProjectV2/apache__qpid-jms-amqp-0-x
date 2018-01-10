/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.qpid.systest.producer.noroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQNoConsumersException;
import org.apache.qpid.client.AMQNoRouteException;

/**
 * Provides utility methods for checking exceptions that are thrown on the client side when a message is
 * not routable.
 *
 * Exception objects are passed either explicitly as method parameters or implicitly
 * by previously doing {@link Connection#setExceptionListener(ExceptionListener)}.
 */
public class UnroutableMessageTestExceptionListener implements ExceptionListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(UnroutableMessageTestExceptionListener.class);

    /**
     * Number of seconds to check for an event that should should NOT happen
     */
    private static final int NEGATIVE_TIMEOUT = 2;

    /**
     * Number of seconds to keep checking for an event that should should happen
     */
    private static final int POSITIVE_TIMEOUT = 30;

    private BlockingQueue<JMSException> _exceptions = new ArrayBlockingQueue<JMSException>(1);

    @Override
    public void onException(JMSException e)
    {
        LOGGER.info("Received exception " + e);
        _exceptions.add(e);
    }

    public void assertReceivedNoRouteWithReturnedMessage(Message message, String intendedQueueName) throws Exception
    {
        JMSException exception = getReceivedException();
        assertNoRouteExceptionWithReturnedMessage(exception, message, intendedQueueName);
    }

    public void assertReceivedNoRoute(String intendedQueueName) throws Exception
    {
        JMSException exception = getReceivedException();
        assertNoRoute(exception, intendedQueueName);
    }

    public void assertReceivedNoConsumersWithReturnedMessage(Message message) throws Exception
    {
        JMSException exception = getReceivedException();
        AMQNoConsumersException noConsumersException = (AMQNoConsumersException) exception.getLinkedException();
        assertNotNull("AMQNoConsumersException should be linked to JMSException", noConsumersException);
        Message bounceMessage = (Message) noConsumersException.getUndeliveredMessage();
        assertNotNull("Bounced Message is expected", bounceMessage);

        assertEquals("Unexpected message is bounced", message.getJMSMessageID(), bounceMessage.getJMSMessageID());
    }

    public void assertNoRouteExceptionWithReturnedMessage(
            JMSException exception, Message message, String intendedQueueName) throws Exception
    {
        assertNoRoute(exception, intendedQueueName);

        assertNoRouteException(exception, message);
    }

    private void assertNoRouteException(JMSException exception, Message message) throws Exception
    {
        AMQNoRouteException noRouteException = (AMQNoRouteException) exception.getLinkedException();
        assertNotNull("AMQNoRouteException should be linked to JMSException", noRouteException);
        Message bounceMessage = (Message) noRouteException.getUndeliveredMessage();
        assertNotNull("Bounced Message is expected", bounceMessage);

        assertEquals("Unexpected message is bounced", message.getJMSMessageID(), bounceMessage.getJMSMessageID());
    }

    public void assertNoRoute(JMSException exception, String intendedQueueName)
    {
        assertTrue(
                exception + " message should contain intended queue name",
                exception.getMessage().contains(intendedQueueName));

        AMQException noRouteException = (AMQException) exception.getLinkedException();
        assertNotNull("AMQException should be linked to JMSException", noRouteException);

        assertAMQException("Unexpected error code", 312, noRouteException);
        assertTrue(
                "Linked exception " + noRouteException + " message should contain intended queue name",
                noRouteException.getMessage().contains(intendedQueueName));
    }


    public void assertNoException() throws Exception
    {
        assertNull("Unexpected JMSException", _exceptions.poll(NEGATIVE_TIMEOUT, TimeUnit.SECONDS));
    }

    private JMSException getReceivedException() throws Exception
    {
        JMSException exception = _exceptions.poll(POSITIVE_TIMEOUT, TimeUnit.SECONDS);
        assertNotNull("JMSException is expected", exception);
        return exception;
    }

    private void assertAMQException(final String message, final int expected, final AMQException e)
    {
        Object object = e.getErrorCode(); // API change after v6.1
        if (object instanceof Integer)
        {
            assertEquals(message, expected, e.getErrorCode());
        }
        else
        {
            final String fullMessage = String.format("%s. expected actual : %s to start with %d", message, e.getErrorCode(), expected);
            final String actual = String.valueOf(e.getErrorCode());
            assertTrue(fullMessage, actual.startsWith(Integer.toString(expected)));
        }
    }

}
