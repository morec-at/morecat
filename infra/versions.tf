terraform {
  required_version = ">= 1.17.1, < 2.0.0"

  required_providers {
    tfe = {
      source  = "hashicorp/tfe"
      version = "~> 0.83.0"
    }
  }
}

provider "tfe" {}
