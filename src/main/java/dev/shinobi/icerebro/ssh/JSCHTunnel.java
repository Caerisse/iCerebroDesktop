package dev.shinobi.icerebro.ssh;

import dev.shinobi.icerebro.controllers.TunnelController;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.SocketException;

public class JSCHTunnel {
    private JSch jsch;
    private File root;
    private File known_hosts;
    private File private_key;
    private File public_key;
    private String passphrase;
    private Session session;
    private DynamicForwarder df;
    private ReverseDynamicForwarder rdf;

    public JSCHTunnel() {
        jsch = new JSch();
    }

    public boolean setKnownHosts(String host_key) {
        try {
            known_hosts = new File(root, "known_hosts");
            if (!known_hosts.isFile()) {
                boolean success = known_hosts.createNewFile();
                if (!success) {
                    System.out.println("setKnownHosts failed to create file");
                    known_hosts = null;
                    return false;
                }
            }
            FileWriter writer = new FileWriter(known_hosts);
            writer.append(host_key);
            writer.flush();
            writer.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            known_hosts = null;
            return false;
        }
    }

    public File[] generateAuthKeys() {
        return generateAuthKeys(2, 3072, "", "");
    }

    public File[] generateAuthKeys(int type, int size, String passphrase, String comment) {
        this.passphrase = passphrase;

        File[] keys = {null, null};

        String[] type_names = {"dsa", "rsa", "ecdsa"};
        String type_name;
        try {
            type_name = type_names[type - 1];
        } catch (IndexOutOfBoundsException e) {
            System.out.println("type parameter must be between 1 and 3 corresponding to the encryption desired 1=dsa, 2=rsa, 3=ecdsa");
            System.out.println("Generating key with default type (2=rsa)");
            return generateAuthKeys(2, size, passphrase, comment);
        }

        private_key = new File(root, "id_" + type_name);
        public_key = new File(root, "id_" + type_name + ".pub");

        if (private_key.exists() && public_key.exists()) {
            // TODO also check they are the same type and size
            keys[0] = private_key;
            keys[1] = public_key;
        } else {
            try {
                KeyPair k_pair = KeyPair.genKeyPair(jsch, type, size);
                k_pair.writePrivateKey(private_key.getAbsolutePath(), passphrase.getBytes());
                k_pair.writePublicKey(public_key.getAbsolutePath(), comment);
                System.out.println("Finger print: " + k_pair.getFingerPrint());

                keys[0] = private_key;
                keys[1] = public_key;

                k_pair.dispose();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return keys;
    }

    public void connect(String host, int port, String user, TunnelController.Monitor monitor) throws JSchException {
        connect(host, port, user, null, monitor);
    }

    public void connect(String host, int port, String user, String password, TunnelController.Monitor monitor) throws JSchException {
        System.out.println("Connecting to: " + user + "@" + host + ":" + port);
        session = jsch.getSession(user, host, port);
        if (password != null) {
            session.setPassword(password);
        }
        if (private_key.exists()) {
            jsch.addIdentity(private_key.getAbsolutePath(), passphrase);
        }
        if (known_hosts != null){
            jsch.setKnownHosts(known_hosts.getAbsolutePath());
        } else {
            session.setConfig("StrictHostKeyChecking", "no");
        }

        localUserInfo lui = new localUserInfo();
        session.setUserInfo(lui);
        System.out.println("Creating connection to host: " + host);
        session.connect();
        if (session.isConnected()){
            System.out.println("Connected successfully");
            monitor.writeTitle("Connected to " + host);
        } else {
            System.out.println("Failed to connect");
            monitor.writeTitle("Failed to connect to " + host);
        }
    }

    public void startPortForwardingL(int tunnelLocalPort, String tunnelRemoteHost, int tunnelRemotePort, TunnelController.Monitor monitor)
            throws JSchException
    {
        System.out.println("Initializing Local Port forwarding ");
        session.setPortForwardingL(tunnelLocalPort, tunnelRemoteHost, tunnelRemotePort);
        System.out.println("Port forward successful forwarding: " +
                "localhost:" + tunnelLocalPort + " -> " + tunnelRemoteHost + ":" + tunnelRemotePort);
        monitor.writeTitle("localhost:" + tunnelLocalPort + " -> " + tunnelRemoteHost + ":" + tunnelRemotePort);
    }

    public void startPortForwardingR(int tunnelRemotePort, String tunnelRemoteHost, int tunnelLocalPort, TunnelController.Monitor monitor)
            throws JSchException
    {
        System.out.println("Initializing Remote Port forwarding ");
        session.setPortForwardingR(tunnelRemotePort, "localhost", tunnelLocalPort);
        System.out.println("Port forward successful forwarding: " +
                tunnelRemoteHost + ":" + tunnelRemotePort  + " -> " + "localhost:" + tunnelLocalPort);
        monitor.writeTitle(tunnelRemoteHost + ":" + tunnelRemotePort  + " -> " + "localhost:" + tunnelLocalPort);
    }

    public void startDynamicPortForwarding(int port, Session session, TunnelController.Monitor monitor) throws JSchException {
        df = new DynamicForwarder(port, session);
    }

    public void startReverseDynamicPortForwarding(int tunnelRemotePort, String tunnelRemoteHost, int tunnelLocalPort, TunnelController.Monitor monitor)
            throws JSchException {
        startPortForwardingR(tunnelRemotePort, tunnelRemoteHost, tunnelLocalPort, monitor);
        rdf = new ReverseDynamicForwarder(tunnelLocalPort, monitor);
    }

    public void stop(){
        if (df != null) {
            df.stop();
        }
        if (rdf != null) {
            rdf.stop();
        }
        if (session != null && session.isConnected()) {
            System.out.println("Closing SSH Connection");
            session.disconnect();
        }
    }

    public void setRootDir(String name) {
        root = new File(System.getProperty("user.home"), name);
        int i = 0;
        while (root.exists() && !root.isDirectory()){
            root = new File(System.getProperty("user.home"), name + "_" + i);
            i++;
        }
        if (!root.exists()) {
            boolean success = root.mkdirs();
            if (!success) {
                System.out.println("setRootDir failed to create directory");
                root = null;
            }
        }
    }

    static class localUserInfo implements UserInfo {
        String passwd;
        public String getPassword() {
            return passwd;
        }
        public boolean promptYesNo(String str) {
            return true;
        }
        public String getPassphrase() {
            return null;
        }
        public boolean promptPassphrase(String message) {
            return true;
        }
        public boolean promptPassword(String message) {
            return true;
        }
        public void showMessage(String message) {
        }
    }

}