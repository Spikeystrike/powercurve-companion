#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
javac -d "$out" android/grip_gains_companion/app/src/main/java/app/grip_gains_companion/service/ForceDropDetector.java tests/ForceDropDetectorTest.java android/grip_gains_companion/app/src/main/java/app/grip_gains_companion/service/ble/Whc06Decoder.java tests/Whc06DecoderTest.java
java -cp "$out" ForceDropDetectorTest
java -cp "$out" Whc06DecoderTest
node --test tests/bridge.test.cjs
