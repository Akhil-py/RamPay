# RamPay Infrastructure — Terraform

This directory contains the complete Terraform configuration for the RamPay AWS infrastructure.

## Architecture overview

| Component | Resource | Details |
|-----------|----------|---------|
| Network | VPC + subnets | 10.0.0.0/16, 2 public + 2 private subnets across 2 AZs |
| Compute | EKS 1.30 | Managed node group, t3.medium, min 2 / max 5 |
| Database | DynamoDB | Payments + PaymentOutbox tables (PAY_PER_REQUEST) |
| Cache | ElastiCache Redis 7.1 | Single node, cache.t3.micro |
| Messaging | Amazon MSK | Kafka 3.6.0, 2 x kafka.t3.small brokers |
| Events | SNS + SQS | payment-events topic, SQS subscriber, DLQ |
| Auth | IAM / IRSA | Pod-level roles for payment-service and fraud-service |
| Registry | ECR | rampay/payment-service, rampay/fraud-service |

## Prerequisites

- [Terraform >= 1.6](https://developer.hashicorp.com/terraform/install)
- [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html) configured with credentials that have sufficient IAM permissions
- [kubectl](https://kubernetes.io/docs/tasks/tools/) (for interacting with the cluster after provisioning)

Verify your setup:

```bash
terraform version    # must be >= 1.6.0
aws sts get-caller-identity
kubectl version --client
```

## Deploying

### 1. Initialize

Download providers and set up the local backend:

```bash
cd infra/terraform
terraform init
```

### 2. Plan

Review all resources that will be created before touching AWS:

```bash
terraform plan -var="environment=dev"
```

Override any variable on the command line:

```bash
terraform plan \
  -var="environment=staging" \
  -var="eks_node_instance_type=t3.large" \
  -var="eks_node_desired=3"
```

### 3. Apply

```bash
terraform apply -var="environment=dev"
```

Type `yes` when prompted. Total provisioning time is approximately **20–30 minutes**, dominated by MSK (15–20 min) and EKS (10–15 min).

### 4. Connect kubectl to the cluster

After `apply` completes, run the command printed in the `eks_kubeconfig_command` output:

```bash
terraform output -raw eks_kubeconfig_command | bash
# equivalent to:
aws eks update-kubeconfig --region us-east-1 --name rampay-dev
```

Verify:

```bash
kubectl get nodes
```

## Key outputs

After a successful `apply`:

```bash
terraform output eks_cluster_endpoint       # EKS API server URL
terraform output redis_endpoint             # Redis primary endpoint
terraform output msk_bootstrap_brokers      # Kafka bootstrap string
terraform output ecr_payment_service_url    # ECR URL for payment-service
terraform output ecr_fraud_service_url      # ECR URL for fraud-service
terraform output sns_topic_arn
terraform output sqs_queue_url
```

## Annotating Kubernetes service accounts (IRSA)

After deploying, annotate the service accounts so pods can assume their IRSA roles:

```bash
PAYMENT_ROLE=$(terraform output -raw payment_service_irsa_role_arn)
FRAUD_ROLE=$(terraform output -raw fraud_service_irsa_role_arn)

kubectl annotate serviceaccount payment-service \
  eks.amazonaws.com/role-arn=$PAYMENT_ROLE

kubectl annotate serviceaccount fraud-service \
  eks.amazonaws.com/role-arn=$FRAUD_ROLE
```

## Cost estimate (us-east-1, dev defaults)

| Resource | Approx monthly cost |
|----------|---------------------|
| EKS control plane | ~$72 |
| 2 x t3.medium nodes | ~$120 |
| MSK 2 x kafka.t3.small | ~$100 |
| ElastiCache cache.t3.micro | ~$12 |
| NAT Gateway (base + traffic) | ~$35+ |
| DynamoDB (on-demand, low traffic) | < $5 |
| ECR storage | < $1 |
| SNS + SQS | < $1 |
| **Total estimate** | **~$345/month** |

Costs vary with actual traffic. Destroy the environment when not in use to avoid charges.

## Destroying

```bash
terraform destroy -var="environment=dev"
```

> **Warning:** This deletes all resources including DynamoDB tables and their data. Ensure you have taken backups if any real data exists.

## Variable reference

| Variable | Default | Description |
|----------|---------|-------------|
| `aws_region` | `us-east-1` | AWS region |
| `project_name` | `rampay` | Resource name prefix |
| `environment` | `dev` | Deployment environment |
| `vpc_cidr` | `10.0.0.0/16` | VPC CIDR block |
| `eks_cluster_version` | `1.30` | Kubernetes version |
| `eks_node_instance_type` | `t3.medium` | Node instance type |
| `eks_node_min` | `2` | Node group min size |
| `eks_node_max` | `5` | Node group max size |
| `eks_node_desired` | `2` | Node group desired size |
| `redis_node_type` | `cache.t3.micro` | Redis node type |
| `msk_broker_instance_type` | `kafka.t3.small` | MSK broker instance type |
| `msk_broker_count` | `2` | Number of MSK brokers |
| `dynamodb_payments_table` | `Payments` | Payments table name |
| `dynamodb_outbox_table` | `PaymentOutbox` | Outbox table name |

## S3 backend (production)

For team use, replace the local backend in `main.tf` with:

```hcl
backend "s3" {
  bucket         = "rampay-terraform-state"
  key            = "infra/terraform.tfstate"
  region         = "us-east-1"
  dynamodb_table = "rampay-terraform-locks"
  encrypt        = true
}
```

Create the S3 bucket and DynamoDB lock table before running `terraform init`.
