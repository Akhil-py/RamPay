# ---------------------------------------------------------------------------
# DynamoDB — Payments table
# ---------------------------------------------------------------------------

resource "aws_dynamodb_table" "payments" {
  name         = var.dynamodb_payments_table
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "id"

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "fromAccountId"
    type = "S"
  }

  attribute {
    name = "toAccountId"
    type = "S"
  }

  attribute {
    name = "status"
    type = "S"
  }

  attribute {
    name = "idempotencyKey"
    type = "S"
  }

  attribute {
    name = "createdAt"
    type = "S"
  }

  global_secondary_index {
    name            = "fromAccountId-index"
    hash_key        = "fromAccountId"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "toAccountId-index"
    hash_key        = "toAccountId"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "status-index"
    hash_key        = "status"
    range_key       = "createdAt"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "idempotencyKey-index"
    hash_key        = "idempotencyKey"
    projection_type = "ALL"
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }

  # Set to true in production environments
  deletion_protection_enabled = false

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# DynamoDB — PaymentOutbox table
# ---------------------------------------------------------------------------

resource "aws_dynamodb_table" "payment_outbox" {
  name         = var.dynamodb_outbox_table
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "id"
  range_key    = "createdAt"

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "createdAt"
    type = "S"
  }

  attribute {
    name = "status"
    type = "S"
  }

  global_secondary_index {
    name            = "status-createdAt-index"
    hash_key        = "status"
    range_key       = "createdAt"
    projection_type = "ALL"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }

  tags = local.common_tags
}
