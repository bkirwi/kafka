/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package kafka.integration

import java.nio.ByteBuffer

import junit.framework.Assert._
import kafka.admin.AdminUtils
import kafka.api.{TopicMetadataResponse, TopicMetadataRequest}
import kafka.client.ClientUtils
import kafka.cluster.{Broker, BrokerEndPoint}
import kafka.common.ErrorMapping
import kafka.server.{NotRunning, KafkaConfig, KafkaServer}
import kafka.utils.TestUtils
import kafka.utils.TestUtils._
import kafka.zk.ZooKeeperTestHarness
import org.apache.kafka.common.protocol.SecurityProtocol
import org.scalatest.junit.JUnit3Suite

class TopicMetadataTest extends JUnit3Suite with ZooKeeperTestHarness {
  private var server1: KafkaServer = null
  var brokerEndPoints: Seq[BrokerEndPoint] = null
  var adHocConfigs: Seq[KafkaConfig] = null
  val numConfigs: Int = 2

  override def setUp() {
    super.setUp()
    val props = createBrokerConfigs(numConfigs, zkConnect)
    val configs: Seq[KafkaConfig] = props.map(KafkaConfig.fromProps)
    adHocConfigs = configs.takeRight(configs.size - 1) // Started and stopped by individual test cases
    server1 = TestUtils.createServer(configs.head)
    brokerEndPoints = Seq(new Broker(server1.config.brokerId, server1.config.hostName, server1.boundPort()).getBrokerEndPoint(SecurityProtocol.PLAINTEXT))
  }

  override def tearDown() {
    server1.shutdown()
    super.tearDown()
  }

  def testTopicMetadataRequest {
    // create topic
    val topic = "test"
    AdminUtils.createTopic(zkClient, topic, 1, 1)

    // create a topic metadata request
    val topicMetadataRequest = new TopicMetadataRequest(List(topic), 0)

    val serializedMetadataRequest = ByteBuffer.allocate(topicMetadataRequest.sizeInBytes + 2)
    topicMetadataRequest.writeTo(serializedMetadataRequest)
    serializedMetadataRequest.rewind()
    val deserializedMetadataRequest = TopicMetadataRequest.readFrom(serializedMetadataRequest)

    assertEquals(topicMetadataRequest, deserializedMetadataRequest)
  }

  def testBasicTopicMetadata {
    // create topic
    val topic = "test"
    createTopic(zkClient, topic, numPartitions = 1, replicationFactor = 1, servers = Seq(server1))

    var topicsMetadata = ClientUtils.fetchTopicMetadata(Set(topic), brokerEndPoints, "TopicMetadataTest-testBasicTopicMetadata",
      2000,0).topicsMetadata
    assertEquals(ErrorMapping.NoError, topicsMetadata.head.errorCode)
    assertEquals(ErrorMapping.NoError, topicsMetadata.head.partitionsMetadata.head.errorCode)
    assertEquals("Expecting metadata only for 1 topic", 1, topicsMetadata.size)
    assertEquals("Expecting metadata for the test topic", "test", topicsMetadata.head.topic)
    var partitionMetadata = topicsMetadata.head.partitionsMetadata
    assertEquals("Expecting metadata for 1 partition", 1, partitionMetadata.size)
    assertEquals("Expecting partition id to be 0", 0, partitionMetadata.head.partitionId)
    assertEquals(1, partitionMetadata.head.replicas.size)
  }

  def testGetAllTopicMetadata {
    // create topic
    val topic1 = "testGetAllTopicMetadata1"
    val topic2 = "testGetAllTopicMetadata2"
    createTopic(zkClient, topic1, numPartitions = 1, replicationFactor = 1, servers = Seq(server1))
    createTopic(zkClient, topic2, numPartitions = 1, replicationFactor = 1, servers = Seq(server1))

    // issue metadata request with empty list of topics
    var topicsMetadata = ClientUtils.fetchTopicMetadata(Set.empty, brokerEndPoints, "TopicMetadataTest-testGetAllTopicMetadata",
      2000, 0).topicsMetadata
    assertEquals(ErrorMapping.NoError, topicsMetadata.head.errorCode)
    assertEquals(2, topicsMetadata.size)
    assertEquals(ErrorMapping.NoError, topicsMetadata.head.partitionsMetadata.head.errorCode)
    assertEquals(ErrorMapping.NoError, topicsMetadata.last.partitionsMetadata.head.errorCode)
    val partitionMetadataTopic1 = topicsMetadata.head.partitionsMetadata
    val partitionMetadataTopic2 = topicsMetadata.last.partitionsMetadata
    assertEquals("Expecting metadata for 1 partition", 1, partitionMetadataTopic1.size)
    assertEquals("Expecting partition id to be 0", 0, partitionMetadataTopic1.head.partitionId)
    assertEquals(1, partitionMetadataTopic1.head.replicas.size)
    assertEquals("Expecting metadata for 1 partition", 1, partitionMetadataTopic2.size)
    assertEquals("Expecting partition id to be 0", 0, partitionMetadataTopic2.head.partitionId)
    assertEquals(1, partitionMetadataTopic2.head.replicas.size)
  }

  def testAutoCreateTopic {
    // auto create topic
    val topic = "testAutoCreateTopic"
    var topicsMetadata = ClientUtils.fetchTopicMetadata(Set(topic), brokerEndPoints, "TopicMetadataTest-testAutoCreateTopic",
      2000,0).topicsMetadata
    assertEquals(ErrorMapping.LeaderNotAvailableCode, topicsMetadata.head.errorCode)
    assertEquals("Expecting metadata only for 1 topic", 1, topicsMetadata.size)
    assertEquals("Expecting metadata for the test topic", topic, topicsMetadata.head.topic)
    assertEquals(0, topicsMetadata.head.partitionsMetadata.size)

    // wait for leader to be elected
    TestUtils.waitUntilLeaderIsElectedOrChanged(zkClient, topic, 0)
    TestUtils.waitUntilMetadataIsPropagated(Seq(server1), topic, 0)

    // retry the metadata for the auto created topic
    topicsMetadata = ClientUtils.fetchTopicMetadata(Set(topic), brokerEndPoints, "TopicMetadataTest-testBasicTopicMetadata",
      2000,0).topicsMetadata
    assertEquals(ErrorMapping.NoError, topicsMetadata.head.errorCode)
    assertEquals(ErrorMapping.NoError, topicsMetadata.head.partitionsMetadata.head.errorCode)
    var partitionMetadata = topicsMetadata.head.partitionsMetadata
    assertEquals("Expecting metadata for 1 partition", 1, partitionMetadata.size)
    assertEquals("Expecting partition id to be 0", 0, partitionMetadata.head.partitionId)
    assertEquals(1, partitionMetadata.head.replicas.size)
    assertTrue(partitionMetadata.head.leader.isDefined)
  }

  private def checkIsr(servers: Seq[KafkaServer]): Unit = {
    val activeBrokers: Seq[KafkaServer] = servers.filter(x => x.brokerState.currentState != NotRunning.state)
    val expectedIsr: Seq[BrokerEndPoint] = activeBrokers.map(
      x => new BrokerEndPoint(x.config.brokerId,
                              if (x.config.hostName.nonEmpty) x.config.hostName else "localhost",
                              x.boundPort())
    )

    // Assert that topic metadata at new brokers is updated correctly
    activeBrokers.foreach(x => {
      var metadata: TopicMetadataResponse = new TopicMetadataResponse(Seq(), Seq(), -1)
      waitUntilTrue(() => {
        metadata = ClientUtils.fetchTopicMetadata(
                                Set.empty,
                                Seq(new BrokerEndPoint(
                                                  x.config.brokerId,
                                                  if (x.config.hostName.nonEmpty) x.config.hostName else "localhost",
                                                  x.boundPort())),
                                "TopicMetadataTest-testBasicTopicMetadata",
                                2000, 0)
        metadata.topicsMetadata.nonEmpty &&
          metadata.topicsMetadata.head.partitionsMetadata.nonEmpty &&
          expectedIsr == metadata.topicsMetadata.head.partitionsMetadata.head.isr
      },
        "Topic metadata is not correctly updated for broker " + x + ".\n" +
        "Expected ISR: " + expectedIsr + "\n" +
        "Actual ISR  : " + (if (metadata.topicsMetadata.nonEmpty &&
                                metadata.topicsMetadata.head.partitionsMetadata.nonEmpty)
                              metadata.topicsMetadata.head.partitionsMetadata.head.isr
                            else
                              ""))
    })
  }


  def testIsrAfterBrokerShutDownAndJoinsBack {
    // start adHoc brokers
    val adHocServers = adHocConfigs.map(p => createServer(p))
    val allServers: Seq[KafkaServer] = Seq(server1) ++ adHocServers

    // create topic
    val topic: String = "test"
    AdminUtils.createTopic(zkClient, topic, 1, numConfigs)

    // shutdown a broker
    adHocServers.last.shutdown()
    adHocServers.last.awaitShutdown()

    // startup a broker
    adHocServers.last.startup()

    // check metadata is still correct and updated at all brokers
    checkIsr(allServers)

    // shutdown adHoc brokers
    adHocServers.map(p => p.shutdown())
  }
}
