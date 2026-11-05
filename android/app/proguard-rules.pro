# Capri keeps her services and activities by name from the manifest; R8 keeps those already.
# The engine reads JSON by hand, with no reflection, so it needs no keep rules.
-dontwarn com.sun.net.httpserver.**
