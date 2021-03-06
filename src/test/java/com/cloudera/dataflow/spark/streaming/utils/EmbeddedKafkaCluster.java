/*
 * Copyright (c) 2015, Cloudera, Inc. All Rights Reserved.
 *
 * Cloudera, Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the
 * License.
 */
package com.cloudera.dataflow.spark.streaming.utils;

import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.utils.Time;

/**
 * https://gist.github.com/fjavieralba/7930018
 */
public class EmbeddedKafkaCluster {
  private final List<Integer> ports;
  private final String zkConnection;
  private final Properties baseProperties;

  private final String brokerList;

  private final List<KafkaServer> brokers;
  private final List<File> logDirs;

  public EmbeddedKafkaCluster(String zkConnection) {
    this(zkConnection, new Properties());
  }

  public EmbeddedKafkaCluster(String zkConnection, Properties baseProperties) {
    this(zkConnection, baseProperties, Collections.singletonList(-1));
  }

  public EmbeddedKafkaCluster(String zkConnection, Properties baseProperties, List<Integer> ports) {
    this.zkConnection = zkConnection;
    this.ports = resolvePorts(ports);
    this.baseProperties = baseProperties;

    this.brokers = new ArrayList<KafkaServer>();
    this.logDirs = new ArrayList<File>();

    this.brokerList = constructBrokerList(this.ports);
  }

  private List<Integer> resolvePorts(List<Integer> ports) {
    List<Integer> resolvedPorts = new ArrayList<Integer>();
    for (Integer port : ports) {
      resolvedPorts.add(resolvePort(port));
    }
    return resolvedPorts;
  }

  private int resolvePort(int port) {
    if (port == -1) {
      return TestUtils.getAvailablePort();
    }
    return port;
  }

  private String constructBrokerList(List<Integer> ports) {
    StringBuilder sb = new StringBuilder();
    for (Integer port : ports) {
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append("localhost:").append(port);
    }
    return sb.toString();
  }

  public void startup() {
    for (int i = 0; i < ports.size(); i++) {
      Integer port = ports.get(i);
      File logDir = TestUtils.constructTempDir("kafka-local");

      Properties properties = new Properties();
      properties.putAll(baseProperties);
      properties.setProperty("zookeeper.connect", zkConnection);
      properties.setProperty("broker.id", String.valueOf(i + 1));
      properties.setProperty("host.name", "localhost");
      properties.setProperty("port", Integer.toString(port));
      properties.setProperty("log.dir", logDir.getAbsolutePath());
      properties.setProperty("log.flush.interval.messages", String.valueOf(1));

      KafkaServer broker = startBroker(properties);

      brokers.add(broker);
      logDirs.add(logDir);
    }
  }


  private KafkaServer startBroker(Properties props) {
    KafkaServer server = new KafkaServer(new KafkaConfig(props), new SystemTime());
    server.startup();
    return server;
  }

  public Properties getProps() {
    Properties props = new Properties();
    props.putAll(baseProperties);
    props.put("metadata.broker.list", brokerList);
    props.put("zookeeper.connect", zkConnection);
    return props;
  }

  public String getBrokerList() {
    return brokerList;
  }

  public List<Integer> getPorts() {
    return ports;
  }

  public String getZkConnection() {
    return zkConnection;
  }

  public void shutdown() {
    for (KafkaServer broker : brokers) {
      try {
        broker.shutdown();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    for (File logDir : logDirs) {
      try {
        TestUtils.deleteFile(logDir);
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("EmbeddedKafkaCluster{");
    sb.append("brokerList='").append(brokerList).append('\'');
    sb.append('}');
    return sb.toString();
  }

  public static class EmbeddedZookeeper {
    private int port = -1;
    private int tickTime = 500;

    private ServerCnxnFactory factory;
    private File snapshotDir;
    private File logDir;

    public EmbeddedZookeeper() {
      this(-1);
    }

    public EmbeddedZookeeper(int port) {
      this(port, 500);
    }

    public EmbeddedZookeeper(int port, int tickTime) {
      this.port = resolvePort(port);
      this.tickTime = tickTime;
    }

    private int resolvePort(int port) {
      if (port == -1) {
        return TestUtils.getAvailablePort();
      }
      return port;
    }

    public void startup() throws IOException {
      if (this.port == -1) {
        this.port = TestUtils.getAvailablePort();
      }
      this.factory = NIOServerCnxnFactory.createFactory(new InetSocketAddress("localhost", port),
              1024);
      this.snapshotDir = TestUtils.constructTempDir("embeeded-zk/snapshot");
      this.logDir = TestUtils.constructTempDir("embeeded-zk/log");

      try {
        factory.startup(new ZooKeeperServer(snapshotDir, logDir, tickTime));
      } catch (InterruptedException e) {
        throw new IOException(e);
      }
    }


    public void shutdown() {
      factory.shutdown();
      try {
        TestUtils.deleteFile(snapshotDir);
      } catch (FileNotFoundException e) {
        // ignore
      }
      try {
        TestUtils.deleteFile(logDir);
      } catch (FileNotFoundException e) {
        // ignore
      }
    }

    public String getConnection() {
      return "localhost:" + port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public void setTickTime(int tickTime) {
      this.tickTime = tickTime;
    }

    public int getPort() {
      return port;
    }

    public int getTickTime() {
      return tickTime;
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("EmbeddedZookeeper{");
      sb.append("connection=").append(getConnection());
      sb.append('}');
      return sb.toString();
    }
  }

  static class SystemTime implements Time {
    public long milliseconds() {
      return System.currentTimeMillis();
    }

    public long nanoseconds() {
      return System.nanoTime();
    }

    public void sleep(long ms) {
      try {
        Thread.sleep(ms);
      } catch (InterruptedException e) {
        // Ignore
      }
    }
  }

  static class TestUtils {
    private static final Random RANDOM = new Random();

    private TestUtils() {
    }

    public static File constructTempDir(String dirPrefix) {
      File file = new File(System.getProperty("java.io.tmpdir"), dirPrefix + RANDOM.nextInt
              (10000000));
      if (!file.mkdirs()) {
        throw new RuntimeException("could not create temp directory: " + file.getAbsolutePath());
      }
      file.deleteOnExit();
      return file;
    }

    public static int getAvailablePort() {
      try {
        ServerSocket socket = new ServerSocket(0);
        try {
          return socket.getLocalPort();
        } finally {
          socket.close();
        }
      } catch (IOException e) {
        throw new IllegalStateException("Cannot find available port: " + e.getMessage(), e);
      }
    }

    public static boolean deleteFile(File path) throws FileNotFoundException {
      if (!path.exists()) {
        throw new FileNotFoundException(path.getAbsolutePath());
      }
      boolean ret = true;
      if (path.isDirectory()) {
        for (File f : path.listFiles()) {
          ret = ret && deleteFile(f);
        }
      }
      return ret && path.delete();
    }
  }
}
