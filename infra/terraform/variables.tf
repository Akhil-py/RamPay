variable "aws_region" {
  description = "AWS region to deploy all resources into."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Short name used as a prefix for all resource names and tags."
  type        = string
  default     = "rampay"
}

variable "environment" {
  description = "Deployment environment (dev, staging, prod)."
  type        = string
  default     = "dev"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "eks_cluster_version" {
  description = "Kubernetes version for the EKS cluster."
  type        = string
  default     = "1.30"
}

variable "eks_node_instance_type" {
  description = "EC2 instance type for EKS managed node group workers."
  type        = string
  default     = "t3.medium"
}

variable "eks_node_min" {
  description = "Minimum number of nodes in the EKS managed node group."
  type        = number
  default     = 2
}

variable "eks_node_max" {
  description = "Maximum number of nodes in the EKS managed node group."
  type        = number
  default     = 5
}

variable "eks_node_desired" {
  description = "Desired number of nodes in the EKS managed node group."
  type        = number
  default     = 2
}

variable "redis_node_type" {
  description = "ElastiCache node type for the Redis cluster."
  type        = string
  default     = "cache.t3.micro"
}

variable "msk_broker_instance_type" {
  description = "MSK broker instance type."
  type        = string
  default     = "kafka.t3.small"
}

variable "msk_broker_count" {
  description = "Number of MSK broker nodes. Must equal the number of private subnets (2)."
  type        = number
  default     = 2
}

variable "dynamodb_payments_table" {
  description = "Name of the DynamoDB Payments table."
  type        = string
  default     = "Payments"
}

variable "dynamodb_outbox_table" {
  description = "Name of the DynamoDB PaymentOutbox table."
  type        = string
  default     = "PaymentOutbox"
}
