/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8237470 8299015
 * @summary Confirm HttpResponse.BodySubscribers#ofFile(Path)
 *          works with default and non-default file systems
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.httpclient.test.lib.http2.Http2TestServer
 *        jdk.httpclient.test.lib.http2.Http2TestServerConnection
 *        jdk.httpclient.test.lib.http2.Http2TestExchange
 *        jdk.httpclient.test.lib.http2.Http2Handler
 *        jdk.httpclient.test.lib.http2.OutgoingPushPromise
 *        jdk.httpclient.test.lib.http2.Queue jdk.test.lib.net.SimpleSSLContext
 *        jdk.test.lib.Platform jdk.test.lib.util.FileUtils
 * @run testng/othervm BodySubscriberOfFileTest
 */

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.util.FileUtils;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.stream.IntStream;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http2.Http2TestServerConnection;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.httpclient.test.lib.http2.OutgoingPushPromise;
import jdk.httpclient.test.lib.http2.Queue;
import static java.lang.System.out;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.testng.Assert.assertEquals;

public class BodySubscriberOfFileTest implements HttpServerAdapters {
    static final String MSG = "msg";

    SSLContext sslContext;
    HttpServerAdapters.HttpTestServer httpTestServer;    // HTTP/1.1      [ 4 servers ]
    HttpServerAdapters.HttpTestServer httpsTestServer;   // HTTPS/1.1
    HttpServerAdapters.HttpTestServer http2TestServer;   // HTTP/2 ( h2c )
    HttpServerAdapters.HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;

    FileSystem zipFs;
    Path defaultFsPath;
    Path zipFsPath;

    // Default file system set-up

    static Path defaultFsFile() throws Exception {
        var file = Path.of("defaultFile.txt");
        if (Files.notExists(file)) {
            Files.createFile(file);
        }
        return file;
    }

    @DataProvider(name = "defaultFsData")
    public Object[][] defaultFsData() {
        return new Object[][]{
                {  httpURI,    defaultFsPath,  MSG,  true   },
                {  httpsURI,   defaultFsPath,  MSG,  true   },
                {  http2URI,   defaultFsPath,  MSG,  true   },
                {  https2URI,  defaultFsPath,  MSG,  true   },
                {  httpURI,    defaultFsPath,  MSG,  false  },
                {  httpsURI,   defaultFsPath,  MSG,  false  },
                {  http2URI,   defaultFsPath,  MSG,  false  },
                {  https2URI,  defaultFsPath,  MSG,  false  },
        };
    }

    @Test(dataProvider = "defaultFsData")
    public void testDefaultFs(String uriString,
                              Path path,
                              String expectedMsg,
                              boolean sameClient) throws Exception {
        out.printf("\n\n--- testDefaultFs(%s, %s, \"%s\", %b): starting\n",
                uriString, path, expectedMsg, sameClient);
        receive(uriString, path, expectedMsg, sameClient);
    }

    // Zip file system set-up

    static FileSystem newZipFs() throws Exception {
        Path zipFile = Path.of("file.zip");
        return FileSystems.newFileSystem(zipFile, Map.of("create", "true"));
    }

    static Path zipFsFile(FileSystem fs) throws Exception {
        var file = fs.getPath("fileInZip.txt");
        if (Files.notExists(file)) {
            Files.createFile(file);
        }
        return file;
    }

    @DataProvider(name = "zipFsData")
    public Object[][] zipFsData() {
        return new Object[][]{
                {  httpURI,    zipFsPath,  MSG,  true   },
                {  httpsURI,   zipFsPath,  MSG,  true   },
                {  http2URI,   zipFsPath,  MSG,  true   },
                {  https2URI,  zipFsPath,  MSG,  true   },
                {  httpURI,    zipFsPath,  MSG,  false  },
                {  httpsURI,   zipFsPath,  MSG,  false  },
                {  http2URI,   zipFsPath,  MSG,  false  },
                {  https2URI,  zipFsPath,  MSG,  false  },
        };
    }

    @Test(dataProvider = "zipFsData")
    public void testZipFs(String uriString,
                          Path path,
                          String expectedMsg,
                          boolean sameClient) throws Exception {
        out.printf("\n\n--- testZipFs(%s, %s, \"%s\", %b): starting\n",
                uriString, path, expectedMsg, sameClient);
        receive(uriString, path, expectedMsg, sameClient);
    }

    private static final int ITERATION_COUNT = 3;

    private void receive(String uriString,
                      Path path,
                      String expectedMsg,
                      boolean sameClient) throws Exception {
        HttpClient client = null;

        for (int i = 0; i < ITERATION_COUNT; i++) {
            if (!sameClient || client == null) {
                client = HttpClient.newBuilder()
                        .proxy(NO_PROXY)
                        .sslContext(sslContext)
                        .build();
            }
            var req = HttpRequest.newBuilder(URI.create(uriString))
                .POST(BodyPublishers.noBody())
                .build();

            BodyHandler<Path> handler = respInfo -> BodySubscribers.ofFile(path);
            var resp = client.send(req, handler);
            String msg = Files.readString(path, StandardCharsets.UTF_8);
            out.printf("Resp code: %s\n", resp.statusCode());
            out.printf("Msg written to %s: %s\n", resp.body(), msg);
            assertEquals(resp.statusCode(), 200);
            assertEquals(msg, expectedMsg);
        }
    }

    // A large enough number of buffers to gather from, in an attempt to provoke a partial
    // write. Loosely based on the value of _SC_IOV_MAX, to trigger partial gathering write.
    private static final int NUM_GATHERING_BUFFERS = 1024 + 1;

    @Test
    public void testSubscribersWritesAllBytes() throws Exception {
        var buffers = IntStream.range(0, NUM_GATHERING_BUFFERS)
                .mapToObj(i -> new byte[10])
                .map(ByteBuffer::wrap).toList();
        int expectedSize = buffers.stream().mapToInt(Buffer::remaining).sum();

        var subscriber = BodySubscribers.ofFile(defaultFsPath);
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) { }
            @Override
            public void cancel() { }
        });
        subscriber.onNext(buffers);
        subscriber.onComplete();
        buffers.forEach(b -> assertEquals(b.remaining(), 0) );
        assertEquals(expectedSize, Files.size(defaultFsPath));
    }

    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        defaultFsPath = defaultFsFile();
        zipFs = newZipFs();
        zipFsPath = zipFsFile(zipFs);

        httpTestServer = HttpServerAdapters.HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(new HttpEchoHandler(), "/http1/echo");
        httpURI = "http://" + httpTestServer.serverAuthority() + "/http1/echo";

        httpsTestServer = HttpServerAdapters.HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(new HttpEchoHandler(), "/https1/echo");
        httpsURI = "https://" + httpsTestServer.serverAuthority() + "/https1/echo";

        http2TestServer = HttpServerAdapters.HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(new HttpEchoHandler(), "/http2/echo");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2/echo";

        https2TestServer = HttpServerAdapters.HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(new HttpEchoHandler(), "/https2/echo");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/echo";

        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
    }

    @AfterTest
    public void teardown() throws Exception {
        if (Files.exists(zipFsPath))
            FileUtils.deleteFileTreeWithRetry(zipFsPath);
        if (Files.exists(defaultFsPath))
            FileUtils.deleteFileTreeWithRetry(defaultFsPath);

        httpTestServer.stop();
        httpsTestServer.stop();
        http2TestServer.stop();
        https2TestServer.stop();
        zipFs.close();
    }

    static class HttpEchoHandler implements HttpServerAdapters.HttpTestHandler {
        @Override
        public void handle(HttpServerAdapters.HttpTestExchange t) throws IOException {
            try (InputStream is = t.getRequestBody();
                OutputStream os = t.getResponseBody()) {
                is.readAllBytes();
                t.sendResponseHeaders(200, MSG.getBytes().length);
                os.write(MSG.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
