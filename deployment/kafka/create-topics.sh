#!/bin/bash
# Kafka topic creation script for AI Agent Platform
# Run against your Kafka cluster before deploying services

KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"

echo "Creating Kafka topics on $KAFKA_BOOTSTRAP..."

# Audit events - high volume, long retention
kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic vault-audit-events \
  --partitions 8 --replication-factor 3 \
  --config retention.ms=604800000 \
  --config cleanup.policy=delete

# Gateway access logs
kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic gateway-access-log \
  --partitions 4 --replication-factor 3 \
  --config retention.ms=259200000

# Anomaly alerts
kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic anomaly-alerts \
  --partitions 2 --replication-factor 3 \
  --config retention.ms=2592000000

# Transaction events - highest throughput
kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic txn-events \
  --partitions 16 --replication-factor 3 \
  --config retention.ms=86400000

# Fraud alerts
kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic fraud-alerts \
  --partitions 4 --replication-factor 3 \
  --config retention.ms=2592000000

# Fraud responses (block/allow)
kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic fraud-responses \
  --partitions 4 --replication-factor 3 \
  --config retention.ms=86400000

# Collections queue
kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic collections-queue \
  --partitions 2 --replication-factor 3 \
  --config retention.ms=604800000

# Agent handoff context - short-lived
kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic agent-handoff-context \
  --partitions 4 --replication-factor 3 \
  --config retention.ms=3600000

# Customer feedback
kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic customer-feedback \
  --partitions 2 --replication-factor 3 \
  --config retention.ms=7776000000

echo "All topics created successfully."
kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --list
