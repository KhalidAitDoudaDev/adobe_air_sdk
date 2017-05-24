package com.adjust.sdktesting;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Created by uerceg on 03/04/2017.
 */

public class UtilsNetworking {
    private static final String TAG = "UtilsNetworking";
    static ConnectionOptions connectionOptions;
    static TrustManager[] trustAllCerts;
    static HostnameVerifier hostnameVerifier;
    static Type stringStringMap;

    static {
        trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        Utils.debug("getAcceptedIssuers");

                        return null;
                    }

                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                        Utils.debug("checkClientTrusted");
                    }

                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                        Utils.debug("checkServerTrusted");
                    }
                }
        };
        hostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };
        connectionOptions = new ConnectionOptions();
    }

    public static class HttpResponse {
        public String response = null;
        public Integer responseCode = null;
        public Map<String, List<String>> headerFields = null;
    }

    public interface IConnectionOptions {
        void applyConnectionOptions(HttpsURLConnection connection);
    }

    static class ConnectionOptions implements IConnectionOptions {
        public String clientSdk;

        @Override
        public void applyConnectionOptions(HttpsURLConnection connection) {
            if (this.clientSdk != null) {
                connection.setRequestProperty("Client-SDK", clientSdk);

                //Inject local ip address for Jenkins script
                connection.setRequestProperty("Local-Ip", getIPAddress(true));
            }
            connection.setConnectTimeout(Constants.ONE_MINUTE);
            connection.setReadTimeout(Constants.ONE_MINUTE);
            try {
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                connection.setSSLSocketFactory(sc.getSocketFactory());

                connection.setHostnameVerifier(hostnameVerifier);
                Utils.debug("applyConnectionOptions");
            } catch (Exception e) {
                Utils.debug("applyConnectionOptions %s", e.getMessage());
            }
        }
    }

    static HttpsURLConnection createPOSTHttpsURLConnection(String urlString,
                                                           String postData,
                                                           IConnectionOptions connectionOptions)
            throws IOException {
        Log.d(TAG, "createPOSTHttpsURLConnection() called with: urlString = [" + urlString + "], postData = [" + postData + "], connectionOptions = [" + connectionOptions + "]");
        DataOutputStream wr = null;
        HttpsURLConnection connection = null;

        try {
            Utils.debug("POST request: %s", urlString);
            URL url = new URL(urlString);
            connection = (HttpsURLConnection) url.openConnection();

            connectionOptions.applyConnectionOptions(connection);

            connection.setRequestMethod("POST");
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);

            if (postData != null && postData.length() > 0) {
                wr = new DataOutputStream(connection.getOutputStream());
                wr.writeBytes(postData);
            }

            return connection;
        } catch (Exception e) {
            throw e;
        } finally {
            try {
                if (wr != null) {
                    wr.flush();
                    wr.close();
                }
            } catch (Exception e) {
            }
        }
    }

    static HttpResponse readHttpResponse(HttpsURLConnection connection) throws Exception {
        StringBuffer sb = new StringBuffer();
        HttpResponse httpResponse = new HttpResponse();

        try {
            connection.connect();

            httpResponse.responseCode = connection.getResponseCode();
            InputStream inputStream;

            if (httpResponse.responseCode >= 400) {
                inputStream = connection.getErrorStream();
            } else {
                inputStream = connection.getInputStream();
            }

            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            String line;

            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            Utils.error("Failed to read response. (%s)", e.getMessage());
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        httpResponse.response = sb.toString();
        return httpResponse;
    }

    static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return delim < 0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Utils.error("Failed to read ip address (%s)", ex.getMessage());
        }

        return "";
    }
}