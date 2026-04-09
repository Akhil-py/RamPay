# ---------------------------------------------------------------------------
# MSK — broker configuration
# ---------------------------------------------------------------------------

resource "aws_msk_configuration" "main" {
  name           = local.cluster_name
  kafka_versions = ["3.6.0"]

  server_properties = <<-PROPS
    auto.create.topics.enable=false
    default.replication.factor=2
    min.insync.replicas=1
    num.partitions=3
    log.retention.hours=168
    transaction.state.log.replication.factor=2
    transaction.state.log.min.isr=1
  PROPS
}

# ---------------------------------------------------------------------------
# MSK — security group
# ---------------------------------------------------------------------------

resource "aws_security_group" "msk" {
  name        = "${local.cluster_name}-msk-sg"
  description = "Allow Kafka traffic from EKS nodes"
  vpc_id      = aws_vpc.main.id

  # Plaintext Kafka
  ingress {
    description     = "Kafka plaintext from EKS nodes"
    from_port       = 9092
    to_port         = 9092
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_nodes.id]
  }

  # TLS Kafka
  ingress {
    description     = "Kafka TLS from EKS nodes"
    from_port       = 9094
    to_port         = 9094
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
    Name = "${local.cluster_name}-msk-sg"
  })
}

# ---------------------------------------------------------------------------
# MSK — cluster
# ---------------------------------------------------------------------------

resource "aws_msk_cluster" "main" {
  cluster_name           = local.cluster_name
  kafka_version          = "3.6.0"
  number_of_broker_nodes = var.msk_broker_count

  broker_node_group_info {
    instance_type  = var.msk_broker_instance_type
    # client_subnets length must equal number_of_broker_nodes.
    # With msk_broker_count=2 and 2 private subnets this works correctly.
    client_subnets  = aws_subnet.private[*].id
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = 20
      }
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.main.arn
    revision = aws_msk_configuration.main.latest_revision
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "PLAINTEXT"
      in_cluster    = true
    }
  }

  open_monitoring {
    prometheus {
      jmx_exporter {
        enabled_in_broker = true
      }
      node_exporter {
        enabled_in_broker = true
      }
    }
  }

  tags = local.common_tags
}
