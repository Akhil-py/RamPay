# ---------------------------------------------------------------------------
# ElastiCache Redis — subnet group
# ---------------------------------------------------------------------------

resource "aws_elasticache_subnet_group" "redis" {
  name       = "${local.cluster_name}-redis"
  subnet_ids = aws_subnet.private[*].id

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# ElastiCache Redis — security group
# ---------------------------------------------------------------------------

resource "aws_security_group" "redis" {
  name        = "${local.cluster_name}-redis-sg"
  description = "Allow Redis traffic from EKS nodes"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Redis from EKS nodes"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_nodes.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${local.cluster_name}-redis-sg"
  })
}

# ---------------------------------------------------------------------------
# ElastiCache Redis — replication group (single-node)
# ---------------------------------------------------------------------------

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = "${local.cluster_name}"
  description          = "RamPay Redis — caching and fraud blocklist"

  node_type  = var.redis_node_type
  port       = 6379
  engine     = "redis"

  # Pin to a stable Redis 7.1 release
  engine_version = "7.1"

  # Single-node — no automatic failover
  num_cache_clusters         = 1
  automatic_failover_enabled = false

  subnet_group_name  = aws_elasticache_subnet_group.redis.name
  security_group_ids = [aws_security_group.redis.id]

  at_rest_encryption_enabled = true

  # transit_encryption_enabled kept false for simplicity:
  # enabling it requires an auth_token and TLS-aware client config.
  transit_encryption_enabled = false

  tags = local.common_tags
}
