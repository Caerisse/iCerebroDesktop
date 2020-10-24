package dev.shinobi.icerebro.ssh;

import com.jcraft.jsch.ChannelDirectTCPIP;
import com.jcraft.jsch.Session;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import jsocks.socks.CProxy;
import jsocks.socks.ProxyMessage;
import jsocks.socks.ProxyServer;
import jsocks.socks.Socks4Message;
import jsocks.socks.Socks5Message;
import jsocks.socks.server.ServerAuthenticatorNone;

class DynamicForwarder implements Runnable {
    private class DynamicForward extends ProxyServer {

        Session session;
        ChannelDirectTCPIP channel;
        int port;

        DynamicForward(Session session, int port) {
            super(new ServerAuthenticatorNone());
            System.out.println("Creating DynamicForward: session=" + session + ", port=" + port);
            this.session = session;
            this.port = port;

            setLog(System.out);
        }

        DynamicForward(Socket s, Session session, int port) {
            super(new ServerAuthenticatorNone(), s);
            System.out.println("Creating DynamicForward: socket=" + s + "session=" + session + ", port=" + port);
            this.session = session;
            this.port = port;
            setLog(System.out);
        }

        @Override
        public void start(int port, int backlog, InetAddress localIP) throws IOException {
            ss = new ServerSocket(port, backlog, localIP);
            System.out.println("Starting SOCKS Proxy on: "+ss.getInetAddress().getHostAddress()+":"+ss.getLocalPort());

            //noinspection InfiniteLoopStatement
            while (true) {
                Socket s = ss.accept();
                DynamicForward df = new DynamicForward(s, session, port);
                (new Thread(df)).start();
            }
        }

        @Override
        protected void onConnect(ProxyMessage msg) throws IOException {
            System.out.println("onConnect");
            ProxyMessage response = null;
            int iSock5Cmd = CProxy.SOCKS_FAILURE;    //defaulting to failure
            int iSock4Msg = Socks4Message.REPLY_NO_CONNECT;
            InetAddress sIp = null;
            int iPort = 0;
            ChannelDirectTCPIP _channel = null;

            try {
                System.out.println("Trying to open direct-tcpip channel");
                _channel = (ChannelDirectTCPIP) session.openChannel("direct-tcpip");
                _channel.setHost(msg.host);
                _channel.setPort(msg.port);
                _channel.connect();

                iSock5Cmd = CProxy.SOCKS_SUCCESS;
                iSock4Msg = Socks4Message.REPLY_OK;
                sIp = msg.ip;
                iPort = msg.port;

            } catch (Exception sE) {
                System.out.println("Failed connecting to remote socket. Exception: " + sE.getLocalizedMessage());

                //TBD Pick proper socks error for corresponding socket error, below is too generic
                iSock5Cmd = CProxy.SOCKS_CONNECTION_REFUSED;
                iSock4Msg = Socks4Message.REPLY_NO_CONNECT;
            }

            if (msg instanceof Socks5Message) {
                response = new Socks5Message(iSock5Cmd, sIp, iPort);
            } else {
                response = new Socks4Message(iSock4Msg, sIp, iPort);
            }

            response.write(out);
            
            if (_channel != null) {
                startPipe(_channel);
            } else {
                throw (new RuntimeException("onConnect() Failed to create Socket()"));
            }

        }

        protected void startPipe(ChannelDirectTCPIP _channel) {
            System.out.println("startPipe");
            mode = PIPE_MODE;
            channel = _channel;
            try {
                remote_in = _channel.getInputStream();
                remote_out = _channel.getOutputStream();
                pipe_thread1 = Thread.currentThread();
                pipe_thread2 = new Thread(this);
                pipe_thread2.start();
                pipe(in, remote_out);
            } catch (IOException | NullPointerException ignored) {
            }
        }

        @Override
        protected synchronized void abort() {
            super.abort();
            System.out.println("aborting");
            if (channel != null) {
                channel.disconnect();
            }
        }

        void start() throws IOException {
            System.out.println("Starting DynamicForward");
            start(port);
        }
    }

    private DynamicForward df = null;
    private Thread ServerThread = null;
    private Exception e = null;

    DynamicForwarder(int port, Session session) {
        df = new DynamicForward(session, port);
        DynamicForward.setLog(System.out);
        ServerThread = new Thread(this, "DynamicForwarder");
        ServerThread.start();
    }

    public void run() {
        try {
            df.start();
        } catch (IOException e) {
            this.e = e;
        }
    }

    void stop() {
        df.stop();
        if (!ServerThread.isInterrupted()) {
            ServerThread.interrupt();
        }
    }
}
