# ---------------------------------------------------------------------------
# EKS Node security group
# (defined here because iam.tf owns node-level resources;
#  elasticache.tf and msk.tf reference it via aws_security_group.eks_nodes)
# ---------------------------------------------------------------------------

resource "aws_security_group" "eks_nodes" {
  name        = "${local.cluster_name}-eks-nodes-sg"
  description = "Security group for EKS worker nodes"
  vpc_id      = aws_vpc.main.id

  # Allow all traffic between nodes in the same group
  ingress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    self      = true
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${local.cluster_name}-eks-nodes-sg"
  })
}

# ---------------------------------------------------------------------------
# EKS Cluster IAM role
# ---------------------------------------------------------------------------

data "aws_iam_policy_document" "eks_cluster_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["eks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "eks_cluster" {
  name               = "${local.cluster_name}-eks-cluster"
  assume_role_policy = data.aws_iam_policy_document.eks_cluster_assume_role.json

  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "eks_cluster_policy" {
  role       = aws_iam_role.eks_cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

# ---------------------------------------------------------------------------
# EKS Node IAM role
# ---------------------------------------------------------------------------

data "aws_iam_policy_document" "eks_node_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "eks_node" {
  name               = "${local.cluster_name}-eks-node"
  assume_role_policy = data.aws_iam_policy_document.eks_node_assume_role.json

  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "eks_node_policy" {
  role       = aws_iam_role.eks_node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
}

resource "aws_iam_role_policy_attachment" "eks_cni_policy" {
  role       = aws_iam_role.eks_node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
}

resource "aws_iam_role_policy_attachment" "ecr_readonly" {
  role       = aws_iam_role.eks_node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# ---------------------------------------------------------------------------
# IRSA — payment-service (DynamoDB access)
# ---------------------------------------------------------------------------

data "aws_iam_policy_document" "payment_service_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.eks.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${replace(aws_iam_openid_connect_provider.eks.url, "https://", "")}:sub"
      values   = ["system:serviceaccount:default:payment-service"]
    }
  }
}

resource "aws_iam_role" "payment_service" {
  name               = "${local.cluster_name}-payment-service"
  assume_role_policy = data.aws_iam_policy_document.payment_service_assume_role.json

  tags = local.common_tags
}

resource "aws_iam_role_policy" "payment_service_dynamodb" {
  name = "dynamodb-access"
  role = aws_iam_role.payment_service.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "DynamoDBTableAccess"
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:DeleteItem",
          "dynamodb:Query",
          "dynamodb:Scan",
          "dynamodb:BatchGetItem",
          "dynamodb:BatchWriteItem",
          "dynamodb:DescribeTable",
          "dynamodb:CreateTable",
        ]
        Resource = [
          aws_dynamodb_table.payments.arn,
          "${aws_dynamodb_table.payments.arn}/index/*",
          aws_dynamodb_table.payment_outbox.arn,
          "${aws_dynamodb_table.payment_outbox.arn}/index/*",
        ]
      }
    ]
  })
}

# ---------------------------------------------------------------------------
# IRSA — fraud-service
# (Fraud service only accesses Redis and MSK, both reachable over the VPC
#  without extra IAM policies — the role is created for future expansion
#  and for the K8s ServiceAccount annotation.)
# ---------------------------------------------------------------------------

data "aws_iam_policy_document" "fraud_service_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.eks.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${replace(aws_iam_openid_connect_provider.eks.url, "https://", "")}:sub"
      values   = ["system:serviceaccount:default:fraud-service"]
    }
  }
}

resource "aws_iam_role" "fraud_service" {
  name               = "${local.cluster_name}-fraud-service"
  assume_role_policy = data.aws_iam_policy_document.fraud_service_assume_role.json

  tags = local.common_tags
}
