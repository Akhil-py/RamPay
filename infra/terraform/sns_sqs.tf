# ---------------------------------------------------------------------------
# SNS — payment events topic
# ---------------------------------------------------------------------------

resource "aws_sns_topic" "payment_events" {
  name = "${local.cluster_name}-payment-events"

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# SQS — dead-letter queue
# ---------------------------------------------------------------------------

resource "aws_sqs_queue" "payment_events_dlq" {
  name = "${local.cluster_name}-payment-events-dlq"

  # Keep failed messages for 14 days to allow investigation / replay
  message_retention_seconds = 1209600

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# SQS — main payment-events queue
# ---------------------------------------------------------------------------

resource "aws_sqs_queue" "payment_events" {
  name = "${local.cluster_name}-payment-events"

  # Give consumers 60 s to process each message before it becomes visible again
  visibility_timeout_seconds = 60

  # Retain unprocessed messages for 24 hours
  message_retention_seconds = 86400

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.payment_events_dlq.arn
    maxReceiveCount     = 3
  })

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# SQS queue policy — allow SNS to send messages
# ---------------------------------------------------------------------------

resource "aws_sqs_queue_policy" "payment_events" {
  queue_url = aws_sqs_queue.payment_events.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowSNSPublish"
        Effect    = "Allow"
        Principal = { Service = "sns.amazonaws.com" }
        Action    = "sqs:SendMessage"
        Resource  = aws_sqs_queue.payment_events.arn
        Condition = {
          ArnEquals = {
            "aws:SourceArn" = aws_sns_topic.payment_events.arn
          }
        }
      }
    ]
  })
}

# ---------------------------------------------------------------------------
# SNS subscription — fan out to SQS
# ---------------------------------------------------------------------------

resource "aws_sns_topic_subscription" "sqs" {
  topic_arn = aws_sns_topic.payment_events.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.payment_events.arn
}
