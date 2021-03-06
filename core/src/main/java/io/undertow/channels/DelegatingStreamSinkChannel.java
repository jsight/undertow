package io.undertow.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author Stuart Douglas
 */
public abstract class DelegatingStreamSinkChannel<T extends DelegatingStreamSinkChannel> implements StreamSinkChannel {

    protected final StreamSinkChannel delegate;
    protected final ChannelListener.SimpleSetter<T> writeSetter = new ChannelListener.SimpleSetter<T>();
    protected final ChannelListener.SimpleSetter<T> closeSetter = new ChannelListener.SimpleSetter<T>();

    public DelegatingStreamSinkChannel(final StreamSinkChannel delegate) {
        this.delegate = delegate;
        delegate.getWriteSetter().set(ChannelListeners.delegatingChannelListener((T) this, writeSetter));
        delegate.getCloseSetter().set(ChannelListeners.delegatingChannelListener((T) this, closeSetter));
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return delegate.transferFrom(src, position, count);
    }

    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    public boolean isWriteResumed() {
        return delegate.isWriteResumed();
    }

    public boolean flush() throws IOException {
        return delegate.flush();
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        delegate.awaitWritable(time, timeUnit);
    }

    public int write(final ByteBuffer src) throws IOException {
        return delegate.write(src);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        return delegate.write(srcs, offset, length);
    }

    public void awaitWritable() throws IOException {
        delegate.awaitWritable();
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return delegate.setOption(option, value);
    }

    public ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter() {
        return closeSetter;
    }

    public ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter() {
        return writeSetter;
    }

    public boolean supportsOption(final Option<?> option) {
        return delegate.supportsOption(option);
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return delegate.write(srcs);
    }

    public void resumeWrites() {
        delegate.resumeWrites();
    }

    public boolean isOpen() {
        return delegate.isOpen();
    }

    public void shutdownWrites() throws IOException {
        delegate.shutdownWrites();
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return delegate.transferFrom(source, count, throughBuffer);
    }

    public XnioExecutor getWriteThread() {
        return delegate.getWriteThread();
    }

    public void wakeupWrites() {
        delegate.wakeupWrites();
    }

    public void close() throws IOException {
        delegate.close();
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return delegate.getOption(option);
    }

    public void suspendWrites() {
        delegate.suspendWrites();
    }

}
