/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.kafka.clients.producer;

/**
 * A key/value pair to be sent to Kafka. This consists of a topic name to which the record is being sent, an optional
 * partition number, an optional offset, and an optional key and value.
 * <p>
 * If a valid partition number is specified that partition will be used when sending the record. If no partition is
 * specified but a key is present a partition will be chosen using a hash of the key. If neither key nor partition is
 * present a partition will be assigned in a round-robin fashion.
 */
public final class ProducerRecord<K, V> {

    private final String topic;
    private final Integer partition;
    private final long offset;
    private final K key;
    private final V value;

    /**
     * Creates a record to be sent to a specified topic and partition
     * 
     * @param topic The topic the record will be appended to
     * @param partition The partition to which the record should be sent
     * @param offset The expected offset for the record, or -1 to leave unspecified
     * @param key The key that will be included in the record
     * @param value The record contents
     */
    public ProducerRecord(String topic, Integer partition, long offset, K key, V value) {
        if (topic == null)
            throw new IllegalArgumentException("Topic cannot be null");
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.key = key;
        this.value = value;
    }

    /**
     * Creates a record to be sent to a specified topic and partition
     * 
     * @param topic The topic the record will be appended to
     * @param partition The partition to which the record should be sent
     * @param key The key that will be included in the record
     * @param value The record contents
     */
    public ProducerRecord(String topic, Integer partition, K key, V value) {
        this(topic, partition, -1, key, value);
    }

    /**
     * Create a record to be sent to Kafka
     * 
     * @param topic The topic the record will be appended to
     * @param key The key that will be included in the record
     * @param value The record contents
     */
    public ProducerRecord(String topic, K key, V value) {
        this(topic, null, key, value);
    }

    /**
     * Create a record with no key
     * 
     * @param topic The topic this record should be sent to
     * @param value The record contents
     */
    public ProducerRecord(String topic, V value) {
        this(topic, null, value);
    }

    /**
     * The topic this record is being sent to
     */
    public String topic() {
        return topic;
    }

    /**
     * The key (or null if no key is specified)
     */
    public K key() {
        return key;
    }

    /**
     * @return The value
     */
    public V value() {
        return value;
    }

    /**
     * The partition to which the record will be sent (or null if no partition was specified)
     */
    public Integer partition() {
        return partition;
    }

    /**
     * The expected offset for this record (or -1 if no expected offset
     */
    public long offset() {
        return offset;
    }

    @Override
    public String toString() {
        String key = this.key == null ? "null" : this.key.toString();
        String value = this.value == null ? "null" : this.value.toString();
        return "ProducerRecord(topic=" + topic + ", partition=" + partition + ", offset=" + offset + ", key=" + key + ", value=" + value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        else if (!(o instanceof ProducerRecord))
            return false;

        ProducerRecord<?, ?> that = (ProducerRecord<?, ?>) o;

        if (key != null ? !key.equals(that.key) : that.key != null) 
            return false;
        else if (partition != null ? !partition.equals(that.partition) : that.partition != null) 
            return false;
        else if (topic != null ? !topic.equals(that.topic) : that.topic != null) 
            return false;
        else if (offset != that.offset)
            return false;
        else if (value != null ? !value.equals(that.value) : that.value != null) 
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = topic != null ? topic.hashCode() : 0;
        result = 31 * result + (partition != null ? partition.hashCode() : 0);
        result = 31 * result + (int) offset;
        result = 31 * result + (key != null ? key.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
}
