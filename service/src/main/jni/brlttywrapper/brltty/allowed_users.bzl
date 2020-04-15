"""Packages that are allowed to use brltty."""

# New brltty users should file a go/getsecurityreview ticket describing their
# use case before sending a CL to a member of ise-team@ to approve the
# visibility addition
BRLTTY_ALLOWED_USERS = [
    "//java/com/google/android/accessibility/braille/service:__pkg__",
    "//research/android/marvin/braille/service:__pkg__",
]
