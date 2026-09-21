# The availability zones this account may use in the region. Reading them
# rather than naming them keeps the module portable and keeps the subnet
# definitions free of region-specific literals.
data "aws_availability_zones" "available" {
  state = "available"

  # Only the zones every account in the region has. Local Zones and
  # Wavelength zones are opt-in, appear only in accounts that enabled them,
  # and would otherwise make the first two names the module slices depend on
  # which account is running it.
  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]
  }
}
