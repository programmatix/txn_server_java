/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.couchbase.sdkd.util;

import java.io.*;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.couchbase.client.core.env.CoreEnvironment;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.env.ClusterEnvironment;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;


/**
 * Class for storing fairly content and dumb static SDKD content.
 * Currently this stores the global client list as well as the maximum per-vb
 * queue size.
 *
 * @author mnunberg
 */
public class SdkdConfig {
    /** how many SDKd threads should share a single CouchbaseClient */
    public static int handleShareCount = 100;
    public static long queueBlockTimeout = 0;
    public static int queueCapacity = 0;
    public static int cancelWait = 45;
    public static int pFactor = 95;
    public static boolean getSync = true;
    public static long defaultViewTimeout = 30000;
    public static long defaultConnectTimeout = 30000;
    public static boolean sslEnabled = false;
    public static String sslKeystoreFile = "cacerts";
    public static String sslKeystorePass = "couchbase";
    public static String sslServerCertPath = "/opt/couchbase/var/lib/couchbase/config/ssl-cert-key.pem-ca";
    public static String sslCert = "ssl-cert-key.pem-ca";
    public static String sslAlias = "ssl_situational";
    public static KeyStore keystore;
    public static String clientInterface = "sync";
    public static String logFile = "";
    public static String username = "";
    public static int autoFailoverTimeoutMs = 0;
    static private boolean isCertGenerated = false;
    private static Logger logger = SdkdConfig.getLogger("sdkdconfig");
    private static String certKeystoreFile = "cert/keystore.jks";
    private static String certKeystorePass = "123456";

    static private Lock sdkLock = new ReentrantLock();
    static ArrayList<SharedHandle> clientList = new ArrayList<SharedHandle>();


    static private SharedHandleRRComparator shComparator =
            new SharedHandleRRComparator();
    private static Cluster cluster;
    private static Bucket bucket;
    //public static ClusterEnvironment env;
    public static CoreEnvironment env;
    public static ClusterEnvironment certenv;

    static public int getQueueLimit() {
        return 1000;
    }

    static private void sortClientList() {
        SharedHandle[] l = new SharedHandle[clientList.size()];
        clientList.toArray(l);
        Arrays.sort(l, shComparator);
        clientList.clear();
        clientList.addAll(Arrays.asList(l));
    }

    /**
     * Return a new SharedHandle object, which is a wrapper around a
     * CouchbaseClient.
     * @return A SharedHandle.
     * @throws IOException
     */
    static public SharedHandle getClient(String seedNode, String bucketName, String passwd, boolean clientCert) throws IOException {
        SharedHandle ret = null;
        Cluster cluster = Cluster.connect(seedNode, Strings.LOGIN_ADMIN, passwd);
        Bucket bucket = cluster.bucket(bucketName);
        if (handleShareCount == 0) {
            ret = new SharedHandle();
            ret.setHandle(bucket);
            ret.setCluster(cluster);
            ret.setDefaultHandleCollection(bucket.defaultCollection());
            return ret;
        }
        sdkLock.lock();

        try {
            if (clientList.isEmpty()) {
                ret = new SharedHandle();
                clientList.add(ret);
            }
            ret = clientList.get(0);
            /* smallest count is not small enough! */
            if (ret.getRefCount() >= handleShareCount) {
                ret = new SharedHandle();
                clientList.add(ret);
            }
            if (ret.getHandle() == null) {
                ret.setHandle(bucket);
            }
            if(ret.getCluster() == null){
                ret.setCluster(cluster);
            }
            if(ret.getDefaultHandleCollection() == null){
                ret.setDefaultHandleCollection(bucket.defaultCollection());
            }

            ret.ref();
        } finally {
            sortClientList();
            sdkLock.unlock();
        }

        return ret;
    }

    private static Bucket connect(String node, String bucketName, String passwd, boolean clientCert) {
        if (clientCert) {
            if (!isCertGenerated) {
                int ret = initCert("default", node);
                if (ret != 0) {
                    logger.severe("Failed to init certificate");
                    return null;
                }
                File[] flist = new File(".").listFiles();
                for (File f: flist) {
                    logger.info("file:"+f.getName());
                }
                copy_ssl_cert(node);
                setup_keystore();

                SdkdConfig.certenv =  ClusterEnvironment.builder().build();
                try {
                    SdkdConfig.env.securityConfig().trustManagerFactory().init(keystore);
                }catch(KeyStoreException kse){
                    sdkdLogger().log(Level.SEVERE, "Cannot initialize keystore: ", kse);
                }


//                SdkdConfig.certenv = DefaultCouchbaseEnvironment.builder()
//                        .sslEnabled(true)
//                        .sslKeystoreFile(certKeystoreFile)
//                        .sslKeystorePassword(certKeystorePass)
//                        .connectTimeout(50000)
//                        .certAuthEnabled(true)
//                        .build();

                isCertGenerated = true;
            }
            cluster = Cluster.connect(node, Strings.LOGIN_ADMIN, passwd);

            bucket = cluster.bucket(bucketName);
            return bucket;

        } else {
            return connect(node, bucketName, passwd);
        }
    }

    private static int initCert(String username, String node) {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command("./gen_keystore.sh", node, username);
        builder.directory(new File("cert"));
        int exitcode = 1;
        StringBuffer output = new StringBuffer();

        // generate cert and install on couchbase server
        try {
            Process p = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String line = "";
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }
            exitcode = p.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (output.length() > 0) {
            logger.severe(output.toString());
        }
        return exitcode;
    }

    private static Bucket connect(String node, String bucketName, String passwd) {
        // Set SSL
        if(SdkdConfig.sslEnabled){
            copy_ssl_cert(node);
            setup_keystore();
        }
        cluster = Cluster.connect(node, Strings.LOGIN_ADMIN, passwd);
        bucket = cluster.bucket(bucketName);//.toBlocking().single();
        return bucket;
    }

    /**
     * copy couchbase ssl cert from server to local
     * @param node  host to get the ssl certificate from
     */
    public static void copy_ssl_cert(String node){
        String hostname = node;
        String username = "root";
        String password = "couchbase";
        String copyFrom = SdkdConfig.sslServerCertPath;
        String copyTo = System.getProperty("user.dir"); // current dir

        JSch jsch = new JSch();
        Session session = null;
        try {
            session = jsch.getSession(username, hostname, 22);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setPassword(password);
            session.connect();

            Channel channel = session.openChannel("sftp");
            channel.connect();
            ChannelSftp sftpChannel = (ChannelSftp) channel;

            sftpChannel.get(copyFrom, copyTo);
            sftpChannel.exit();
            session.disconnect();
        } catch (JSchException e) {
            e.printStackTrace();
        } catch (SftpException e) {
            e.printStackTrace();
        }
    }

    /**
     * create keystore programmatically, import server ssl certificate
     * and generate keystore file
     */
    public static void setup_keystore(){
        //create keystore
        try{
            //load the current keystore content
            keystore = KeyStore.getInstance(KeyStore.getDefaultType());

            File keystoreFile = new File(SdkdConfig.sslKeystoreFile);
            FileInputStream is = new FileInputStream(keystoreFile);
            char[] password = SdkdConfig.sslKeystorePass.toCharArray();
            keystore.load(is, password);

            //import the ssl server certificate
            String alias = "ssl_situational";
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            InputStream certstream = fullStream (sslCert);
            Certificate certs = cf.generateCertificate(certstream);
            // Add the certificate
            keystore.setCertificateEntry(alias, certs);
            // Save the new keystore contents
            FileOutputStream out = new FileOutputStream(keystoreFile);
            keystore.store(out, password);
            out.close();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * convert certificate file path from string to byteArrayInput stream
     *
     * @param fname certificate file path
     * @return byteArrayInput stream form of the certificate file path
     */
    public static InputStream fullStream(String fname) throws IOException {
        FileInputStream fis = new FileInputStream(fname);
        DataInputStream dis = new DataInputStream(fis);
        byte[] bytes = new byte[dis.available()];
        dis.readFully(bytes);
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        dis.close();
        return bais;
    }

    /**
     * clean up ssl certificate config at the end of the run
     *
     * @param fname ssl cert file name
     */
    public static void clean_up_cert(String fname){
        File ssl_file = new File(fname);
        if(!ssl_file.delete()){
            System.out.println("Delete" + ssl_file.getName() +" failed.");
        }

        try {
            keystore.deleteEntry(sslAlias);
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }
    }

    /**
     * Release the SharedHandle object. SharedHandle objects may be shared
     * and this call effectively decrements the reference count allowing
     * for prompt destruction and cleanup.
     *
     * @param cli A recieved SharedHandle object from @ref getClient
     */
    static public void releaseClient(SharedHandle cli) {

        if (handleShareCount == 0) {
           cluster.disconnect();
            //  cluster.disconnect();//.toBlocking().single();

            return;
        }

        // Clean up SSL
        if(SdkdConfig.sslEnabled){
            clean_up_cert(sslCert);
        }

        sdkLock.lock();
        try {
            if (cli.unref() <= 0) {
                clientList.remove(cli);
            }
            sortClientList();
        } finally {
            sdkLock.unlock();
        }
    }

    public static CoreEnvironment environment() {
        return env;
    }

    public static Logger sdkdLogger() {
        return LogUtil.sdkdLogger();
    }

    public static Logger getLogger(String s) {
        return LogUtil.getLogger(s);
    }

    /**
     * @author mnunberg
     */
    public static class SharedHandleRRComparator implements Comparator<SharedHandle> {

        @Override
        public int compare(SharedHandle o1, SharedHandle o2) {
            return o1.getRefCount() - o2.getRefCount();
        }
    }

}
