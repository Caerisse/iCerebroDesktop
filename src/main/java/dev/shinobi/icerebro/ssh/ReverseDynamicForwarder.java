package dev.shinobi.icerebro.ssh;

import dev.shinobi.icerebro.controllers.TunnelController;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import jsocks.socks.CProxy;
import jsocks.socks.ProxyMessage;
import jsocks.socks.ProxyServer;
import jsocks.socks.Socks4Message;
import jsocks.socks.Socks5Message;
import jsocks.socks.SocksSocket;
import jsocks.socks.server.ServerAuthenticatorNone;

class ReverseDynamicForwarder implements Runnable {
    private class ReverseDynamicForward extends ProxyServer {
        int port;
        TunnelController.Monitor monitor;

        ReverseDynamicForward(int port, TunnelController.Monitor monitor) {
            super(new ServerAuthenticatorNone());
            this.port = port;
            this.monitor = monitor;
            setLog(System.out);
        }

        ReverseDynamicForward(Socket s, int port, TunnelController.Monitor monitor) {
            super(new ServerAuthenticatorNone(), s);
            this.port = port;
            this.monitor = monitor;
            setLog(System.out);
        }

        @Override
        public void start(int port, int backlog, InetAddress localIP) throws IOException {
            ss = new ServerSocket(port, backlog, localIP);
            System.out.println("Starting SOCKS Proxy on: " + ss.getInetAddress().getHostAddress() + ":" + ss.getLocalPort());
            monitor.writeToMonitor("Starting SOCKS Proxy on: " + ss.getInetAddress().getHostAddress() + ":"
                    + ss.getLocalPort());

            //noinspection InfiniteLoopStatement
            while (true) {
                Socket s = ss.accept();
                System.out.println("Accepted from: " + s.getInetAddress().getHostName() + ":" + s.getPort());
                monitor.writeToMonitor("Accepted from: " + s.getInetAddress().getHostName() + ":" + s.getPort());
                ReverseDynamicForward rdf = new ReverseDynamicForward(s, port, monitor);
                (new Thread(rdf)).start();
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

            Socket s = null;

            try {
                if (proxy == null) {
                    s = new Socket(msg.ip, msg.port);
                } else {
                    s = new SocksSocket(proxy, msg.ip, msg.port);
                }
                System.out.println("Connected to " + s.getInetAddress() + ":" + s.getPort());
                monitor.writeToMonitor("Connected to " + s.getInetAddress() + ":" + s.getPort());

                iSock5Cmd = CProxy.SOCKS_SUCCESS; iSock4Msg = Socks4Message.REPLY_OK;
                sIp = s.getInetAddress();
                iPort = s.getPort();

            }
            catch (Exception sE) {
                System.out.println("Failed connecting to remote socket. Exception: " + sE.getLocalizedMessage());
                monitor.writeToMonitor("Failed connecting to remote socket. Exception: " + sE.getLocalizedMessage());

                //TBD Pick proper socks error for corresponding socket error, below is too generic
                iSock5Cmd = CProxy.SOCKS_CONNECTION_REFUSED; iSock4Msg = Socks4Message.REPLY_NO_CONNECT;
            }

            if (msg instanceof Socks5Message) {
                response = new Socks5Message(iSock5Cmd, sIp, iPort);
            } else {
                response = new Socks4Message(iSock4Msg, sIp, iPort);
            }

            response.write(out);

            if (s != null) {
                startPipe(s);
            }
            else {
                monitor.writeToMonitor("onConnect() Failed to create Socket()");
                throw (new RuntimeException("onConnect() Failed to create Socket()"));
            }

        }

        void start() throws IOException {
            System.out.println("Starting ReverseDynamicForward");
            start(port);
        }
    }

    private ReverseDynamicForward rdf = null;
    private Thread ServerThread = null;
    private Exception e = null;


    ReverseDynamicForwarder(int port, TunnelController.Monitor monitor) {
        rdf = new ReverseDynamicForward(port, monitor);
        ReverseDynamicForward.setLog(System.out);
        ServerThread = new Thread(this, "ReverseDynamicForwarder");
        ServerThread.start();
    }

    public void run() {
        try {
            System.out.println("Starting ReverseDynamicForward in Thread: " + Thread.currentThread().getName());
            rdf.start();
        } catch (IOException e) {
            e.printStackTrace();
            this.e = e;
//            rdf.stop();
        }
    }

    void stop() {
        rdf.stop();
        if (!ServerThread.isInterrupted()) {
            ServerThread.interrupt();
        }
    }
}
