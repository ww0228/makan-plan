package makanplan.util

import java.security.MessageDigest
import java.nio.charset.StandardCharsets

// simple password hashing helpers (SHA-256 hex)
object Password:

  // legacy alias: returns SHA-256 hex
  def sha256(s: String): String =
    sha256Hex(s)

  // preferred alias used across the app
  def hash(plain: String): String =
    sha256Hex(plain)

  // verify using constant-time compare
  def verify(plain: String, hashed: String): Boolean =
    constantTimeEquals(hash(plain), hashed)

  // helpers 
  // compute SHA-256 and hex-encode it
  private def sha256Hex(s: String): String =
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(s.getBytes(StandardCharsets.UTF_8))
    toHex(bytes)

  // bytes -> lowercase hex string
  private def toHex(bytes: Array[Byte]): String =
    val sb = new StringBuilder(bytes.length * 2)
    var i = 0
    while i < bytes.length do
      val b = bytes(i) & 0xff
      val hi = "0123456789abcdef".charAt(b >>> 4)
      val lo = "0123456789abcdef".charAt(b & 0x0f)
      sb.append(hi).append(lo)
      i += 1
    sb.toString

  // constant-time equality to avoid timing leaks
  private def constantTimeEquals(a: String, b: String): Boolean =
    val aBytes = a.getBytes(StandardCharsets.UTF_8)
    val bBytes = b.getBytes(StandardCharsets.UTF_8)
    if aBytes.length != bBytes.length then return false
    var diff = 0
    var i = 0
    while i < aBytes.length do
      diff |= (aBytes(i) ^ bBytes(i)) & 0xff
      i += 1
    diff == 0
