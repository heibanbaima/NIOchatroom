import core.IoContext;

import java.io.IOException;

public class Server {
    public static void main(String[] args) throws IOException{
        IoContext.setup().ioProvider(new IoSelectorProvider())
    }
}
