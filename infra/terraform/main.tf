terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Local backend used for portability.
  # To switch to S3 backend, replace with:
  #
  # backend "s3" {
  #   bucket         = "rampay-terraform-state"
  #   key            = "infra/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "rampay-terraform-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}

# Kubernetes provider declared but not configured — connecting it to the
# just-created cluster would cause a chicken-and-egg dependency at plan time.
# Configure it separately after `terraform apply` when the cluster exists.
provider "kubernetes" {}

provider "tls" {}

# ---------------------------------------------------------------------------
# Data sources
# ---------------------------------------------------------------------------

data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_caller_identity" "current" {}

# ---------------------------------------------------------------------------
# Locals
# ---------------------------------------------------------------------------

locals {
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }

  cluster_name = "${var.project_name}-${var.environment}"
}
