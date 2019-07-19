/*
 * Copyright (C) 2018 Joan Goyeau.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.streams.scala.kstream

import java.time.Duration.ofSeconds

import org.apache.kafka.streams.kstream.JoinWindows
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala.Serdes._
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.utils.TestDriver
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FlatSpec, Matchers}

@RunWith(classOf[JUnitRunner])
class KStreamTest extends FlatSpec with Matchers with TestDriver {

  "filter a KStream" should "filter records satisfying the predicate" in {
    val builder = new StreamsBuilder()
    val sourceTopic = "source"
    val sinkTopic = "sink"

    builder.stream[String, String](sourceTopic).filter((_, value) => value != "value2").to(sinkTopic)

    val testDriver = createTestDriver(builder)

    testDriver.pipeRecord(sourceTopic, ("1", "value1"))
    testDriver.readRecord[String, String](sinkTopic).value shouldBe "value1"

    testDriver.pipeRecord(sourceTopic, ("2", "value2"))
    testDriver.readRecord[String, String](sinkTopic) shouldBe null

    testDriver.pipeRecord(sourceTopic, ("3", "value3"))
    testDriver.readRecord[String, String](sinkTopic).value shouldBe "value3"

    testDriver.readRecord[String, String](sinkTopic) shouldBe null

    testDriver.close()
  }

  "filterNot a KStream" should "filter records not satisfying the predicate" in {
    val builder = new StreamsBuilder()
    val sourceTopic = "source"
    val sinkTopic = "sink"

    builder.stream[String, String](sourceTopic).filterNot((_, value) => value == "value2").to(sinkTopic)

    val testDriver = createTestDriver(builder)

    testDriver.pipeRecord(sourceTopic, ("1", "value1"))
    testDriver.readRecord[String, String](sinkTopic).value shouldBe "value1"

    testDriver.pipeRecord(sourceTopic, ("2", "value2"))
    testDriver.readRecord[String, String](sinkTopic) shouldBe null

    testDriver.pipeRecord(sourceTopic, ("3", "value3"))
    testDriver.readRecord[String, String](sinkTopic).value shouldBe "value3"

    testDriver.readRecord[String, String](sinkTopic) shouldBe null

    testDriver.close()
  }

  "foreach a KStream" should "run foreach actions on records" in {
    val builder = new StreamsBuilder()
    val sourceTopic = "source"

    var acc = ""
    builder.stream[String, String](sourceTopic).foreach((_, value) => acc += value)

    val testDriver = createTestDriver(builder)

    testDriver.pipeRecord(sourceTopic, ("1", "value1"))
    acc shouldBe "value1"

    testDriver.pipeRecord(sourceTopic, ("2", "value2"))
    acc shouldBe "value1value2"

    testDriver.close()
  }

  "peek a KStream" should "run peek actions on records" in {
    val builder = new StreamsBuilder()
    val sourceTopic = "source"
    val sinkTopic = "sink"

    var acc = ""
    builder.stream[String, String](sourceTopic).peek((_, v) => acc += v).to(sinkTopic)

    val testDriver = createTestDriver(builder)

    testDriver.pipeRecord(sourceTopic, ("1", "value1"))
    acc shouldBe "value1"
    testDriver.readRecord[String, String](sinkTopic).value shouldBe "value1"

    testDriver.pipeRecord(sourceTopic, ("2", "value2"))
    acc shouldBe "value1value2"
    testDriver.readRecord[String, String](sinkTopic).value shouldBe "value2"

    testDriver.close()
  }

  "selectKey a KStream" should "select a new key" in {
    val builder = new StreamsBuilder()
    val sourceTopic = "source"
    val sinkTopic = "sink"

    builder.stream[String, String](sourceTopic).selectKey((_, value) => value).to(sinkTopic)

    val testDriver = createTestDriver(builder)

    testDriver.pipeRecord(sourceTopic, ("1", "value1"))
    testDriver.readRecord[String, String](sinkTopic).key shouldBe "value1"

    testDriver.pipeRecord(sourceTopic, ("1", "value2"))
    testDriver.readRecord[String, String](sinkTopic).key shouldBe "value2"

    testDriver.readRecord[String, String](sinkTopic) shouldBe null

    testDriver.close()
  }

  "join 2 KStreams" should "join correctly records" in {
    val builder = new StreamsBuilder()
    val sourceTopic1 = "source1"
    val sourceTopic2 = "source2"
    val sinkTopic = "sink"

    val stream1 = builder.stream[String, String](sourceTopic1)
    val stream2 = builder.stream[String, String](sourceTopic2)
    stream1.join(stream2)((a, b) => s"$a-$b", JoinWindows.of(ofSeconds(1))).to(sinkTopic)

    val now = System.currentTimeMillis()

    val testDriver = createTestDriver(builder, now)

    testDriver.pipeRecord(sourceTopic1, ("1", "topic1value1"), now)
    testDriver.pipeRecord(sourceTopic2, ("1", "topic2value1"), now)

    testDriver.readRecord[String, String](sinkTopic).value shouldBe "topic1value1-topic2value1"

    testDriver.readRecord[String, String](sinkTopic) shouldBe null

    testDriver.close()
  }

  // can't test at the groupByKey level - have to convert to stream as per below?
//  "group by key" should "use the default serdes" in {
//    val builder = new StreamsBuilder()
//    val sourceTopic1 = "source1"
//    val sourceTopic2 = "source2"
//    val sinkTopic = "sink"
//
//    import org.apache.kafka.streams.scala._
//    import org.apache.kafka.streams.scala.kstream._
//    import org.apache.kafka.streams.scala.Serdes._
//
//    val s1 = builder.stream[String, String](sourceTopic1)
//    val s2 = builder.stream[String, String](sourceTopic2)
//
//    // explicit
//    s1.merge(s2).groupByKey(Grouped.`with`(Serdes.String, Serdes.String)) // works
//
//    // implicit
//    s1.merge(s2).groupByKey() // doesn't compile without change
//
//    val now = System.currentTimeMillis()
//    val testDriver = createTestDriver(builder, now)
//
//    testDriver.pipeRecord(sourceTopic1, ("1", "topic1value1"), now)
//    testDriver.pipeRecord(sourceTopic2, ("1", "topic2value2"), now)
//
//    testDriver.readRecord[String, String](sinkTopic).value shouldBe "topic1value1-topic2value1"
//    testDriver.close()
//  }
//
//  // can't test at the reduce level - have to convert to stream as per below?
//  "reduce a merged stream" should "work" in {
//    val builder = new StreamsBuilder()
//
//    val s1 = builder.stream[String, String]("s1")
//    val s2 = builder.stream[String, String]("s2")
//
//    s1.merge(s2).groupByKey().reduce(_ + _)
//  }

  "groupby key, reduce, filter, tostream, to" should "work" in {
    val builder = new StreamsBuilder()
    val sourceTopic1 = "base-data"
    val sourceTopic2 = "reg-data"
    val sinkTopic = "sink"

    val s1 = builder.stream[String, String](sourceTopic1)
    val s2 = builder.stream[String, String](sourceTopic2)

//    import org.apache.kafka.streams.scala.Serdes._
    s1.merge(s2)
      .groupByKey()
      .reduce(_ + ", " + _)
      .toStream
      .filter((k, v) => v.contains("base-data"))
      .to(sinkTopic)

    val now = System.currentTimeMillis()
    val testDriver = createTestDriver(builder, now)

    testDriver.pipeRecord(sourceTopic1, ("A", "keyA-topic1-base-data"), now)
    testDriver.pipeRecord(sourceTopic2, ("B", "keyB-topic2-reg-data"), now)
    testDriver.pipeRecord(sourceTopic2, ("A", "keyA-topic2-reg-data"), now)
    testDriver.pipeRecord(sourceTopic2, ("B", "keyB-topic1-base-data"), now)

    val value1 = testDriver.readRecord[String, String](sinkTopic)
    val value2 = testDriver.readRecord[String, String](sinkTopic)
    val value3 = testDriver.readRecord[String, String](sinkTopic)

    value1.value shouldBe "keyA-topic1-base-data"
    value2.value shouldBe "keyA-topic1-base-data, keyA-topic2-reg-data"
    value3.value shouldBe "keyB-topic2-reg-data, keyB-topic1-base-data"
    testDriver.close()
  }

}
