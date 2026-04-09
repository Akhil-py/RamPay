output "eks_cluster_endpoint" {
  description = "HTTPS endpoint of the EKS API server."
  value       = aws_eks_cluster.main.endpoint
}

output "eks_cluster_name" {
  description = "Name of the EKS cluster."
  value       = aws_eks_cluster.main.name
}

output "eks_kubeconfig_command" {
  description = "Run this command to update your local kubeconfig for the cluster."
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${aws_eks_cluster.main.name}"
}

output "dynamodb_payments_table_name" {
  description = "Name of the DynamoDB Payments table."
  value       = aws_dynamodb_table.payments.name
}

output "dynamodb_outbox_table_name" {
  description = "Name of the DynamoDB PaymentOutbox table."
  value       = aws_dynamodb_table.payment_outbox.name
}

output "redis_endpoint" {
  description = "Primary endpoint for the ElastiCache Redis replication group."
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "msk_bootstrap_brokers" {
  description = "Plaintext bootstrap broker connection string for the MSK cluster."
  value       = aws_msk_cluster.main.bootstrap_brokers
}

output "sns_topic_arn" {
  description = "ARN of the payment-events SNS topic."
  value       = aws_sns_topic.payment_events.arn
}

output "sqs_queue_url" {
  description = "URL of the payment-events SQS queue."
  value       = aws_sqs_queue.payment_events.url
}

output "ecr_payment_service_url" {
  description = "ECR repository URL for the payment-service container image."
  value       = aws_ecr_repository.payment_service.repository_url
}

output "ecr_fraud_service_url" {
  description = "ECR repository URL for the fraud-service container image."
  value       = aws_ecr_repository.fraud_service.repository_url
}

output "vpc_id" {
  description = "ID of the RamPay VPC."
  value       = aws_vpc.main.id
}

output "private_subnet_ids" {
  description = "IDs of the private subnets (used by EKS nodes, MSK, Redis)."
  value       = aws_subnet.private[*].id
}

output "public_subnet_ids" {
  description = "IDs of the public subnets (used by the NAT gateway and load balancers)."
  value       = aws_subnet.public[*].id
}

output "payment_service_irsa_role_arn" {
  description = "ARN of the IRSA role for payment-service pods."
  value       = aws_iam_role.payment_service.arn
}

output "fraud_service_irsa_role_arn" {
  description = "ARN of the IRSA role for fraud-service pods."
  value       = aws_iam_role.fraud_service.arn
}
