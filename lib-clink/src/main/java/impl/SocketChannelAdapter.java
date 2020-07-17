package impl;

import core.IoArgs;
import core.IoProvider;
import core.Receiver;
import core.Sender;
import utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocketChannelAdapter implements Sender, Receiver,Cloneable {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final SocketChannel channel;
    private final IoProvider ioProvider;
    private final OnChannelStatusChangedListener listener;

    private IoArgs.IoArgsEventListener receiveIoEventListener;
    private IoArgs.IoArgsEventListener sendIoEventListener;

    public SocketChannelAdapter(SocketChannel channel, IoProvider ioProvider, OnChannelStatusChangedListener listener) throws IOException {
        this.channel = channel;
        this.ioProvider = ioProvider;
        this.listener = listener;

        channel.configureBlocking(false);
    }

    @Override
    public boolean receiveAsync(IoArgs.IoArgsEventListener listener) throws IOException{
        if (isClosed.get()){
            throw new IOException("Current channel is closed!");
        }

        receiveIoEventListener = listener;

        return ioProvider.registerInput(channel,inputCallback);
    }

    @Override
    public boolean sendAsync(IoArgs args,IoArgs.IoArgsEventListener listener)throws IOException{
        if (isClosed.get()){
            throw new IOException("Current channel is closed!");
        }

        sendIoEventListener = listener;

        outputCallback.setAttach(args);
        return ioProvider.registerOutput(channel,outputCallback);
    }

    private final IoProvider.HandleInputCallback inputCallback = new IoProvider.HandleInputCallback() {
        @Override
        protected void canProviderInput() {
            if (isClosed.get()){
                return;
            }

            IoArgs args = new IoArgs();
            IoArgs.IoArgsEventListener listener = SocketChannelAdapter.this.receiveIoEventListener;

            if (listener != null){
                listener.onStarted(args);
            }

            try {
                if (args.read(channel) > 0 && listener != null){
                    listener.onCompleted(args);
                }else {
                    throw new IOException("Cannot read any data!");
                }
            }catch (IOException ignored){
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    private final IoProvider.HandleOutputCallback outputCallback = new IoProvider.HandleOutputCallback() {
        @Override
        protected void canProviderOutput(Object attach) {
            if (isClosed.get()){
                return;
            }
            sendIoEventListener.onCompleted(null);
        }
    };

    public interface OnChannelStatusChangedListener{
        void onChannelClosed(SocketChannel channel);
    }
}
