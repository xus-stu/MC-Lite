"""Optional Python companion for MC Lite.

This package talks to the Java launcher's loopback API.  It is not bundled
inside the launcher JAR; install it only if you want a Python/PySide6 UI or
automation scripts.
"""
from .client import BridgeClient, BridgeError, start_bridge

__all__ = ["BridgeClient", "BridgeError", "start_bridge"]
