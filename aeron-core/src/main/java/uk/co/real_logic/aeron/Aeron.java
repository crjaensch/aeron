/*
 * Copyright 2014 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.aeron;

import uk.co.real_logic.aeron.admin.*;
import uk.co.real_logic.aeron.util.AdminBufferStrategy;
import uk.co.real_logic.aeron.util.AtomicArray;
import uk.co.real_logic.aeron.util.CommonConfiguration;
import uk.co.real_logic.aeron.util.MappingAdminBufferStrategy;
import uk.co.real_logic.aeron.util.concurrent.AtomicBuffer;
import uk.co.real_logic.aeron.util.concurrent.ringbuffer.ManyToOneRingBuffer;
import uk.co.real_logic.aeron.util.concurrent.ringbuffer.RingBuffer;

import java.nio.ByteBuffer;

import static uk.co.real_logic.aeron.util.concurrent.ringbuffer.BufferDescriptor.TRAILER_LENGTH;

/**
 * Encapsulation of media driver and API for source and receiver construction
 */
public final class Aeron
{
    private static final int ADMIN_BUFFER_SIZE = 512 + TRAILER_LENGTH;

    /**
     * Creates an media driver associated with this Aeron instance that can be used to create sources and receivers on.
     *
     * @param builder of the media driver and Aeron configuration or null for default configuration
     * @return Aeron instance
     */
    public static Aeron newSingleMediaDriver(final Builder builder)
    {
        return new Aeron(builder);
    }

    /**
     * Creates multiple media drivers associated with multiple Aeron instances that can be used to create sources
     * and receivers.
     *
     * @param builders of the media drivers
     * @return array of Aeron instances
     */
    public static Aeron[] newMultipleMediaDrivers(final Builder[] builders)
    {
        final Aeron[] aerons = new Aeron[builders.length];

        for (int i = 0, max = builders.length; i < max; i++)
        {
            aerons[i] = new Aeron(builders[i]);
        }

        return aerons;
    }

    private final ProducerControlFactory producerControl;
    private final ManyToOneRingBuffer adminCommandBuffer;
    private final ErrorHandler errorHandler;
    private final ClientAdminThread adminThread;
    private final AdminBufferStrategy adminBuffers;
    private final AtomicArray<Channel> channels;
    private final AtomicArray<ConsumerChannel> receivers;

    private Aeron(final Builder builder)
    {
        errorHandler = builder.errorHandler;
        adminBuffers = builder.adminBuffers;
        producerControl = builder.producerControl;
        channels = new AtomicArray<>();
        receivers = new AtomicArray<>();
        adminCommandBuffer = new ManyToOneRingBuffer(new AtomicBuffer(ByteBuffer.allocate(ADMIN_BUFFER_SIZE)));

        try
        {
            final RingBuffer recvBuffer = new ManyToOneRingBuffer(new AtomicBuffer(adminBuffers.toApi()));
            final RingBuffer sendBuffer = new ManyToOneRingBuffer(new AtomicBuffer(adminBuffers.toMediaDriver()));
            final BufferUsageStrategy bufferUsage = new BasicBufferUsageStrategy(CommonConfiguration.DATA_DIR);
            final AdminErrorHandler adminErrorHandler = new AdminErrorHandler(builder.invalidDestinationHandler);
            adminThread = new ClientAdminThread(adminCommandBuffer,
                                                recvBuffer, sendBuffer,
                                                bufferUsage,
                                                channels, receivers,
                                                adminErrorHandler,
                                                producerControl);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Unable to create Aeron", e);
        }
    }

    /**
     * Create a new source that is to send to {@link uk.co.real_logic.aeron.Destination}.
     *
     * A unique, random, session ID will be generated for the source if the builder does not
     * set it. If the builder sets the Session ID, then it will be checked for conflicting with existing session Ids.
     *
     * @param builder for source options, etc.
     * @return new source
     */
    public Source newSource(final Source.Builder builder)
    {
        builder.adminThread(new ClientAdminThreadCursor(adminCommandBuffer));
        return new Source(channels, builder);
    }

    /**
     * Create a new source that is to send to {@link Destination}
     * @param destination address to send all data to
     * @return new source
     */
    public Source newSource(final Destination destination)
    {
        return newSource(new Source.Builder().destination(destination));
    }

    /**
     * Create an array of sources.
     *
     * Convenience function to make it easier to create a number of Sources easier.
     *
     * @param builders for the source options, etc.
     * @return array of new sources.
     */
    public Source[] newSources(final Source.Builder[] builders)
    {
        final Source[] sources = new Source[builders.length];

        for (int i = 0, max = builders.length; i < max; i++)
        {
            sources[i] = newSource(builders[i]);
        }

        return sources;
    }

    /**
     * Create a new receiver that will listen on {@link uk.co.real_logic.aeron.Destination}
     * @param builder builder for receiver options.
     * @return new receiver
     */
    public Consumer newReceiver(final Consumer.Builder builder)
    {
        final ClientAdminThreadCursor adminThread = new ClientAdminThreadCursor(adminCommandBuffer);
        return new Consumer(adminThread, builder, receivers);
    }

    /**
     * Create a new receiver that will listen on a given destination, etc.
     *
     * @param block to fill in receiver builder
     * @return new receiver
     */
    public Consumer newConsumer(final java.util.function.Consumer<Consumer.Builder> block)
    {
        Consumer.Builder builder = new Consumer.Builder();
        block.accept(builder);
        return newReceiver(builder);
    }

    public ClientAdminThread adminThread()
    {
        return adminThread;
    }

    public static class Builder
    {
        private ErrorHandler errorHandler;
        private AdminBufferStrategy adminBuffers;
        private InvalidDestinationHandler invalidDestinationHandler;
        private ProducerControlFactory producerControl;

        public Builder()
        {
            errorHandler = new DummyErrorHandler();
            adminBuffers = new MappingAdminBufferStrategy(CommonConfiguration.ADMIN_DIR);
            producerControl = DefaultProducerControlStrategy::new;
        }

        public Builder errorHandler(ErrorHandler errorHandler)
        {
            this.errorHandler = errorHandler;
            return this;
        }

        public Builder adminBufferStrategy(AdminBufferStrategy adminBuffers)
        {
            this.adminBuffers = adminBuffers;
            return this;
        }

        public Builder invalidDestinationHandler(final InvalidDestinationHandler invalidDestination)
        {
            this.invalidDestinationHandler = invalidDestination;
            return this;
        }

        public Builder producerControl(final ProducerControlFactory producerControl)
        {
            this.producerControl = producerControl;
            return this;
        }
    }

}
