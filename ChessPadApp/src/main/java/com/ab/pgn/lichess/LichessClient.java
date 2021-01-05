package com.ab.pgn.lichess;

import com.ab.pgn.BitStream;
import com.ab.pgn.Config;
import com.ab.pgn.PgnLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterInputStream;
import java.util.zip.GZIPInputStream;

/**
 * Created by Alexander Bootman on 4/4/2020.
 * HttpClient from  org.apachi is a mess, lots of incompatible versions. Additionally, this code should work in
 * both general-purpose JVM and Android. So i's much easier just to use java.net.HttpURLConnection
 */
public class LichessClient {
    private static final boolean DEBUG = false;

    static final String
//*
        DOMAIN = "lichess.org",
/*/
        DOMAIN = "lichess.dev",
//*/
        DOMAIN_URL = "https://" + DOMAIN,
        LOGIN_URL = DOMAIN_URL + "/login",
        LOGOUT_URL = DOMAIN_URL + "/logout",
        TRAINING_URL_REQUEST = DOMAIN_URL + "/training/mix",
        TRAINING_URL_COMPLETE = DOMAIN_URL + "/training/complete/mix/",

        SESSION_COOKIE_MARK = "sessionId=",

        str_dummy = null;

    public static final int
        CONNECT_TIMEOUT_MSEC = 5000,
        LICHESS_UNKNOWN_ERROR = 0,
        LICHESS_RESULT_OK = HttpURLConnection.HTTP_OK,
        int_dummy = 0;

    private static final PgnLogger logger = PgnLogger.getLogger(LichessClient.class);

//    private static long seed = new Date().getTime();
    private volatile HttpCookie sessionCookie;

    private void initDebug() {
        final String cookieText =
            "lila2=df8540e31ed3ec39ccbc34fd06f038d8a6474e48-sessionId=DQWtGaRBGxtoTA5RupPrUB&sid=2VzaUcSpCcGhj0bt4l5xP3"
            ;
// uncomment for debug mode:
//         parseCookie(cookieText);
    }

    public LichessClient(String user, String password) throws Config.PGNException {
        initDebug();
        login(user, password);
    }

    public LichessClient() {
        initDebug();
    }

    public void serialize(BitStream.Writer writer) throws Config.PGNException {
        try {
            if (sessionCookie == null) {
                writer.write(0, 1);
            } else {
                String s = sessionCookie.toString();
                writer.write(1, 1);
                writer.writeString(sessionCookie.toString());
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public LichessClient(BitStream.Reader reader) throws Config.PGNException {
        try {
            if (reader.read(1) == 1) {
                parseCookie(reader.readString());
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
        initDebug();
    }

    private void  parseCookie(String header) {
        if (header != null) {
            List<HttpCookie> cookies = HttpCookie.parse(header);
            for (HttpCookie cookie : cookies) {
                if (cookie.getValue().contains(SESSION_COOKIE_MARK)) {
                    sessionCookie = cookie;
                    logger.debug(String.format("new sessionCookie %s", sessionCookie.toString()));
                }
            }
        }
    }

    public int login(String user, String password) throws Config.PGNException {
        int result;
        logout();
        if (user == null || user.isEmpty() || password == null || password.isEmpty()) {
            return HttpURLConnection.HTTP_UNAUTHORIZED;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("username=").append(user);
        sb.append("&").append("password=").append(password);
        byte[] postDataBytes = sb.toString().getBytes(Charset.defaultCharset());

        try {
            URL url = new URL(LOGIN_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MSEC);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
            conn.setDoOutput(true);
            conn.getOutputStream().write(postDataBytes);
            conn.getOutputStream().close();

            result = conn.getResponseCode();
            // HTTP_UNAUTHORIZED (401) on wrong password
            // HTTP_SEE_OTHER (303) on correct authorization
            if (result == HttpURLConnection.HTTP_SEE_OTHER) {
                String header = conn.getHeaderField("Set-Cookie");
                parseCookie(header);
                if (sessionCookie != null) {
                    result = LICHESS_RESULT_OK;
                }
            }
            String res = conn.getResponseMessage();
            conn.disconnect();
            logger.debug(String.format("Lichess login result %s, %s", result, res));
        } catch (IOException e) {
            throw  new Config.PGNException(e);
        }
        return result;
    }

    public void logout() {
        final String[] requestProperties = {
            "authority: " + DOMAIN,
            "accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3",
            "accept-encoding: gzip, deflate",
            "accept-language: en-US,en;q=0.9",
            "cache-control: max-age=0",
            "content-length: 0",
            "content-type: application/x-www-form-urlencoded",
            "origin: " + DOMAIN_URL,
            "user-agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.80 Safari/537.36",
        };
        if (sessionCookie == null) {
            return;     // no need, ignore
        }
        try {
            URL url = new URL(LOGOUT_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MSEC);
            conn.setRequestMethod("POST");
            if (DEBUG) {
                logger.debug(String.format("logout %s", url.toString()));
            }
            for (String prop : requestProperties) {
                String[] parts = prop.split(": ");
                if (DEBUG) {
                    logger.debug(String.format("%s: %s", parts[0], parts[1]));
                }
                conn.setRequestProperty(parts[0], parts[1]);
            }
            String cookie = sessionCookie.getName() + "=" + sessionCookie.getValue();
            conn.setRequestProperty("cookie", cookie);

            int result = conn.getResponseCode();
            String res = conn.getResponseMessage();
            conn.disconnect();
            logger.debug(String.format("logout result %s, %s", result, res));
        } catch (IOException e) {
            logger.error(e);        // ignore failure
        }
        sessionCookie = null;
    }

    public boolean isUserLoggedIn() {
        return sessionCookie != null;
    }

    private String fetchPuzzle(URL url) throws IOException {
        final String[] requestProperties = {
            "authority: " + DOMAIN,
            "scheme: https",
            "accept: */*",
            "accept-encoding: gzip, deflate",
            "accept-language: en-US,en;q=0.9",
            "user-agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.80 Safari/537.36",
            "x-requested-with: XMLHttpRequest",
        };

        StringBuilder sb = new StringBuilder();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MSEC);
        try {
            if (DEBUG) {
                logger.debug(String.format("fetch %s", url.toString()));
            }
            for (String prop : requestProperties) {
                String[] parts = prop.split(": ");
                if (DEBUG) {
                    logger.debug(String.format("%s: %s", parts[0], parts[1]));
                }
                conn.setRequestProperty(parts[0], parts[1]);
            }
            if (sessionCookie != null) {
                String newCookie = sessionCookie.getName() + "=" + sessionCookie.getValue();
                conn.setRequestProperty("cookie", newCookie);
                if (DEBUG) {
                    logger.debug(String.format("%s: %s", "cookie", newCookie));
                }
            }
            // todo: verify!
//            conn.setRequestProperty("referer", String.format(Locale.US, "https://lichess.org/training/%d", puzzleId));  // old puzzleId
//            System.out.println(String.format("%s: %s", "referer", String.format(Locale.US, "https://lichess.org/training/%d", puzzleId)));

//        for (Map.Entry<String, List<String>> entry : conn.getRequestProperties().entrySet()) {
//            System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue());
//        }

            if (DEBUG) {
                for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                    logger.debug("Key = " + entry.getKey() + ", Value = " + entry.getValue());
                }
            }

            InputStream in = conn.getInputStream();
            BufferedReader br;
            String contentEncoding = conn.getContentEncoding();
            if ("gzip".equals(contentEncoding)) {
                br = new BufferedReader(new InputStreamReader(new GZIPInputStream(in)));
            } else if ("deflate".equals(contentEncoding)) {
                // todo: test! Lichess server does not support it
                br = new BufferedReader(new InputStreamReader(new DeflaterInputStream(in)));
            } else {
                br = new BufferedReader(new InputStreamReader(in));
            }
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
                if (DEBUG) {
                    logger.debug(line);
                }
            }
        } catch (Exception e) {
            for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                logger.debug("Key = " + entry.getKey() + ", Value = " + entry.getValue());
            }
        } finally {
            conn.disconnect();
        }

        return new String(sb);
    }

    public String getPuzzle(String puzzleId) throws IOException {
        URL url = new URL(TRAINING_URL_REQUEST + "/" + puzzleId);
        return fetchPuzzle(url);
    }

    // get next puzzle
    public String getPuzzle() throws IOException {
//        URL url = new URL(TRAINING_URL + "new?_=" + (++seed));
        URL url = new URL(TRAINING_URL_REQUEST);
        return fetchPuzzle(url);
    }

    /**
     *
     * @param puzzleId
     * @param result 1 for success, 0 for failure
     */
    public void recordResult(String puzzleId, int result) throws IOException {
        logger.debug(String.format("record %s", puzzleId));

        final SimpleDateFormat gmtDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'");
        gmtDateFormat.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));

        final String[] requestProperties = {
            "Authority: " + DOMAIN,
            "Scheme: https",
//            "Accept: */*",
            "accept: application/vnd.lichess.v5+json",
            "Accept-Encoding: gzip, deflate",
            "Accept-Language: en-US,en;q=0.9",
            "Content-Type: application/x-www-form-urlencoded; charset=UTF-8",
//            "content-type: multipart/form-data; boundary=----WebKitFormBoundary6KV7wz1PUIhe39x6",
            "Origin: " + DOMAIN_URL,
            "referer: " + TRAINING_URL_REQUEST,
//            "User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.80 Safari/537.36",
            "user-agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36",

                // new:
//        "Referrer Policy: strict-origin-when-cross-origin",
//        "content-encoding: identity",
//        "content-type: application/json",
//        "date: " + gmtDateFormat.format(new Date()),
//        "expect-ct: max-age=31536000, enforce",


//        server: nginx
//        strict-transport-security: max-age=31536000; includeSubDomains; preload
//        x-frame-options: DENY
//        :authority: lichess.org
//:method: POST
//        :path: /training/complete/mix/73TlC
//        :scheme: https
//        accept: application/vnd.lichess.v5+json
//        accept-encoding: gzip, deflate, br
//        accept-language: en-US,en;q=0.9,ru;q=0.8
//        cache-control: max-age=0
//        content-length: 135
//        content-type: multipart/form-data; boundary=----WebKitFormBoundary6KV7wz1PUIhe39x6
//        cookie: lila2=0fc5b264ee1c6c7dacad2838615cd67dbec4dfb2-sessionId=H4TGaR4AxJ87EhcrzR5fF3&sid=8y3oqSPjAv7bInzki6d965
//        origin: https://lichess.org
//        referer: https://lichess.org/training/mix
//        sec-ch-ua: "Google Chrome";v="87", " Not;A Brand";v="99", "Chromium";v="87"
//        sec-ch-ua-mobile: ?0
//        sec-fetch-dest: empty
//        sec-fetch-mode: cors
//        sec-fetch-site: same-origin
//        user-agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36
//        x-requested-with: XMLHttpRequest


        };
        String postData = "win=" + result;
        byte[] postDataBytes = postData.getBytes("UTF-8");

        URL url = new URL(TRAINING_URL_COMPLETE + puzzleId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
//            conn.setRequestProperty("referer", TRAINING_URL + puzzleId);
            if (sessionCookie != null) {
                String newCookie = sessionCookie.getName() + "=" + sessionCookie.getValue();
                conn.setRequestProperty("cookie", newCookie);
            }
            for (String prop : requestProperties) {
                String[] parts = prop.split(": ");
                if (DEBUG)
                {
                    logger.debug(String.format("request %s: %s", parts[0], parts[1]));
                }
                conn.setRequestProperty(parts[0], parts[1]);
            }
            conn.setDoOutput(true);
            conn.getOutputStream().write(postDataBytes);
            conn.getOutputStream().close();

            if (DEBUG)
            {
                for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                    logger.debug("Key = " + entry.getKey() + ", Value = " + entry.getValue());
                }
            }
            String res = conn.getResponseMessage();
            if (DEBUG) {
                logger.debug(String.format("recordResult %s", res));
            }
        } finally {
            conn.disconnect();
        }
    }

    // todo:
    public String fetchPuzzleBatch() throws IOException {
        final String[] requestProperties = {
//                "authority: " + DOMAIN,
//                "scheme: https",
//                "accept: */*",
//                "accept-encoding: gzip, deflate",
//                "accept-language: en-US,en;q=0.9",
//                "content-type: application/x-www-form-urlencoded; charset=UTF-8",
//                "content-length: 0",
//                "origin: " + DOMAIN_URL,
//                "user-agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.80 Safari/537.36",

//                "authority: " + DOMAIN,
//                "scheme: https",
                "accept: */*",
                "accept-encoding: gzip, deflate",
                "accept-language: en-US,en;q=0.9",
                "user-agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.80 Safari/537.36",
//                "x-requested-with: XMLHttpRequest",
        };

        StringBuilder sb = new StringBuilder();
        URL url = new URL(TRAINING_URL_REQUEST + String.format("batch?nb=10"));
//        URL url = new URL(TRAINING_URL + String.format("batch?nb=10&after=80307"));
//        URL url = new URL(TRAINING_URL + String.format("batch?new=%s&nb=10", ++seed));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MSEC);
        try {
            logger.debug(String.format("fetchPuzzleBatch %s", url.toString()));
            for (String prop : requestProperties) {
                String[] parts = prop.split(": ");
                if (DEBUG) {
                    logger.debug(String.format("%s: %s", parts[0], parts[1]));
                }
                conn.setRequestProperty(parts[0], parts[1]);
            }
            if (sessionCookie != null) {
                String newCookie = sessionCookie.getName() + "=" + sessionCookie.getValue();
                conn.setRequestProperty("cookie", newCookie);
                if (DEBUG) {
                    logger.debug(String.format("%s: %s", "cookie", newCookie));
                }
            }
            // todo: verify!
//            conn.setRequestProperty("referer", String.format(Locale.US, "https://lichess.org/training/%d", puzzleId));  // old puzzleId
//            System.out.println(String.format("%s: %s", "referer", String.format(Locale.US, "https://lichess.org/training/%d", puzzleId)));

//        for (Map.Entry<String, List<String>> entry : conn.getRequestProperties().entrySet()) {
//            System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue());
//        }

            if (DEBUG) {
                for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                    logger.debug("Key = " + entry.getKey() + ", Value = " + entry.getValue());
                }
            }

            InputStream in = conn.getInputStream();
            BufferedReader br;
            String contentEncoding = conn.getContentEncoding();
            if ("gzip".equals(contentEncoding)) {
                br = new BufferedReader(new InputStreamReader(new GZIPInputStream(in)));
            } else if ("deflate".equals(contentEncoding)) {
                // todo: test! Lichess server does not support it
                br = new BufferedReader(new InputStreamReader(new DeflaterInputStream(in)));
            } else {
                br = new BufferedReader(new InputStreamReader(in));
            }
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
                if (DEBUG) {
                    logger.debug(line);
                }
            }
        } finally {
            conn.disconnect();
        }

        return new String(sb);
    }

}
