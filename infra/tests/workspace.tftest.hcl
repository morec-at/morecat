mock_provider "tfe" {}

run "prod_requires_apply_approval" {
  command = plan

  assert {
    condition     = tfe_workspace.prod.terraform_version == "1.17.1"
    error_message = "HCP Terraform must use Terraform 1.17.1, matching the local CLI."
  }

  assert {
    condition     = tfe_workspace.prod.name == "morecat-prod"
    error_message = "The workspace name must use the short prod suffix."
  }

  assert {
    condition     = tfe_workspace.prod.organization == "emag-org"
    error_message = "The prod workspace must belong to emag-org by default."
  }

  assert {
    condition     = tfe_workspace.prod.auto_apply == false
    error_message = "Production infrastructure must require apply approval."
  }

  assert {
    condition     = tfe_workspace.prod.working_directory == "infra" && tfe_workspace_settings.prod.execution_mode == "remote"
    error_message = "Remote runs must use the repository's infra directory."
  }

  assert {
    condition     = !tfe_workspace.prod.allow_destroy_plan && !tfe_workspace.prod.force_delete && !tfe_workspace_settings.prod.global_remote_state && !tfe_workspace_settings.prod.project_remote_state && length(tfe_workspace_settings.prod.remote_state_consumer_ids) == 0
    error_message = "The self-managed workspace must protect its state and reject destroy plans."
  }
}

run "remote_runs_receive_their_organization" {
  command = plan

  variables {
    hcp_organization = "test-organization"
  }

  assert {
    condition     = contains([for input in tfe_variable.inputs : input.value], "test-organization")
    error_message = "Remote runs need their non-secret Terraform inputs configured."
  }
}
