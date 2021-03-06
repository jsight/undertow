/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.websockets.protocol.version00;

import java.nio.ByteBuffer;

import io.undertow.websockets.StreamSinkFrameChannel;
import io.undertow.websockets.WebSocketFrameType;
import org.xnio.channels.StreamSinkChannel;

/**
 * {@link StreamSinkFrameChannel} implementation for writing {@link WebSocketFrameType#TEXT}
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class WebSocket00TextFrameSinkChannel extends StreamSinkFrameChannel {

    private static final ByteBuffer START = ByteBuffer.allocateDirect(1).put((byte) 0x00);
    private static final ByteBuffer END = ByteBuffer.allocateDirect(1).put((byte) 0xFF);

    WebSocket00TextFrameSinkChannel(StreamSinkChannel channel, WebSocket00Channel wsChannel, long payloadSize) {
        super(channel, wsChannel, WebSocketFrameType.TEXT, payloadSize);
    }

    @Override
    protected ByteBuffer createFrameStart() {
        return START.duplicate();
    }

    @Override
    protected ByteBuffer createFrameEnd() {
        return END.duplicate();
    }

}
