package handle;

import core.Connector;
import utils.CloseUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;

public class ClientHandler {
    private final Connector connector;
    private final SocketChannel socketChannel;
    private final ClientWriteHandler writeHandler;
    private final ClientWriteHandler.ClientHandlerCallback clientHandlerCallback;
    private final String clientInfo;

    public ClientHandler(SocketChannel socketChannel, ClientWriteHandler.ClientHandlerCallback clientHandlerCallback) throws IOException {
        this.socketChannel = socketChannel;

        connector = new Connector(){
            @Override
            public void onChannelClosed(SocketChannel channel){
                super.onChannelClosed(channel);
                exitBySelf();
            }

            @Override
            protected void onReceiveNewMessage(String str){
                super.onReceiveNewMessage(str);
                clientHandlerCallback.onNewMessageArrived(ClientHandler.this,str);
            }
        };
        connector.setup(socketChannel);

        Selector writeSelector = Selector.open();
        socketChannel.register(writeSelector,SelectionKey.OP_WRITE);
        this.writeHandler = new ClientWriteHandler(writeSelector);

        this.clientHandlerCallback = clientHandlerCallback;
        this.clientInfo = socketChannel.getRemoteAddress().toString();
        System.out.println("新客户端连接："+clientInfo);
    }

    class ClientWriteHandler{
        private boolean done = false;
        private final Selector selector;
        private final ByteBuffer byteBuffer;
        private final ExecutorService executorService;

        ClientWriteHandler(Selector selector, ByteBuffer byteBuffer, ExecutorService executorService) {
            this.selector = selector;
            this.byteBuffer = byteBuffer;
            this.executorService = executorService;
        }

        void exit(){
            done = true;
            CloseUtils.close(selector);
            executorService.shutdownNow();
        }

        void send(String str){
            if (done){
                return;
            }
            executorService.execute(new WriteRunnable(str));
        }

        private void exitBySelf(){
            exit();
            clientHandlerCallback.onselfClose(this);
        }

        public interface ClientHandlerCallback{
            void onselfClose(ClientHandler handler);
            void onNewMessageArrived(ClientHandler handler,String msg);
        }

        class WriteRunnable implements Runnable{
            private final String msg;

            WriteRunnable(String msg) {
                this.msg = msg + '\n';
            }

            @Override
            public void run(){
                if (ClientWriteHandler.this.done){
                    return;
                }

                byteBuffer.clear();
                byteBuffer.put(msg.getBytes());
                byteBuffer.flip();

                while (!done && byteBuffer.hasRemaining()){
                    try {
                        int len = socketChannel.write(byteBuffer);
                        if (len < 0){
                            System.out.println("客户端已无法发送数据！");
                            ClientHandler.this.exitBySelf();
                        }
                    }
                }
            }
        }
    }
}
